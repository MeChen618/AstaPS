package emu.grasscutter.server.packet.recv;
import emu.grasscutter.game.activity.leylinechallenge.*;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;
@Opcodes(5333)
public final class HandlerStygianStartChallengeReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var p = session.getPlayer();
        if (p == null) return;
        var f = StygianProtocol.fields(payload);
        StygianNativeEntry.enter(p, f.getOrDefault(5, 0));
    }
}
