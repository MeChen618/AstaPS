package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_QUEST_GLOBAL_VAR_GREATER;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

/** QUEST_CONTENT_QUEST_GLOBAL_VAR_GREATER — param[0] = key, param[1] = value the var must exceed. */
@QuestValueContent(QUEST_CONTENT_QUEST_GLOBAL_VAR_GREATER)
public class ContentQuestGlobalVarGreater extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        var p = condition.getParam();
        if (p == null || p.length < 2) return false;
        var actual = quest.getOwner().getQuestManager().getQuestGlobalVarValue(p[0]);
        return actual != null && actual > p[1];
    }
}
