package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.quest.ParentQuestVideoKey;
import emu.grasscutter.game.quest.QuestEncryptionKey;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.proto.FinishedParentQuestNotifyOuterClass.FinishedParentQuestNotify;
import emu.grasscutter.net.proto.FinishedParentQuestUpdateNotifyOuterClass.FinishedParentQuestUpdateNotify;
import emu.grasscutter.net.proto.GetParentQuestVideoKeyReqOuterClass.GetParentQuestVideoKeyReq;
import emu.grasscutter.net.proto.GetParentQuestVideoKeyRspOuterClass.GetParentQuestVideoKeyRsp;
import emu.grasscutter.net.proto.ParentQuestOuterClass.ParentQuest;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketFinishedParentQuestNotify;
import emu.grasscutter.server.packet.send.PacketFinishedParentQuestUpdateNotify;
import emu.grasscutter.server.packet.send.PacketGetParentQuestVideoKeyRsp;
import emu.grasscutter.utils.JsonUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class ParentQuestVideoRequestTest {
    private static final int PARENT = 991823;
    // Synthetic data: these values are not keys for any game asset.
    private static final String KEY1 = "00112233445566778899aabbccddeeff";
    private static final String KEY2 = "18446744073709551615";
    private ParentQuestVideoKey oldKey;
    private QuestEncryptionKey oldQuestKey;
    private MainQuestData oldParent;

    @BeforeEach
    void setUp() {
        oldKey = GameData.getParentQuestVideoKeys().put(PARENT, new ParentQuestVideoKey(KEY1, KEY2));
        oldParent = GameData.getMainQuestDataMap().put(
                PARENT, JsonUtils.decode("{subQuests:[]}", MainQuestData.class));
        oldQuestKey = GameData.getMainQuestEncryptionMap().put(
                PARENT, JsonUtils.decode("{mainQuestId:991823,encryptionKey:123}", QuestEncryptionKey.class));
    }

    @AfterEach
    void restore() {
        restore(GameData.getParentQuestVideoKeys(), oldKey);
        restore(GameData.getMainQuestDataMap(), oldParent);
        restore(GameData.getMainQuestEncryptionMap(), oldQuestKey);
    }

    private static <T> void restore(Map<Integer, T> map, T previous) {
        if (previous == null) map.remove(PARENT);
        else map.put(PARENT, previous);
    }

    @Test
    void handlesTheNativeRequestAndAnswersArchivedQuestsWithoutAnActiveQuest() throws Exception {
        assertEquals(2974, HandlerGetParentQuestVideoKeyReq.class.getAnnotation(Opcodes.class).value());
        // Native request tag 0x10 (field 2), value 5006. Do not derive this from our descriptor.
        assertEquals(5006, GetParentQuestVideoKeyReq.parseFrom(new byte[] {0x10, (byte) 0x8e, 0x27})
                .getParentQuestId());
        var session = new RecordingSession(); // Deliberately no player/active quest manager.
        var request = GetParentQuestVideoKeyReq.newBuilder().setParentQuestId(PARENT).build();
        new HandlerGetParentQuestVideoKeyReq().handle(session, new byte[0], request.toByteArray());

        assertEquals(1, session.sent.size());
        assertEquals(9768, session.sent.getFirst().getOpcode());
        var response = GetParentQuestVideoKeyRsp.parseFrom(session.sent.getFirst().getData());
        assertEquals(0, response.getRetcode());
        assertEquals(PARENT, response.getParentQuestId());
        // Independent wire numbers: string field 3 is the SECOND cache component.
        assertEquals(Map.of(3, KEY2, 4, KEY1), wireStrings(session.sent.getFirst().getData()));
    }

    @Test
    void absentVideoKeysReturnFailureAndNeverFallBackToQuestEncryptionKeys() throws Exception {
        GameData.getParentQuestVideoKeys().remove(PARENT);
        var packet = new PacketGetParentQuestVideoKeyRsp(PARENT);
        var response = GetParentQuestVideoKeyRsp.parseFrom(packet.getData());
        assertNotEquals(0, response.getRetcode());
        assertEquals(PARENT, response.getParentQuestId());
        assertTrue(wireStrings(packet.getData()).isEmpty());
        var input = CodedInputStream.newInstance(packet.getData());
        boolean foundRetcode = false;
        for (int tag; (tag = input.readTag()) != 0;) {
            if (tag == 112) { // field 14, varint. The old uint64 video_key here was incorrect.
                assertNotEquals(0, input.readInt32());
                foundRetcode = true;
            } else input.skipField(tag);
        }
        assertTrue(foundRetcode);
    }

    @Test
    void aSingleConfiguredComponentStaysAStringWithoutInventingTheOther() throws Exception {
        GameData.getParentQuestVideoKeys().put(PARENT, new ParentQuestVideoKey("", KEY2));
        var packet = new PacketGetParentQuestVideoKeyRsp(PARENT);
        assertEquals(0, GetParentQuestVideoKeyRsp.parseFrom(packet.getData()).getRetcode());
        assertEquals(Map.of(3, KEY2), wireStrings(packet.getData()));
    }

    @Test
    void loginAndAllParentUpdateVariantsUseTheSameKeysWithoutAdvancingTheQuest() throws Exception {
        var player = new Player();
        var quest = new GameMainQuest(player, PARENT);
        quest.setAddedByCommand(true);
        player.getQuestManager().getMainQuests().put(PARENT, quest);
        var before = quest.getState();
        var vars = quest.getQuestVars().clone();

        var login = FinishedParentQuestNotify.parseFrom(new PacketFinishedParentQuestNotify(player).getData());
        assertParentKeys(login.getParentQuestListList().stream()
                .filter(p -> p.getParentQuestId() == PARENT).findFirst().orElseThrow());
        for (var packet : List.of(
                new PacketFinishedParentQuestUpdateNotify(quest),
                new PacketFinishedParentQuestUpdateNotify(List.of(quest)),
                new PacketFinishedParentQuestUpdateNotify(PARENT))) {
            var update = FinishedParentQuestUpdateNotify.parseFrom(packet.getData());
            assertParentKeys(update.getParentQuestList(0));
        }
        assertEquals(before, quest.getState());
        assertFalse(quest.isFinished());
        assertArrayEquals(vars, quest.getQuestVars());
    }

    private static void assertParentKeys(ParentQuest parent) throws Exception {
        assertEquals(Map.of(395, KEY1, 773, KEY2), wireStrings(parent.toByteArray()));
    }

    private static Map<Integer, String> wireStrings(byte[] bytes) throws Exception {
        var result = new HashMap<Integer, String>();
        var input = CodedInputStream.newInstance(bytes);
        for (int tag; (tag = input.readTag()) != 0;) {
            int field = tag >>> 3;
            if (field == 3 || field == 4 || field == 395 || field == 773) {
                assertEquals(2, tag & 7);
                result.put(field, input.readStringRequireUtf8());
            } else input.skipField(tag);
        }
        return result;
    }

    private static final class RecordingSession extends GameSession {
        final List<BasePacket> sent = new ArrayList<>();

        RecordingSession() { super(null); }

        @Override
        public void send(BasePacket packet) { sent.add(packet); }
    }
}
