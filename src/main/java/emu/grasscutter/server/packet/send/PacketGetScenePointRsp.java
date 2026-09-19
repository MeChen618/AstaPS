package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetScenePointRspOuterClass.GetScenePointRsp;
import java.util.LinkedHashSet;

/**
 * Per-scene unlock+unhide+areas. Only points in the player's unlocked set are unlocked; everything
 * else stays locked (so map fog / waypoints follow statue & manual unlocks).
 */
public class PacketGetScenePointRsp extends BasePacket {

    public PacketGetScenePointRsp(Player player, int sceneId) {
        super(PacketOpcodes.GetScenePointRsp);

        GetScenePointRsp.Builder p = GetScenePointRsp.newBuilder().setSceneId(sceneId);

        LinkedHashSet<Integer> pointIds = new LinkedHashSet<>();
        var perScene = GameData.getScenePointsPerScene();
        if (perScene != null) {
            var scenePoints = perScene.get(sceneId);
            if (scenePoints != null) {
                pointIds.addAll(scenePoints);
            }
        }
        if (pointIds.isEmpty()) {
            var unlocked = player.getUnlockedScenePoints(sceneId);
            if (unlocked != null && !unlocked.isEmpty()) {
                pointIds.addAll(unlocked);
            }
        }
        if (pointIds.isEmpty()) {
            var all = GameData.getScenePointIdList();
            if (all != null) {
                pointIds.addAll(all);
            }
        }

        var unlockedSet = player.getUnlockedScenePoints(sceneId);
        boolean has7 = false;
        int unlockedCount = 0;
        int lockedCount = 0;
        for (int pointId : pointIds) {
            boolean locked =
                    player.isScenePointForceLocked(sceneId, pointId)
                            || !unlockedSet.contains(pointId);

            if (locked) {
                p.addLockedPointList(pointId);
                p.addUnhidePointList(pointId);
                lockedCount++;
            } else {
                p.addUnlockedPointList(pointId);
                p.addUnhidePointList(pointId);
                unlockedCount++;
            }
            if (pointId == 7) has7 = unlockedSet.contains(7);
        }

        // Map fog areas: only what the player has unlocked (via statues / waypoints).
        var areas = player.getUnlockedSceneAreas(sceneId);
        if (areas.isEmpty() && sceneId == 3) {
            p.addUnlockAreaList(1);
        } else {
            for (int areaId : areas) {
                p.addUnlockAreaList(areaId);
            }
        }

        Grasscutter.getLogger()
                .info(
                        "GetScenePointRsp sceneId={} total={} unlocked={} locked={} hasStatue7={} uid={}",
                        sceneId,
                        pointIds.size(),
                        unlockedCount,
                        lockedCount,
                        has7,
                        player.getUid());

        this.setData(p);
    }
}
