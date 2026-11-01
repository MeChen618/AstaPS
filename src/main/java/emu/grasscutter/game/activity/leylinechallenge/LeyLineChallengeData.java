package emu.grasscutter.game.activity.leylinechallenge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.utils.FileUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native data for activity 5269, Stygian Onslaught. */
public final class LeyLineChallengeData {
    private LeyLineChallengeData() {}

    public record Level(
            int id,
            int difficulty,
            int reviseLevel,
            List<Integer> watcherIds,
            List<Integer> levelConfigIds) {}

    public record Schedule(int id, long begin, long end, List<Integer> levelIds, int rewardWatcherId) {}

    public record Difficulty(int id, int dungeonId, int requiredCompletion) {}

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId CN = ZoneId.of("Asia/Shanghai");

    private static final Map<Integer, Level> LEVELS = loadLevels();
    private static final Map<Integer, Difficulty> DIFFICULTIES = loadDifficulties();
    private static final List<Schedule> SCHEDULES = loadSchedules();
    private static final Map<Integer, String> LEVEL_CONFIG_ICONS = loadLevelConfigIcons();

    private static JsonElement read(String name) {
        try (var reader = Files.newBufferedReader(
                FileUtils.getResourcePath("ExcelBinOutput/" + name), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (Exception e) {
            Grasscutter.getLogger().warn("Cannot read LeyLine data {}", name, e);
            return null;
        }
    }

    private static List<Integer> ints(JsonObject row, String key) {
        if (!row.has(key) || !row.get(key).isJsonArray()) return List.of();
        var result = new ArrayList<Integer>();
        for (var element : row.getAsJsonArray(key)) {
            try {
                result.add(element.getAsInt());
            } catch (RuntimeException ignored) {
            }
        }
        return List.copyOf(result);
    }

    private static Map<Integer, Level> loadLevels() {
        var result = new HashMap<Integer, Level>();
        var root = read("LeyLineLevelExcelConfigData.json");
        if (root == null || !root.isJsonArray()) return Map.of();
        for (var element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            var row = element.getAsJsonObject();
            try {
                int id = row.get("id").getAsInt();
                int difficulty = row.has("MMKEJFCFMBN") ? row.get("MMKEJFCFMBN").getAsInt() : 0;
                int revise = row.has("reviseLevel") ? row.get("reviseLevel").getAsInt() : 0;
                result.put(
                        id,
                        new Level(
                                id,
                                difficulty,
                                revise,
                                ints(row, "watcherList"),
                                ints(row, "levelConfigList")));
            } catch (RuntimeException ignored) {
            }
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, Difficulty> loadDifficulties() {
        var result = new HashMap<Integer, Difficulty>();
        var root = read("LeyLineDifficultyExcelConfigData.json");
        if (root == null || !root.isJsonArray()) return Map.of();
        for (var element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            var row = element.getAsJsonObject();
            try {
                int id = row.get("id").getAsInt();
                int dungeon = row.has("leyLineDungeonId") ? row.get("leyLineDungeonId").getAsInt() : 0;
                int required = row.has("requiredDifficultyCompletionToUnlock")
                        ? row.get("requiredDifficultyCompletionToUnlock").getAsInt() : 0;
                result.put(id, new Difficulty(id, dungeon, required));
            } catch (RuntimeException ignored) {
            }
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, String> loadLevelConfigIcons() {
        var result = new HashMap<Integer, String>();
        var root = read("LeyLineLevelConfigExcelConfigData.json");
        if (root == null || !root.isJsonArray()) return Map.of();
        for (var element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            var row = element.getAsJsonObject();
            try {
                int id = row.get("id").getAsInt();
                String icon = row.has("specialMonsterIcon")
                        ? row.get("specialMonsterIcon").getAsString()
                        : "";
                result.put(id, icon == null ? "" : icon);
            } catch (RuntimeException ignored) {
            }
        }
        return Map.copyOf(result);
    }

    public static String levelConfigIcon(int levelConfigId) {
        return LEVEL_CONFIG_ICONS.getOrDefault(levelConfigId, "");
    }

    private static long parseTime(String text) {
        try {
            return LocalDateTime.parse(text.trim(), TIME_FORMAT).atZone(CN).toEpochSecond();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static List<Schedule> loadSchedules() {
        var result = new ArrayList<Schedule>();
        var root = read("LeyLineChallengeExcelConfigData.json");
        if (root == null || !root.isJsonArray()) return List.of();
        for (var element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            var row = element.getAsJsonObject();
            try {
                int id = row.get("scheduleId").getAsInt();
                long begin = parseTime(row.get("scheduleStartTime").getAsString());
                long end = parseTime(row.get("scheduleEndTime").getAsString());
                int reward = row.has("rewardWatcherId") ? row.get("rewardWatcherId").getAsInt() : 0;
                result.add(new Schedule(id, begin, end, ints(row, "levelIdList"), reward));
            } catch (RuntimeException ignored) {
            }
        }
        result.sort(Comparator.comparingLong(Schedule::begin));
        return List.copyOf(result);
    }

    public static Schedule currentSchedule() {
        long now = System.currentTimeMillis() / 1000;
        for (var schedule : SCHEDULES) {
            if (now >= schedule.begin() && now <= schedule.end()) return schedule;
        }
        return SCHEDULES.isEmpty() ? null : SCHEDULES.get(SCHEDULES.size() - 1);
    }

    public static boolean isScheduleActive(Schedule schedule) {
        if (schedule == null) return false;
        long now = System.currentTimeMillis() / 1000;
        return now >= schedule.begin() && now <= schedule.end();
    }

    /** Level id for a 1-based difficulty inside the schedule. */
    public static int levelIdForDifficulty(Schedule schedule, int difficulty) {
        if (schedule == null || difficulty <= 0 || difficulty > schedule.levelIds().size()) return 0;
        return schedule.levelIds().get(difficulty - 1);
    }

    public static Level level(int levelId) {
        return LEVELS.get(levelId);
    }

    public static int difficultyForLevel(int levelId) {
        var level = LEVELS.get(levelId);
        return level == null ? 0 : level.difficulty();
    }

    public static Difficulty difficulty(int difficulty) {
        return DIFFICULTIES.get(difficulty);
    }

    public static int dungeonForDifficulty(int difficulty) {
        var data = DIFFICULTIES.get(difficulty);
        int candidate = data == null ? 0 : data.dungeonId();
        if (candidate > 0 && GameData.getDungeonDataMap().containsKey(candidate)) return candidate;
        // The extracted dungeon table contains 20003-20005 only; reuse those scenes for 4-6.
        int fallback = 20003 + Math.floorMod(difficulty - 1, 3);
        if (GameData.getDungeonDataMap().containsKey(fallback)) return fallback;
        return 20003;
    }

    public static int difficultyForDungeon(int dungeonId) {
        for (var data : DIFFICULTIES.values()) {
            if (data.dungeonId() == dungeonId) return data.id();
        }
        if (dungeonId >= 20003 && dungeonId <= 20005) return dungeonId - 20002;
        return 0;
    }

}
