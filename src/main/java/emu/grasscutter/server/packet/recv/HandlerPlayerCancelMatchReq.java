package emu.grasscutter.server.packet.recv;
import emu.grasscutter.game.activity.leylinechallenge.*;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;
@Opcodes(7618)
public final class HandlerPlayerCancelMatchReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var p = session.getPlayer();
        if (p == null) return;
        if (!StygianMatchmaking.of(p.getServer()).reserved(p.getUid())) return;
        var f = StygianProtocol.fields(payload);
        StygianMatchmaking.of(p.getServer()).cancel(p, f.getOrDefault(15, 0));
    }
}
