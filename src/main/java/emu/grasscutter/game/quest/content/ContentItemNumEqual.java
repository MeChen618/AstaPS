package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_ITEM_NUM_EQUAL;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

/**
 * QUEST_CONTENT_ITEM_NUM_EQUAL — param[0] = item id, param[1] = required count.
 *
 * <p>Reads the player's inventory at evaluation time so the event only has to say "items changed";
 * a stale payload can never complete the step with an old count.
 */
@QuestValueContent(QUEST_CONTENT_ITEM_NUM_EQUAL)
public class ContentItemNumEqual extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        var p = condition.getParam();
        if (p == null || p.length < 2) return false;
        int have = quest.getOwner().getInventory().getItemCountById(p[0]);
        return have == p[1];
    }
}
