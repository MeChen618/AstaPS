package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DungeonEntryInfoOuterClass.DungeonEntryInfo;
import emu.grasscutter.net.proto.DungeonEntryPointInfoOuterClass.DungeonEntryPointInfo;
import emu.grasscutter.net.proto.DungeonEntryInfoRspOuterClass.DungeonEntryInfoRsp;
import java.util.*;

public class PacketDungeonEntryInfoRsp extends BasePacket {

    public PacketDungeonEntryInfoRsp(PointData pointData) {
        super(PacketOpcodes.DungeonEntryInfoRsp);

        DungeonEntryInfoRsp.Builder proto =
                DungeonEntryInfoRsp.newBuilder().setPointId(pointData.getId());

        if (pointData.getDungeonIds() != null) {
            for (int dungeonId : pointData.getDungeonIds()) {
                DungeonEntryInfo info = DungeonEntryInfo.newBuilder().setDungeonId(dungeonId).build();
                proto.addDungeonEntryList(info);
            }
        }

        this.setData(proto);
    }

    /**
     * Used in conjunction with quest-related dungeons.
     *
     * @param pointData The data associated with the dungeon.
     * @param additional A collection of additional quest-related dungeon IDs.
     */
    public PacketDungeonEntryInfoRsp(PointData pointData, List<Integer> additional) {
        super(PacketOpcodes.DungeonEntryInfoRsp);

        var packet = DungeonEntryInfoRsp.newBuilder().setPointId(pointData.getId());

        // Add dungeon IDs from the point data.
        if (pointData.getDungeonIds() != null) {
            Arrays.stream(pointData.getDungeonIds())
                    .forEach(
                            id -> packet.addDungeonEntryList(DungeonEntryInfo.newBuilder().setDungeonId(id)));
        }

        // Add additional dungeon IDs.
        additional.forEach(
                id -> packet.addDungeonEntryList(DungeonEntryInfo.newBuilder().setDungeonId(id)));

        this.setData(packet);
    }

    /** 7.0 entry-point response with a recommended dungeon and scene metadata. */
    public PacketDungeonEntryInfoRsp(PointData pointData, List<Integer> additional, int sceneId, int worldLevel) {
        super(PacketOpcodes.DungeonEntryInfoRsp);

        try {
            if (pointData.getDungeonRandomList() != null && pointData.getDungeonRandomList().length > 0) {
                pointData.updateDailyDungeon();
            }
        } catch (Throwable ignored) {
            // Keep serving the static dungeon list when daily data is incomplete.
        }

        LinkedHashSet<Integer> dungeonIds = new LinkedHashSet<>();
        if (pointData.getDungeonIds() != null) {
            for (int id : pointData.getDungeonIds()) if (id > 0) dungeonIds.add(id);
        }
        if (additional != null) {
            for (Integer id : additional) if (id != null && id > 0) dungeonIds.add(id);
        }

        int recommend = pickRecommend(dungeonIds, worldLevel);
        DungeonEntryInfoRsp.Builder response = DungeonEntryInfoRsp.newBuilder()
                .setRetcode(0)
                .setPointId(pointData.getId())
                .setRecommendDungeonId(recommend)
                .setJpmdjmadpil(true);
        DungeonEntryPointInfo.Builder entryPoint = DungeonEntryPointInfo.newBuilder()
                .setPointId(pointData.getId())
                .setSceneId(sceneId > 0 ? sceneId : 3)
                .setRecommendDungeonId(recommend);

        for (int dungeonId : dungeonIds) {
            DungeonEntryInfo info = DungeonEntryInfo.newBuilder().setDungeonId(dungeonId).build();
            response.addDungeonEntryList(info);
            entryPoint.addDungeonEntryList(info);
        }
        response.addDungeonEntryPointList(entryPoint);
        this.setData(response);
    }

    private static int pickRecommend(Collection<Integer> dungeonIds, int worldLevel) {
        int fallback = 0;
        int best = 0;
        int bestLimit = Integer.MIN_VALUE;
        for (int dungeonId : dungeonIds) {
            fallback = dungeonId;
            DungeonData data = GameData.getDungeonDataMap().get(dungeonId);
            int limit = data == null ? 0 : data.getLimitLevel();
            if (limit <= worldLevel && limit >= bestLimit) {
                best = dungeonId;
                bestLimit = limit;
            }
        }
        return best != 0 ? best : fallback;
    }

    public PacketDungeonEntryInfoRsp() {
        super(PacketOpcodes.DungeonEntryInfoRsp);

        DungeonEntryInfoRsp proto = DungeonEntryInfoRsp.newBuilder().setRetcode(1).build();

        this.setData(proto);
    }
}
