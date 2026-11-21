package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.game.activity.leylinechallenge.LeyLineChallengeCombat;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.GetAllSceneGalleryInfoReq)
public class HandlerGetAllSceneGalleryInfoReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        session.send(LeyLineChallengeCombat.active(player)
                ? LeyLineChallengeCombat.galleries(player)
                : new BasePacket(PacketOpcodes.GetAllSceneGalleryInfoRsp));
    }
}
