package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerSetPauseReqOuterClass.PlayerSetPauseReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerSetPauseRsp;

@Opcodes(PacketOpcodes.PlayerSetPauseReq)
public class HandlerPlayerSetPauseReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = PlayerSetPauseReq.parseFrom(payload);
        var player = session.getPlayer();
        var world = player.getWorld();

        // The native fresh-player intro toggles pause before the first World exists. These requests
        // are valid and their second false->true cycle is the 7.1 intro handoff boundary.
        if (world == null) {
            session.send(new PacketPlayerSetPauseRsp(Retcode.RET_SUCC));
            BornIntroGate.notePause(session, req.getIsPaused());
            return;
        }

        if (player.isInMultiplayer()) {
            session.send(new PacketPlayerSetPauseRsp(Retcode.RET_FAIL));
        } else {
            world.setPaused(req.getIsPaused());
            session.send(new PacketPlayerSetPauseRsp(Retcode.RET_SUCC));
        }
    }
}
