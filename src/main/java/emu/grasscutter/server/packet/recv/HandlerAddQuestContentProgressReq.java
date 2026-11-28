package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AddQuestContentProgressReqOuterClass.AddQuestContentProgressReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAddQuestContentProgressRsp;

@Opcodes(PacketOpcodes.AddQuestContentProgressReq)
public class HandlerAddQuestContentProgressReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = AddQuestContentProgressReq.parseFrom(payload);

        // Find all conditions in quest that are the same as the given one
        var type = QuestContent.getContentTriggerByValue(req.getContentType());
        queueReportedProgress(session.getPlayer(), type, req.getParam());

        session.send(new PacketAddQuestContentProgressRsp(req.getContentType()));
    }

    static void queueReportedProgress(Player player, QuestContent type, int param) {
        if (type == QuestContent.QUEST_CONTENT_ENTER_MY_WORLD_SCENE) {
            var scene = player.getScene();
            if (scene != null && scene.getId() == param) {
                HandlerPostEnterSceneReq.queueOwnWorldSceneEntryQuestEvent(player, scene);
            }
        } else if (type != null) {
            player.getQuestManager().queueEvent(type, param);
        }
    }
}
