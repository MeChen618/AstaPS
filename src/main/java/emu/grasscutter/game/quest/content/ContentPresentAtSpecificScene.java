package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_PRESENT_AT_SPECIFIC_SCENE;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

/**
 * QUEST_CONTENT_PRESENT_AT_SPECIFIC_SCENE — param[0] = scene id, param[1] = unused (always 0).
 *
 * <p>Param semantics are data-proven: all 130 distinct values used by the 7.x quest table are
 * members of {@code SceneExcelConfigData} (3 = Mondstadt windsong, 100, 1004, 20220, 51345, ...).
 *
 * <p>The current scene is read from the player at evaluation time, so it is always the truth; the
 * event is fired whenever a scene becomes ready (see HandlerPostEnterSceneReq).
 */
@QuestValueContent(QUEST_CONTENT_PRESENT_AT_SPECIFIC_SCENE)
public class ContentPresentAtSpecificScene extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        var p = condition.getParam();
        if (p == null || p.length < 1) return false;
        var scene = quest.getOwner().getScene();
        if (scene == null) return false;
        return scene.getId() == p[0];
    }
}
