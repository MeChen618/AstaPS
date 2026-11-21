package emu.grasscutter.server.packet.recv;
import emu.grasscutter.game.activity.leylinechallenge.*;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;
@Opcodes(25847)
public final class HandlerPlayerStartMatchReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var p = session.getPlayer();
        if (p == null) return;
        var f = StygianProtocol.fields(payload);
        // Leave other matchmaking requests to their own handlers. Upstream currently has no
        // generic matchmaking implementation, so returning here preserves its existing behavior.
        int dungeon = f.getOrDefault(11, 0);
        if (f.getOrDefault(12, 0) != 1 || dungeon < 20003 || dungeon > 20005) return;
        StygianMatchmaking.of(p.getServer()).start(p, f.getOrDefault(12, 0), dungeon, f.getOrDefault(7, 0), f.getOrDefault(8, 0));
    }
}
