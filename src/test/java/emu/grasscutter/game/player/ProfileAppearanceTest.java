package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedInputStream;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.SetPlayerHeadImageRspOuterClass.SetPlayerHeadImageRsp;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerGetProfilePictureDataReq;
import emu.grasscutter.server.packet.recv.HandlerSetPlayerHeadImageReq;
import emu.grasscutter.server.packet.recv.HandlerSetPlayerProfileFrameReq;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.util.*;

@ExtendWith(ServerResourceFixture.class)
final class ProfileAppearanceTest {
    private final Map<Field, Object> originals = new HashMap<>();
    private final byte[] header =
            PacketHead.newBuilder().setClientSequenceId(1357).build().toByteArray();
    private RecordingPlayer player;
    private CaptureSession session;

    @BeforeEach
    void setup() throws Exception {
        replace(
                BeyondProfilePictureTable.class,
                "entries",
                List.of(
                        new BeyondProfilePictureTable.Entry(201, "", 0, "BY_DEFAULT"),
                        new BeyondProfilePictureTable.Entry(202, "", 2010001, "BY_PARENT_QUEST")));
        replace(BeyondProfilePictureTable.class, "frameIds", List.of(100000, 100021));
        replace(ProfilePictureHelper.class, "loaded", true);
        replace(ProfilePictureHelper.class, "picToAvatar", Map.of(201, 10000005, 202, 10000007));
        replace(ProfilePictureHelper.class, "avatarToPic", Map.of(10000005, 201, 10000007, 202));
        player = new RecordingPlayer();
        session = new CaptureSession();
        session.setPlayer(player);
    }

    private void replace(Class<?> owner, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        originals.put(field, field.get(null));
        field.set(null, value);
    }

    @AfterEach
    void restore() throws Exception {
        for (var entry : originals.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    @Test
    void listUsesSeparateNativePackedFieldsAndDoesNotModifyPlayerProgress() throws Exception {
        assertEquals(
                21907, HandlerGetProfilePictureDataReq.class.getAnnotation(Opcodes.class).value());
        new HandlerGetProfilePictureDataReq().handle(session, header, new byte[0]);
        var packet = session.packets.getFirst();
        assertEquals(27386, packet.getOpcode());
        assertEquals(1357, PacketHead.parseFrom(packet.getHeader()).getClientSequenceId());
        var input = CodedInputStream.newInstance(packet.getData());
        Map<Integer, List<Integer>> fields = new TreeMap<>();
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            assertEquals(2, tag & 7);
            int limit = input.pushLimit(input.readRawVarint32());
            var ids = new ArrayList<Integer>();
            while (input.getBytesUntilLimit() > 0) ids.add(input.readUInt32());
            input.popLimit(limit);
            fields.put(tag >>> 3, ids);
        }
        assertEquals(Map.of(5, List.of(100000, 100021), 14, List.of(201, 202)), fields);
        assertEquals(1, session.packets.size());
        assertEquals(0, player.saves);
        assertTrue(player.getQuestManager().getMainQuests().isEmpty());
    }

    @Test
    void nativeSelectionRequestsSaveAndReturnTheFullAppearanceWithSequence() throws Exception {
        assertEquals(
                22526, HandlerSetPlayerHeadImageReq.class.getAnnotation(Opcodes.class).value());
        assertEquals(
                29096, HandlerSetPlayerProfileFrameReq.class.getAnnotation(Opcodes.class).value());
        // Native request vectors: picture field 3 = 202; frame field 2 = 100021.
        new HandlerSetPlayerHeadImageReq()
                .handle(session, header, HexFormat.of().parseHex("18ca01"));
        assertEquals(202, player.getHeadImage());
        assertEquals(1, player.saves);
        assertAppearance(session.packets.getLast(), 202, 100000, 0);
        new HandlerSetPlayerProfileFrameReq()
                .handle(session, header, HexFormat.of().parseHex("10b58d06"));
        assertEquals(100021, player.getProfileFrameId());
        assertEquals(2, player.saves);
        assertAppearance(session.packets.getLast(), 202, 100021, 0);
    }

    @Test
    void unknownSelectionsReturnFailureAndPreserveSavedAppearance() throws Exception {
        new HandlerSetPlayerHeadImageReq().handle(session, header, HexFormat.of().parseHex("1801"));
        new HandlerSetPlayerProfileFrameReq()
                .handle(session, header, HexFormat.of().parseHex("1001"));
        assertEquals(0, player.saves);
        for (var packet : session.packets) {
            assertAppearance(packet, 201, 100000, 1);
        }
    }

    private void assertAppearance(BasePacket packet, int picture, int frame, int failure)
            throws Exception {
        assertEquals(675, packet.getOpcode());
        assertEquals(1357, PacketHead.parseFrom(packet.getHeader()).getClientSequenceId());
        var rsp = SetPlayerHeadImageRsp.parseFrom(packet.getData());
        assertEquals(failure, rsp.getRetcode() == 0 ? 0 : 1);
        assertEquals(picture, rsp.getProfilePicture().getProfilePictureId());
        assertEquals(frame, rsp.getProfilePicture().getProfileFrameId());
        // Check the independently recovered response envelope, not only generated round trips.
        var input = CodedInputStream.newInstance(packet.getData());
        boolean snapshot = false;
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if (tag == 50) {
                snapshot = true;
                input.readByteArray();
            } else {
                assertEquals(120, tag);
                assertNotEquals(0, input.readInt32());
            }
        }
        assertTrue(snapshot);
    }

    private static final class CaptureSession extends GameSession {
        final List<BasePacket> packets = new ArrayList<>();

        CaptureSession() {
            super(null);
        }

        @Override
        public void send(BasePacket packet) {
            packets.add(packet);
        }
    }

    private static final class RecordingPlayer extends Player {
        int saves;

        RecordingPlayer() {
            setHeadImage(201);
            setProfileFrameId(100000);
            saves = 0;
        }

        @Override
        public Optional<emu.grasscutter.game.home.GameHome> tryGetHome() {
            return Optional.empty();
        }

        @Override
        public void save() {
            saves++;
        }
    }
}
