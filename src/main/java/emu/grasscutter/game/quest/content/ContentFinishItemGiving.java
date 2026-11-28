package emu.grasscutter.game.quest.content;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestContent;

@QuestValueContent(QuestContent.QUEST_CONTENT_FINISH_ITEM_GIVING)
public final class ContentFinishItemGiving extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        var expected = condition.getParam();
        return params.length == 2 && params[0] > 0
                && expected != null && expected.length >= 2 && expected[0] == params[0]
                && (expected[1] == 0 || expected[1] == params[1]);
    }
}
