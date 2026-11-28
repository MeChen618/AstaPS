package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_FINISH_DUNGEON;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

@QuestValueContent(QUEST_CONTENT_FINISH_DUNGEON)
public class ContentFinishDungeon extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        var expected = condition.getParam();
        if (expected == null || expected.length == 0 || expected[0] <= 0) return false;
        int dungeonId = expected[0];
        // Start and login checks have no new dungeon event, so use the recorded clear.
        if (params.length > 0 && params[0] != dungeonId) return false;
        var completed = quest.getOwner().getPlayerProgress().getCompletedDungeons();
        return completed != null && completed.contains(dungeonId);
    }
}
