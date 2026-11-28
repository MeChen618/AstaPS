package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetProfilePictureDataRsp;

/** Lists portraits and frames without changing quests, inventory or the selected appearance. */
@Opcodes(PacketOpcodes.GetProfilePictureDataReq)
public final class HandlerGetProfilePictureDataReq extends PacketHandler {
    public static void sendProfileList(GameSession session, String source) {
        if (session.getPlayer() != null)
            session.send(new PacketGetProfilePictureDataRsp(session.getPlayer()));
    }

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int sequence =
                header == null || header.length == 0
                        ? 0
                        : PacketHead.parseFrom(header).getClientSequenceId();
        session.send(new PacketGetProfilePictureDataRsp(session.getPlayer(), sequence));
    }
}
