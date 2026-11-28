package emu.grasscutter.server.packet.recv;
import emu.grasscutter.game.activity.leylinechallenge.*;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;
@Opcodes(21621)
public final class HandlerPlayerGuestConfirmMatchReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var p = session.getPlayer();
        if (p == null) return;
        if (!StygianMatchmaking.of(p.getServer()).reserved(p.getUid())) return;
        var f = StygianProtocol.fields(payload);
        StygianMatchmaking.of(p.getServer()).confirm(p, f.getOrDefault(11, 0), f.getOrDefault(7, 0) != 0, true);
    }
}
