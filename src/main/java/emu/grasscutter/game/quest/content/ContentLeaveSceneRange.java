package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_LEAVE_SCENE_RANGE;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.world.Position;

/**
 * QUEST_CONTENT_LEAVE_SCENE_RANGE — param[0] = scene id, param[1] = radius in metres.
 *
 * <p>Both params are data-proven: all 22 distinct param[0] values used by the quest table are
 * members of {@code SceneExcelConfigData} (3, 6, 7, 100, 1004, 20129, 51007, ...), and param[1] is
 * always a round metre figure (10/20/30/40/50/60/70/80/100/150/200).
 *
 * <p>The centre is the quest's own guide point, which the data carries inline, e.g.
 * {@code guide.param = ["[3_15689:8836.877,75.241,8461.724]", "30"]} — the bracket holds
 * {@code sceneId_pointId:x,y,z} (a rotation triple may follow, it is ignored here). A row using
 * {@code LEAVE_SCENE_RANGE [3,100]} has its guide point in scene 3, so this reads as "in scene 3,
 * beyond 100 m from the guide point the step is left".
 *
 * <p>Used almost exclusively as a fail condition, and fired periodically from {@code Player.onTick}
 * because straying is a position change, not an event the client reports.
 */
@QuestValueContent(QUEST_CONTENT_LEAVE_SCENE_RANGE)
public class ContentLeaveSceneRange extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        var p = condition.getParam();
        if (p == null || p.length < 2) return false;

        var player = quest.getOwner();
        var scene = player.getScene();
        // Range only applies inside the scene it was declared for; leaving the scene entirely is
        // what QUEST_CONTENT_LEAVE_SCENE is for.
        if (scene == null || scene.getId() != p[0]) return false;

        var point = parseGuidePoint(quest.getQuestData() == null ? null : quest.getQuestData().getGuide());
        if (point == null) return false;

        var current = player.getPosition();
        if (current == null) return false;

        return current.computeDistance(point) > p[1];
    }

    /** Parses {@code "[3_15689:8836.877,75.241,8461.724]"} into a position. */
    static Position parseGuidePoint(QuestData.Guide guide) {
        if (guide == null) return null;
        var params = guide.getParam();
        if (params == null || params.isEmpty()) return null;
        var spec = params.get(0);
        if (spec == null) return null;

        var colon = spec.indexOf(':');
        if (colon < 0) return null;

        var coords = spec.substring(colon + 1).trim();
        while (coords.endsWith("]") || coords.endsWith(" ")) {
            coords = coords.substring(0, coords.length() - 1);
        }
        var parts = coords.split(",");
        if (parts.length < 3) return null;
        try {
            return new Position(
                    Float.parseFloat(parts[0].trim()),
                    Float.parseFloat(parts[1].trim()),
                    Float.parseFloat(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
