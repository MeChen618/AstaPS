package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_ENTER_MY_WORLD_SCENE;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueContent;

@QuestValueContent(QUEST_CONTENT_ENTER_MY_WORLD_SCENE)
public class ContentEnterMyWorldScene extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        int[] expected = condition.getParam();
        return expected != null && expected.length > 0 && expected[0] > 0
                && params.length > 0 && expected[0] == params[0];
    }
}
