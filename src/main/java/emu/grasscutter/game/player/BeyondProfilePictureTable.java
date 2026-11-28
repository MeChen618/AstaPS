package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Portrait and frame choices from the server's resource tables. */
public final class BeyondProfilePictureTable {
    public static final class Entry {
        public final int id;
        public final String iconPath;
        public final int unlockParam;
        public final String
                unlockType; // [v32] AJFEJFNMCKP：BY_PARENT_QUEST / BY_ITEM / BY_AVATAR / BY_DEFAULT

        public Entry(int id, String iconPath, int unlockParam) {
            this(id, iconPath, unlockParam, "");
        }

        public Entry(int id, String iconPath, int unlockParam, String unlockType) {
            this.id = id;
            this.iconPath = iconPath;
            this.unlockParam = unlockParam;
            this.unlockType = unlockType;
        }
    }

    private static volatile List<Entry> entries;
    private static volatile List<Integer> frameIds;

    public static List<Integer> allFrameIds() {
        var current = frameIds;
        if (current != null) return current;
        synchronized (BeyondProfilePictureTable.class) {
            if (frameIds != null) return frameIds;
            var ids = new java.util.TreeSet<Integer>();
            try {
                var rows =
                        JsonUtils.loadToList(
                                FileUtils.getExcelPath("ProfileFrameExcelConfigData.json"),
                                Map.class);
                if (rows != null)
                    for (var row : rows) {
                        var id = row.get("id");
                        if (id instanceof Number value && value.intValue() > 0)
                            ids.add(value.intValue());
                    }
            } catch (java.io.IOException e) {
                Grasscutter.getLogger().warn("Failed to load profile frames", e);
            }
            return frameIds = List.copyOf(ids);
        }
    }

    public static List<Entry> all() {
        List<Entry> cur = entries;
        if (cur != null) return cur;
        synchronized (BeyondProfilePictureTable.class) {
            if (entries != null) return entries;
            List<Entry> list = new ArrayList<>();
            try {
                Path path = FileUtils.getExcelPath("ProfilePictureExcelConfigData.json");
                if (path == null || !Files.exists(path)) {
                    path =
                            FileUtils.getResourcePath(
                                    "ExcelBinOutput/ProfilePictureExcelConfigData.json");
                }
                List<Map> raw = JsonUtils.loadToList(path, Map.class);
                if (raw != null) {
                    for (Map r : raw) {
                        Object idv = r.get("id");
                        if (idv == null) continue;
                        Object icon = r.get("iconPath");
                        Object up = r.get("unlockParam");
                        Object ut =
                                r.containsKey("NLAPNBMECDF")
                                        ? r.get("NLAPNBMECDF")
                                        : r.get("AJFEJFNMCKP");
                        list.add(
                                new Entry(
                                        ((Number) idv).intValue(),
                                        icon == null ? "" : String.valueOf(icon),
                                        up instanceof Number ? ((Number) up).intValue() : 0,
                                        ut == null ? "" : String.valueOf(ut)));
                    }
                }
            } catch (Throwable t) {
                Grasscutter.getLogger()
                        .warn("BeyondProfilePictureTable load failed: {}", t.toString());
            }
            Grasscutter.getLogger()
                    .info("BeyondProfilePictureTable loaded {} entries", list.size());
            entries = list;
            return list;
        }
    }

    /** 按 id 找一项（找不到返回 null）。 */
    public static Entry find(int id) {
        for (Entry e : all()) {
            if (e.id == id) {
                return e;
            }
        }
        return null;
    }

    private BeyondProfilePictureTable() {}
}
