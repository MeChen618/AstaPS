package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CancelCoopTaskReqOuterClass;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketCancelCoopTaskRsp;

@Opcodes(PacketOpcodes.CancelCoopTaskReq)
public class HandlerCancelCoopTaskReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        CancelCoopTaskReqOuterClass.CancelCoopTaskReq req =
                CancelCoopTaskReqOuterClass.CancelCoopTaskReq.parseFrom(payload);
        var chapterId = req.getChapterId();
        Grasscutter.getLogger().warn("Call to unimplemented packet CancelCoopTaskReq");
        // 尚未实现任务取消，不能回成功让客户端误删进度。
        session.send(new PacketCancelCoopTaskRsp(chapterId, Retcode.RET_SVR_ERROR_VALUE));
    }
}
