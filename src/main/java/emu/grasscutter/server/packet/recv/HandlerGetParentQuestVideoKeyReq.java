package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.quest.ParentQuestVideoKey;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetParentQuestVideoKeyReqOuterClass.GetParentQuestVideoKeyReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetParentQuestVideoKeyRsp;

@Opcodes(PacketOpcodes.GetParentQuestVideoKeyReq)
public final class HandlerGetParentQuestVideoKeyReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = GetParentQuestVideoKeyReq.parseFrom(payload);
        if (!GameData.getParentQuestVideoKeys().containsKey(req.getParentQuestId())) {
            Grasscutter.getLogger().debug(
                    "No video-key metadata for parent quest {} in {}.",
                    Integer.toUnsignedString(req.getParentQuestId()),
                    ParentQuestVideoKey.FILE_NAME);
        }
        // Travel Log can request an archived quest absent from the player's active quest manager.
        session.send(new PacketGetParentQuestVideoKeyRsp(req.getParentQuestId()));
    }
}
