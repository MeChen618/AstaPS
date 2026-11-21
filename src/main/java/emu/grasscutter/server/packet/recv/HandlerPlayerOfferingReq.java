package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.entity.gadget.OfferingHelper;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.PlayerOfferingReq)
public class HandlerPlayerOfferingReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        OfferingHelper.handleOfferingRequest(session.getPlayer(), payload, header);
    }
}
