package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.BeyondProfilePictureTable;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SetPlayerHeadImageReqOuterClass.SetPlayerHeadImageReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetPlayerHeadImageRsp;

@Opcodes(PacketOpcodes.SetPlayerHeadImageReq)
public final class HandlerSetPlayerHeadImageReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int id = SetPlayerHeadImageReq.parseFrom(payload).getProfilePictureId();
        int sequence =
                header == null || header.length == 0
                        ? 0
                        : PacketHead.parseFrom(header).getClientSequenceId();
        boolean valid = BeyondProfilePictureTable.find(id) != null;
        if (valid) session.getPlayer().setHeadImage(id);
        session.send(
                new PacketSetPlayerHeadImageRsp(
                        session.getPlayer(), valid ? 0 : Retcode.RET_FAIL_VALUE, sequence));
    }
}
