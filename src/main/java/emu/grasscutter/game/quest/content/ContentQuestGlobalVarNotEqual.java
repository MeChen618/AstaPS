package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_QUEST_GLOBAL_VAR_NOT_EQUAL;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

/** QUEST_CONTENT_QUEST_GLOBAL_VAR_NOT_EQUAL — param[0] = key, param[1] = value that must NOT match. */
@QuestValueContent(QUEST_CONTENT_QUEST_GLOBAL_VAR_NOT_EQUAL)
public class ContentQuestGlobalVarNotEqual extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        var p = condition.getParam();
        if (p == null || p.length < 2) return false;
        var actual = quest.getOwner().getQuestManager().getQuestGlobalVarValue(p[0]);
        return actual != null && actual != p[1];
    }
}
