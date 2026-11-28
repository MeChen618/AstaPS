package emu.grasscutter.server.packet.recv;
import emu.grasscutter.game.activity.leylinechallenge.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
@Opcodes(25059)
public final class HandlerInterruptGalleryReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        if (!LeyLineChallengeCombat.active(session.getPlayer())) return;
        int id = StygianProtocol.fields(payload).getOrDefault(11, 0);
        boolean ok = LeyLineChallengeCombat.interrupt(session.getPlayer(), id);
        session.send(StygianProtocol.packet(22288, 1, id, 2, ok ? 0 : 1101));
    }
}
