package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarPromoteReqOuterClass.AvatarPromoteReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.AvatarPromoteReq)
public class HandlerAvatarPromoteReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // Extra level (90->95->100): the client's cap-breakthrough reuses AvatarPromoteReq.
        // Let the ExtraLevel bridge handle it first; a plain promote has no data past 90/6.
        if (emu.grasscutter.game.avatar.ExtraLevelUiBridge.tryHandle(
                session.getPlayer(), PacketOpcodes.AvatarPromoteReq, payload)) {
            return;
        }

        AvatarPromoteReq req = AvatarPromoteReq.parseFrom(payload);

        // Ascend avatar
        session.getServer().getInventorySystem().promoteAvatar(session.getPlayer(), req.getGuid());
    }
}
