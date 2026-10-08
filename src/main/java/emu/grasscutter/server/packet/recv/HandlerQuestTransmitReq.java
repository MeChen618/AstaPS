package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.QuestTransmitReqOuterClass.QuestTransmitReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketQuestTransmitRsp;
import java.util.ArrayList;

@Opcodes(PacketOpcodes.QuestTransmitReq)
public class HandlerQuestTransmitReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = QuestTransmitReq.parseFrom(payload);
        int subQuestId = req.getQuestId();
        var player = session.getPlayer();
        var mainQuest = player.getQuestManager().getMainQuestById(subQuestId / 100);

        var posAndRot = new ArrayList<Position>();
        boolean result = false;
        boolean hasTarget = mainQuest != null && mainQuest.hasTeleportPosition(subQuestId, posAndRot);
        if (hasTarget) {
            var data = GameData.getTeleportDataMap().get(subQuestId);
            if (data != null && data.getTransmit_points() != null && !data.getTransmit_points().isEmpty()) {
                var sceneId = data.getTransmit_points().get(0).getScene_id();
                result = player.getWorld().transferPlayerToScene(player, sceneId, posAndRot.get(0));
            }
        }

        // The 7.1 client's persistent "Return to quest point" prompt is still under
        // investigation. Record actual button requests without faking a teleport target.
        if (subQuestId >= 35100 && subQuestId < 35400) {
            Grasscutter.getLogger().info(
                    "[quest-return] uid={} sub={} parentLoaded={} teleportConfigured={} transferred={}",
                    player.getUid(), subQuestId, mainQuest != null, hasTarget, result);
        }

        session.send(new PacketQuestTransmitRsp(result, req));
    }
}
