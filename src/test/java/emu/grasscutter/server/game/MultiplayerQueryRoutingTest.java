package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.TestWorld;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.proto.GetWorldMpInfoRspOuterClass.GetWorldMpInfoRsp;
import emu.grasscutter.net.proto.PlayerGetForceQuitBanInfoRspOuterClass.PlayerGetForceQuitBanInfoRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@ExtendWith(ServerResourceFixture.class)
final class MultiplayerQueryRoutingTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void worldQueryUsesCurrentResponseAndDoesNotChangeMembership(boolean multiplayer)
            throws Exception {
        var session = new CaptureSession();
        var player = new Player();
        var world = TestWorld.create(player);
        var mode = World.class.getDeclaredField("isMultiplayer");
        mode.setAccessible(true);
        mode.setBoolean(world, multiplayer);
        session.setPlayer(player);

        route(session, 3966);

        assertEquals(1, session.sent.size());
        var packet = session.sent.getFirst();
        assertEquals(5681, packet.getOpcode());
        // 原生回包的联机标志在字段 2，单人模式用默认值。
        assertArrayEquals(multiplayer ? new byte[] {0x10, 1} : new byte[0], packet.getData());
        var reply = GetWorldMpInfoRsp.parseFrom(packet.getData());
        assertEquals(0, reply.getRetcode());
        assertEquals(multiplayer, reply.getIsInMpMode());
        assertEquals(0, reply.getQuitMpValidTime());
        assertSame(world, player.getWorld());
        assertEquals(List.of(player), world.getPlayers());
        assertEquals(multiplayer, world.isMultiplayer());
    }

    @Test
    void forceQuitBanQueryDoesNotNeedOrCallTheMultiplayerSystem() throws Exception {
        var session = new CaptureSession();
        var player = new Player();
        var world = TestWorld.create(player);
        session.setPlayer(player);

        // 没有 GameServer；误调用 leaveCoop 会报错并丢失回包。
        route(session, 6686);
        route(session, 6686);

        assertEquals(2, session.sent.size());
        for (var packet : session.sent) {
            assertEquals(26490, packet.getOpcode());
            var reply = PlayerGetForceQuitBanInfoRsp.parseFrom(packet.getData());
            assertEquals(0, reply.getRetcode());
            assertEquals(0, reply.getMatchId());
            assertEquals(0, reply.getExpireTime());
        }
        assertSame(world, player.getWorld());
        assertEquals(List.of(player), world.getPlayers());
        assertTrue(world.transfers.isEmpty());
    }

    @Test
    void queriesWithoutAPlayerReturnAnErrorInsteadOfThrowing() throws Exception {
        var session = new CaptureSession();
        route(session, 3966);
        route(session, 6686);

        assertEquals(2, session.sent.size());
        assertEquals(5681, session.sent.get(0).getOpcode());
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                GetWorldMpInfoRsp.parseFrom(session.sent.get(0).getData()).getRetcode());
        assertEquals(26490, session.sent.get(1).getOpcode());
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                PlayerGetForceQuitBanInfoRsp.parseFrom(session.sent.get(1).getData()).getRetcode());
    }

    @Test
    void worldQueryDuringWorldTeardownReturnsAnError() throws Exception {
        var session = new CaptureSession();
        session.setPlayer(new Player());
        session.getPlayer().setWorld(null);
        route(session, 3966);

        assertEquals(1, session.sent.size());
        assertEquals(
                Retcode.RET_SVR_ERROR_VALUE,
                GetWorldMpInfoRsp.parseFrom(session.sent.getFirst().getData()).getRetcode());
    }

    @Test
    void worldResponseRoundTripsNativeTagsIncludingErrorsAndQuitTime() throws Exception {
        // bool 2、retcode 5、uint32 13；与原生 reader 的 tag 16、40、104 对应。
        var wire = HexFormat.of().parseHex("100128846868ffffffff0f");
        var reply = GetWorldMpInfoRsp.parseFrom(wire);
        assertTrue(reply.getIsInMpMode());
        assertEquals(13316, reply.getRetcode());
        assertEquals(0xffffffffL, Integer.toUnsignedLong(reply.getQuitMpValidTime()));
        assertArrayEquals(wire, reply.toByteArray());
    }

    @Test
    void unimplementedHangoutCancellationReturnsFailureOnTheExpectedRoute() throws Exception {
        var session = new CaptureSession();
        new GameServerPacketHandler(PacketHandler.class)
                .handle(session, 26546, new byte[0], HexFormat.of().parseHex("207b"));

        assertEquals(1, session.sent.size());
        var packet = session.sent.getFirst();
        assertEquals(3565, packet.getOpcode());
        var reply =
                emu.grasscutter.net.proto.CancelCoopTaskRspOuterClass.CancelCoopTaskRsp.parseFrom(
                        packet.getData());
        assertEquals(123, reply.getChapterId());
        assertNotEquals(0, reply.getRetcode());
    }

    private static void route(CaptureSession session, int opcode) {
        new GameServerPacketHandler(PacketHandler.class)
                .handle(session, opcode, new byte[0], new byte[0]);
    }

    private static final class CaptureSession extends GameSession {
        private final List<BasePacket> sent = new ArrayList<>();

        private CaptureSession() {
            super(null);
            setState(SessionState.ACTIVE);
        }

        @Override
        public void send(BasePacket packet) {
            sent.add(packet);
        }
    }
}
