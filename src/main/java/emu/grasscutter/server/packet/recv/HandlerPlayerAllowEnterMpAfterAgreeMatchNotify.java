package emu.grasscutter.server.packet.recv;
import emu.grasscutter.game.activity.leylinechallenge.*;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;
@Opcodes(24608)
public final class HandlerPlayerAllowEnterMpAfterAgreeMatchNotify extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var p = session.getPlayer();
        if (p == null) return;
        var f = StygianProtocol.fields(payload);
        StygianMatchmaking.of(p.getServer()).permit(p, f.getOrDefault(10, 0));
    }
}
