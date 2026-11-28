package emu.grasscutter.game.icecreature;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.google.protobuf.CodedInputStream;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.IceCreatureAllGadgetInfoNotifyOuterClass.IceCreatureAllGadgetInfoNotify;
import emu.grasscutter.net.proto.IceCreatureBuildReqOuterClass.IceCreatureBuildReq;
import emu.grasscutter.net.proto.IceCreatureBuildRspOuterClass.IceCreatureBuildRsp;
import emu.grasscutter.net.proto.IceCreatureSetTypeGadgetReqOuterClass.IceCreatureSetTypeGadgetReq;
import emu.grasscutter.server.packet.recv.HandlerIceCreatureSetTypeGadgetReq;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

final class IceCreatureWheelTest {
    private static IceCreatureConfig config() {
        var config = new IceCreatureConfig();
        config.parseTypes(
                JsonParser.parseString(
                        """
                        [{"id":2,"order":1,"gadgetIdList":[73094010,73190019],"EIJNPJAALDL":20},
                         {"id":20,"order":7,"gadgetIdList":[73174002,73174007],"EIJNPJAALDL":50},
                         {"id":99,"gadgetIdList":[70000000]}]
                        """));
        return config;
    }

    @Test
    void listsEveryWheelTypeAndSkinAndPreservesAValidNonCubeChoice() throws Exception {
        var config = config();
        var player = new Player();
        var initial = IceCreatureSystem.data(player, config);
        assertEquals(
                List.of(73094010, 73174002, 73174007, 73190019),
                initial.getUnlockedGadgetIdListList());
        assertEquals(
                List.of(2, 20),
                initial.getTypeGadgetListList().stream().map(v -> v.getType()).toList());
        assertEquals(20, config.typeOf(73174007));
        assertEquals(50, config.costOf(20));
        assertFalse(initial.getUnlockedGadgetIdListList().contains(70000000));

        // Independently encoded 7.2 request for type 20's alternate gadget and auto-replace = 2.
        var req =
                IceCreatureSetTypeGadgetReq.parseFrom(
                        HexFormat.of().parseHex("18f797f22228024814"));
        assertEquals(73174007, req.getGadgetId());
        assertTrue(IceCreatureSystem.setTypeGadget(player, req, config));
        var choice = IceCreatureSystem.data(player, config).getTypeGadgetList(1);
        assertEquals(73174007, choice.getGadgetId());
        assertEquals(2, choice.getAutoReplaceValue());
        assertEquals(73174007, player.getIceCreatureTypeGadgets().get(20));
        assertEquals(
                6439,
                HandlerIceCreatureSetTypeGadgetReq.class.getAnnotation(Opcodes.class).value());
    }

    @Test
    void invalidCrossTypeOrUnknownSelectionsCannotOverwriteTheSavedChoice() {
        var config = config();
        var player = new Player();
        var valid =
                IceCreatureSetTypeGadgetReq.newBuilder()
                        .setType(20)
                        .setGadgetId(73174007)
                        .setAutoReplaceValue(1)
                        .build();
        assertTrue(IceCreatureSystem.setTypeGadget(player, valid, config));
        for (var req :
                List.of(
                        valid.toBuilder().setGadgetId(73094010).build(),
                        valid.toBuilder().setType(99).build(),
                        valid.toBuilder().setAutoReplaceValue(3).build())) {
            assertFalse(IceCreatureSystem.setTypeGadget(player, req, config));
        }
        assertEquals(
                73174007,
                IceCreatureSystem.data(player, config).getTypeGadgetList(1).getGadgetId());
        assertEquals(1, player.getIceCreatureAutoReplace().get(20));
        player.getIceCreatureTypeGadgets().put(20, -1);
        assertEquals(
                73174002,
                IceCreatureSystem.data(player, config).getTypeGadgetList(1).getGadgetId());
    }

    @Test
    void buildRequestReadsNativeReplacementGuidFlagAndDetailFields() throws Exception {
        // GEBEKBFOCHK: build_detail 2 (empty), gadget 15, replace fixed64 11, flag 12.
        var req =
                IceCreatureBuildReq.parseFrom(
                        HexFormat.of().parseHex("120078f797f2225a0808070605040302016001"));
        assertEquals(73174007, req.getGadgetId());
        assertEquals(List.of(0x0102030405060708L), req.getReplaceGuidListList());
        assertTrue(req.getUnk11());
        assertTrue(req.hasBuildDetail());
    }

    @Test
    void successfulBuildAndSceneRestoreUseNativeInfoEnvelopeFields() throws Exception {
        var owner =
                new Player() {
                    @Override
                    public String getNickname() {
                        return "Tester";
                    }
                };
        owner.setUid(123);
        var record = new IceCreatureRecord();
        record.setGuid(0x0102030405060708L);
        record.setGadgetId(73174007);
        record.setEntityId(0x04000001);
        record.setSceneId(3);
        record.setMapLayerId(123456);
        record.setAreaId(55);
        record.setCreateTime(0x12345678);
        record.setPos(new Position(1, 2, 3));
        var info = IceCreatureSystem.info(owner, record);
        var rsp = IceCreatureBuildRsp.newBuilder().setIceCreatureGadgetInfo(info).build();
        var input = CodedInputStream.newInstance(rsp.toByteArray());
        assertEquals(66, input.readTag(), "IEJNLENCAIO info field 8");
        byte[] wire = input.readByteArray();
        var fields = CodedInputStream.newInstance(wire);
        boolean gadget = false, entity = false, guid = false, time = false, layer = false;
        while (!fields.isAtEnd()) {
            int tag = fields.readTag();
            switch (tag) {
                case 96 -> {
                    assertEquals(73174007, fields.readUInt32());
                    gadget = true;
                }
                case 72 -> {
                    assertEquals(0x04000001, fields.readUInt32());
                    entity = true;
                }
                case 120 -> {
                    assertEquals(record.getGuid(), fields.readUInt64());
                    guid = true;
                }
                case 61 -> {
                    assertEquals(0x12345678, fields.readFixed32());
                    time = true;
                }
                case 48 -> {
                    assertEquals(123456, fields.readUInt32());
                    layer = true;
                }
                default -> fields.skipField(tag);
            }
        }
        assertTrue(gadget && entity && guid && time && layer);
        var restored =
                IceCreatureAllGadgetInfoNotify.newBuilder()
                        .setUid(123)
                        .addIceCreatureGadgetInfoList(info)
                        .build();
        var restoreFields = CodedInputStream.newInstance(restored.toByteArray());
        assertEquals(16, restoreFields.readTag());
        assertEquals(123, restoreFields.readUInt32());
        assertEquals(82, restoreFields.readTag(), "HOAJPGFOHHP gadget list field 10");
        assertArrayEquals(wire, restoreFields.readByteArray());
        assertEquals(28174, PacketOpcodes.IceCreatureBuildRsp);
    }
}
