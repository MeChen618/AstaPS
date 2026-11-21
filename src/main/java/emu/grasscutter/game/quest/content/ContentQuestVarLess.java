package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_QUEST_VAR_LESS;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

@QuestValueContent(QUEST_CONTENT_QUEST_VAR_LESS)
public class ContentQuestVarLess extends BaseContent {

    @Override
    public boolean execute(
            GameQuest quest,
            QuestData.QuestContentCondition condition,
            String paramStr,
            int... params) {
        return ContentQuestVarEqual.matches(quest, condition, params);
    }
}
