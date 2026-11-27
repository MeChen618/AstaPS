package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.SceneDummyPoints;

import java.util.Arrays;
import java.util.regex.Pattern;

@QuestValueContent(
        value = QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL,
        aliases = {
            QUEST_CONTENT_QUEST_GLOBAL_VAR_NOT_EQUAL,
            QUEST_CONTENT_QUEST_GLOBAL_VAR_GREATER,
            QUEST_CONTENT_QUEST_GLOBAL_VAR_LESS,
            QUEST_CONTENT_ITEM_NUM_EQUAL,
            QUEST_CONTENT_ITEM_NUM_GREATER,
            QUEST_CONTENT_ITEM_NUM_LESS,
            QUEST_CONTENT_CITY_LEVEL_UP,
            QUEST_CONTENT_PRESENT_AT_SPECIFIC_SCENE,
            QUEST_CONTENT_LEAVE_SCENE_RANGE,
            QUEST_CONTENT_PARENT_QUEST_STATE_EQUAL,
            QUEST_CONTENT_UNLOCKED_RECIPE,
            QUEST_CONTENT_NOT_UNLOCKED_RECIPE
        })
public final class ContentCurrentState extends BaseContent {
    private static final SceneDummyPoints SCENE_DUMMY_POINTS = new SceneDummyPoints();
    private static final Pattern POINT =
            Pattern.compile("\\[(\\d+)_[^:]+:([^,]+),([^,]+),([^,\\]]+)(?:,[^\\]]*)?\\]");

    @Override
    public boolean execute(
            GameQuest quest,
            QuestData.QuestContentCondition condition,
            String paramStr,
            int... params) {
        var expected = condition.getParam();
        var type = condition.getType();
        if (type == QUEST_CONTENT_UNLOCKED_RECIPE || type == QUEST_CONTENT_NOT_UNLOCKED_RECIPE) {
            var ids = parseIds(condition.getParamStr());
            if (ids.length == 0
                    || (params.length > 0 && Arrays.stream(ids).noneMatch(id -> id == params[0]))) {
                return false;
            }
            var cooking = quest.getOwner().getCookingManager();
            if (Arrays.stream(ids).anyMatch(id -> !GameData.getCookRecipeDataMap().containsKey(id)))
                return false;
            // 40015 的三种菜谱是可选项，学会其中一种即可。
            boolean learned = Arrays.stream(ids).anyMatch(cooking::isRecipeUnlocked);
            return type == QUEST_CONTENT_UNLOCKED_RECIPE ? learned : !learned;
        }
        int required = type == QUEST_CONTENT_PRESENT_AT_SPECIFIC_SCENE ? 1 : 2;
        if (expected == null || expected.length < required) return false;
        if (params.length > 0 && params[0] != expected[0]) return false;
        var player = quest.getOwner();
        // 事件只负责唤醒检查，数值以当前存档为准。
        return switch (type) {
            case QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL,
                    QUEST_CONTENT_QUEST_GLOBAL_VAR_NOT_EQUAL,
                    QUEST_CONTENT_QUEST_GLOBAL_VAR_GREATER,
                    QUEST_CONTENT_QUEST_GLOBAL_VAR_LESS ->
                    compare(
                            type,
                            player.getQuestManager().getQuestGlobalVarValue(expected[0]),
                            expected[1]);
            case QUEST_CONTENT_ITEM_NUM_EQUAL,
                    QUEST_CONTENT_ITEM_NUM_GREATER,
                    QUEST_CONTENT_ITEM_NUM_LESS ->
                    compare(type, player.getInventory().getItemCountById(expected[0]), expected[1]);
            case QUEST_CONTENT_CITY_LEVEL_UP -> {
                if (expected[0] <= 0 || expected[1] <= 0) yield false;
                var cities = player.getCityInfoData();
                var city = cities == null ? null : cities.get(expected[0]);
                yield city != null && city.getLevel() >= expected[1];
            }
            case QUEST_CONTENT_PRESENT_AT_SPECIFIC_SCENE ->
                    player.getScene() != null && player.getScene().getId() == expected[0];
            case QUEST_CONTENT_PARENT_QUEST_STATE_EQUAL -> {
                var parent = player.getQuestManager().getMainQuestById(expected[0]);
                yield parent != null && parent.getState().getValue() == expected[1];
            }
            case QUEST_CONTENT_LEAVE_SCENE_RANGE -> {
                var scene = player.getScene();
                if (scene == null || scene.getId() != expected[0] || expected[1] < 0) yield false;
                var center = parsePoint(condition.getParamStr(), expected[0]);
                yield center != null && player.getPosition().computeDistance(center) > expected[1];
            }
            default -> false;
        };
    }

    static boolean compare(QuestContent type, int actual, int expected) {
        return switch (type) {
            case QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL, QUEST_CONTENT_ITEM_NUM_EQUAL ->
                    actual == expected;
            case QUEST_CONTENT_QUEST_GLOBAL_VAR_NOT_EQUAL -> actual != expected;
            case QUEST_CONTENT_QUEST_GLOBAL_VAR_GREATER, QUEST_CONTENT_ITEM_NUM_GREATER ->
                    actual > expected;
            case QUEST_CONTENT_QUEST_GLOBAL_VAR_LESS, QUEST_CONTENT_ITEM_NUM_LESS ->
                    actual < expected;
            default -> false;
        };
    }

    public static boolean checkOnStart(QuestContent type) {
        return switch (type) {
            case QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL,
                    QUEST_CONTENT_QUEST_GLOBAL_VAR_NOT_EQUAL,
                    QUEST_CONTENT_QUEST_GLOBAL_VAR_GREATER,
                    QUEST_CONTENT_QUEST_GLOBAL_VAR_LESS,
                    QUEST_CONTENT_ITEM_NUM_EQUAL,
                    QUEST_CONTENT_ITEM_NUM_GREATER,
                    QUEST_CONTENT_ITEM_NUM_LESS,
                    QUEST_CONTENT_CITY_LEVEL_UP,
                    QUEST_CONTENT_PRESENT_AT_SPECIFIC_SCENE,
                    QUEST_CONTENT_PARENT_QUEST_STATE_EQUAL,
                    QUEST_CONTENT_UNLOCKED_RECIPE,
                    QUEST_CONTENT_NOT_UNLOCKED_RECIPE ->
                    true;
            default -> false;
        };
    }

    static int[] parseIds(String spec) {
        if (spec == null || spec.isBlank()) return new int[0];
        try {
            int[] ids =
                    Arrays.stream(spec.split(",", -1))
                            .map(String::trim)
                            .mapToInt(Integer::parseInt)
                            .toArray();
            return Arrays.stream(ids).allMatch(id -> id > 0) ? ids : new int[0];
        } catch (NumberFormatException ignored) {
            return new int[0];
        }
    }

    static Position parsePoint(String spec, int sceneId) {
        if (spec == null || spec.isBlank()) return null;
        var point = spec.trim();
        if (!point.startsWith("[")) return SCENE_DUMMY_POINTS.resolve(sceneId, point);

        var match = POINT.matcher(point);
        if (!match.matches()) return null;
        try {
            if (Integer.parseInt(match.group(1)) != sceneId) return null;
            float x = Float.parseFloat(match.group(2));
            float y = Float.parseFloat(match.group(3));
            float z = Float.parseFloat(match.group(4));
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                    ? new Position(x, y, z)
                    : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
