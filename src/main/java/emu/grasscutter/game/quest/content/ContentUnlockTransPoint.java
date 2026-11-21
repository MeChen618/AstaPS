package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.*;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

import java.util.Arrays;

@QuestValueContent(
        value = QUEST_CONTENT_UNLOCK_TRANS_POINT,
        aliases = {QUEST_CONTENT_UNLOCK_ANY_TRANS_POINT})
public class ContentUnlockTransPoint extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest,
            QuestData.QuestContentCondition condition,
            String paramStr,
            int... params) {
        var expected = condition.getParam();
        if (expected == null || expected.length < 2 || expected[0] <= 0) return false;
        var sceneId = expected[0];
        if (params.length > 0 && params[0] != sceneId) return false;
        var scenePoints = quest.getOwner().getUnlockedScenePoints().get(sceneId);
        if (scenePoints == null) return false;
        if (condition.getType() == QUEST_CONTENT_UNLOCK_ANY_TRANS_POINT) {
            int[] candidates = ContentCurrentState.parseIds(condition.getParamStr());
            return Arrays.stream(candidates).anyMatch(scenePoints::contains);
        }
        return scenePoints.contains(expected[1]);
    }
}
