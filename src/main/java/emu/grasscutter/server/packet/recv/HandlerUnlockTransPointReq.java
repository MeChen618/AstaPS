package emu.grasscutter.server.packet.recv;

import emu.grasscutter.data.GameData;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass;
import emu.grasscutter.net.proto.UnlockTransPointReqOuterClass.UnlockTransPointReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketUnlockTransPointRsp;

@Opcodes(PacketOpcodes.UnlockTransPointReq)
public class HandlerUnlockTransPointReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        UnlockTransPointReq req = UnlockTransPointReq.parseFrom(payload);
        var entry = GameData.getScenePointEntryById(req.getSceneId(), req.getPointId());
        boolean isStatue =
                emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(
                        entry != null ? entry.getPointData() : null);
        boolean unlocked =
                session
                        .getPlayer()
                        .getProgressManager()
                        .unlockTransPoint(req.getSceneId(), req.getPointId(), isStatue);
        session
                .getPlayer()
                .sendPacket(
                        new PacketUnlockTransPointRsp(
                                unlocked
                                        ? RetcodeOuterClass.Retcode.RET_SUCC
                                        : RetcodeOuterClass.Retcode.RET_FAIL));
    }
}
