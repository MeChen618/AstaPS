package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.activity.leylinechallenge.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

@Opcodes(24829)
public final class HandlerStygianConfirmRoundReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var p = session.getPlayer();
        try {
            var request = StygianRoundRequest.parse(payload);
            int result = LeyLineChallengeCombat.confirmRound(p, request);
            Grasscutter.getLogger().info("Stygian confirm round uid={} round={} avatars={} result={}",
                    p.getUid(), request.round(), request.avatars(), result);
        } catch (java.io.IOException invalid) {
            Grasscutter.getLogger().warn("Invalid Stygian round request uid={}: {}", p.getUid(), invalid.getMessage());
        }
    }
}
