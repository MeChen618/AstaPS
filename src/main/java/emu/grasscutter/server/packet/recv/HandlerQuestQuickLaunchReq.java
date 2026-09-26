package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.QuestQuickLaunchReq._QuestQuickLaunchReq;
import emu.grasscutter.net.proto.QuestQuickLaunchRsp._QuestQuickLaunchRsp;
import emu.grasscutter.server.game.GameSession;

/** Quest quick launch: had no handler, so the client got no reply. Accept it and echo the request. */
@Opcodes(PacketOpcodes._QuestQuickLaunchReq)
public class HandlerQuestQuickLaunchReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = _QuestQuickLaunchReq.parseFrom(payload);
        Grasscutter.getLogger()
                .info(
                        "QuestQuickLaunchReq uid={} quest={} focus={}",
                        session.getPlayer().getUid(),
                        req.getQuestId(),
                        req.getIsEnterFocusMode());

        var rsp =
                _QuestQuickLaunchRsp.newBuilder()
                        .setQuestId(req.getQuestId())
                        .setIsEnterFocusMode(req.getIsEnterFocusMode())
                        .build();
        var packet = new BasePacket(PacketOpcodes._QuestQuickLaunchRsp);
        packet.setData(rsp);
        session.send(packet);
    }
}
