package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerGetForceQuitBanInfoRsp;

@Opcodes(PacketOpcodes.PlayerGetForceQuitBanInfoReq)
public class HandlerPlayerGetForceQuitBanInfoReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {

        // 查询处罚不会退出联机；当前没有强退匹配处罚。
        session.send(
                new PacketPlayerGetForceQuitBanInfoRsp(
                        session.getPlayer() == null
                                ? RetcodeOuterClass.Retcode.RET_SVR_ERROR_VALUE
                                : RetcodeOuterClass.Retcode.RET_SUCC_VALUE));
    }
}
