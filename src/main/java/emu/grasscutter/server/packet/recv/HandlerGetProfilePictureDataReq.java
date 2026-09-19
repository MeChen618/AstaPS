package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetProfilePictureDataRsp;

@Opcodes(PacketOpcodes.GetProfilePictureDataReq) // 7.0 = 2896
public class HandlerGetProfilePictureDataReq extends PacketHandler {

    /** 7.0 Rsp opcode from PacketOpcodes.GetProfilePictureDataRsp */
    private static final int RSP_OPCODE = PacketOpcodes.GetProfilePictureDataRsp; // 20816

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int seq = 0;
        try {
            if (header != null && header.length > 0) {
                seq = PacketHead.parseFrom(header).getClientSequenceId();
            }
        } catch (Exception ignored) {
        }

        var player = session.getPlayer();
        Grasscutter.getLogger()
                .info(
                        "GetProfilePictureDataReq uid={} seq={} -> rsp {}",
                        player != null ? player.getUid() : 0,
                        seq,
                        RSP_OPCODE);

        // Primary correct 7.0 response.
        session.send(new PacketGetProfilePictureDataRsp(player, RSP_OPCODE, 0, seq));
        // Also push notify-shaped list on Beyond notify id (harmless if unused).
        session.send(new PacketGetProfilePictureDataRsp(player, 21088, 20, seq));
    }
}
