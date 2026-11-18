package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

/**
 * QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL — param[0] = quest global var key, param[1] = expected value.
 *
 * <p>The current value is read from the quest manager instead of trusting the event payload, so a
 * stale/again-fired event can never complete the step on an old value.
 */
@QuestValueContent(QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL)
public class ContentQuestGlobalVarEqual extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        var p = condition.getParam();
        if (p == null || p.length < 2) return false;
        var actual = quest.getOwner().getQuestManager().getQuestGlobalVarValue(p[0]);
        return actual != null && actual == p[1];
    }
}
