package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_ITEM_NUM_GREATER;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

/** QUEST_CONTENT_ITEM_NUM_GREATER — param[0] = item id, param[1] = count the player must exceed. */
@QuestValueContent(QUEST_CONTENT_ITEM_NUM_GREATER)
public class ContentItemNumGreater extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        var p = condition.getParam();
        if (p == null || p.length < 2) return false;
        int have = quest.getOwner().getInventory().getItemCountById(p[0]);
        return have > p[1];
    }
}
