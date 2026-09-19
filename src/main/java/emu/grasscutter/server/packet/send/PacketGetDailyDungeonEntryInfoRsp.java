package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.dungeon.DungeonEntryData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Adventurer Handbook → Domains.
 *
 * <p>Generated OuterClass {@code writeTo} is corrupt for the dungeon-id list (field 16000). We
 * manually encode. 7.0 {@code all-in-one.proto} and LunaGC FileDescriptor disagree on field
 * numbers, so {@link #sendBothLayouts} pushes both; client keeps the layout it understands.
 */
public class PacketGetDailyDungeonEntryInfoRsp extends BasePacket {

    public enum WireLayout {
        /** all-in-one.proto / 7.0 CmdID dump */
        V70,
        /** LunaGC embedded FileDescriptor (matches known-good 6.6 builder layout) */
        DESCRIPTOR
    }

    public PacketGetDailyDungeonEntryInfoRsp(Integer sceneID) {
        this(null, sceneID, WireLayout.V70);
    }

    public PacketGetDailyDungeonEntryInfoRsp(Player player, Integer sceneID) {
        this(player, sceneID, WireLayout.V70);
    }

    public PacketGetDailyDungeonEntryInfoRsp(Player player, Integer sceneID, WireLayout layout) {
        super(PacketOpcodes.GetDailyDungeonEntryInfoRsp);
        this.setData(buildPayload(player, sceneID, layout));
    }

    /** Push both wire layouts (descriptor first, 7.0 last). */
    public static void sendBothLayouts(Player player, Integer sceneID) {
        if (player == null || player.getSession() == null) {
            return;
        }
        player.getSession().send(new PacketGetDailyDungeonEntryInfoRsp(player, sceneID, WireLayout.DESCRIPTOR));
        player.getSession().send(new PacketGetDailyDungeonEntryInfoRsp(player, sceneID, WireLayout.V70));
    }

    private static byte[] buildPayload(Player player, Integer sceneID, WireLayout layout) {
        int sceneId = sceneID == null ? 3 : sceneID;
        int playerLevel = player != null ? player.getLevel() : 60;
        List<byte[]> entries = new ArrayList<>();

        for (DungeonEntryData data : GameData.getDungeonEntryDataMap().values()) {
            if (data.getSceneId() != sceneId) {
                continue;
            }
            // Prefer handbook-flagged; if none resolve, fall through still helps UI.
            if (!data.isShowInAdvHandbook()) {
                continue;
            }
            byte[] entry = buildEntryBytes(data, playerLevel, layout);
            if (entry != null) {
                entries.add(entry);
            }
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(512 + entries.size() * 64);
            CodedOutputStream out = CodedOutputStream.newInstance(baos);
            int listField = layout == WireLayout.V70 ? 12 : 14;
            for (byte[] entry : entries) {
                out.writeByteArray(listField, entry);
            }
            out.flush();
            Grasscutter.getLogger()
                    .info(
                            "GetDailyDungeonEntryInfoRsp ({}) sceneId={} level={} entries={}",
                            layout,
                            sceneId,
                            playerLevel,
                            entries.size());
            return baos.toByteArray();
        } catch (Exception e) {
            Grasscutter.getLogger().error("GetDailyDungeonEntryInfoRsp build failed", e);
            return new byte[0];
        }
    }

    private static byte[] buildEntryBytes(DungeonEntryData data, int playerLevel, WireLayout layout) {
        int[] dungeonIds = resolveDungeonIds(data);
        if (dungeonIds == null || dungeonIds.length == 0) {
            return null;
        }
        int recommend = pickRecommendDungeonId(dungeonIds, playerLevel);

        List<Integer> ordered = new ArrayList<>(dungeonIds.length);
        ordered.add(recommend);
        for (int id : dungeonIds) {
            if (id != recommend) {
                ordered.add(id);
            }
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(96);
            CodedOutputStream out = CodedOutputStream.newInstance(baos);
            if (layout == WireLayout.V70) {
                // DailyDungeonEntryInfo (all-in-one.proto)
                out.writeBool(4, true); // EDEOIILGGJC
                out.writeBool(5, true); // is_quick_open
                out.writeUInt32(8, recommend);
                out.writeByteArray(9, buildDungeonEntryInfoBytes(recommend, layout));
                out.writeBool(10, true); // AKNPDDFHEKO
                out.writeUInt32(11, data.getDungeonEntryId());
                out.writeUInt32(12, data.getId());
                writePackedUInt32(out, 15, ordered); // ODDLACNLJOF
            } else {
                // FileDescriptor / known-good 6.6 layout
                writePackedUInt32(out, 3, ordered); // HAOIOGCMAIM
                out.writeBool(4, true); // is_quick_open
                out.writeBool(7, true); // IJCNGAGBNBO
                out.writeBool(8, true); // KCBBHMHAGBK
                out.writeUInt32(10, data.getId()); // dungeon_entry_config_id
                out.writeUInt32(11, recommend); // recommend_dungeon_id
                out.writeByteArray(12, buildDungeonEntryInfoBytes(recommend, layout));
                out.writeUInt32(14, data.getDungeonEntryId()); // dungeon_entry_id
            }
            out.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static void writePackedUInt32(CodedOutputStream out, int field, List<Integer> values)
            throws Exception {
        if (values.isEmpty()) {
            return;
        }
        int size = 0;
        for (int v : values) {
            size += CodedOutputStream.computeUInt32SizeNoTag(v);
        }
        out.writeTag(field, 2); // length-delimited (packed)
        out.writeUInt32NoTag(size);
        for (int v : values) {
            out.writeUInt32NoTag(v);
        }
    }

    private static byte[] buildDungeonEntryInfoBytes(int dungeonId, WireLayout layout) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(16);
        CodedOutputStream out = CodedOutputStream.newInstance(baos);
        if (layout == WireLayout.V70) {
            out.writeUInt32(15, dungeonId);
            out.writeBool(8, true);
        } else {
            out.writeUInt32(1, dungeonId);
            out.writeBool(14, true);
        }
        out.flush();
        return baos.toByteArray();
    }

    private static int pickRecommendDungeonId(int[] dungeonIds, int playerLevel) {
        int bestId = dungeonIds[dungeonIds.length - 1];
        int bestLimit = -1;
        for (int id : dungeonIds) {
            DungeonData dungeon = GameData.getDungeonDataMap().get(id);
            int limit = dungeon != null ? dungeon.getLimitLevel() : 0;
            if (limit <= playerLevel && limit >= bestLimit) {
                bestLimit = limit;
                bestId = id;
            }
        }
        return bestId;
    }

    private static int[] resolveDungeonIds(DungeonEntryData data) {
        try {
            ScenePointEntry point =
                    GameData.getScenePointEntryById(data.getSceneId(), data.getDungeonEntryId());
            if (point == null || point.getPointData() == null) {
                return null;
            }
            PointData pd = point.getPointData();
            if (pd.getDungeonRandomList() != null && pd.getDungeonRandomList().length > 0) {
                pd.updateDailyDungeon();
            }
            int[] ids = pd.getDungeonIds();
            if (ids != null && ids.length > 0) {
                return Arrays.copyOf(ids, ids.length);
            }
            return pd.getDungeonRandomList();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
