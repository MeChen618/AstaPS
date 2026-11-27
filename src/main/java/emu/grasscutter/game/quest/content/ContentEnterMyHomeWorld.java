package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_ENTER_MY_HOME_WORLD;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueContent;
import java.util.Arrays;

@QuestValueContent(QUEST_CONTENT_ENTER_MY_HOME_WORLD)
public final class ContentEnterMyHomeWorld extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        if (params.length == 0) return false;

        int sceneId = params[0];
        return Arrays.stream(ContentCurrentState.parseIds(condition.getParamStr()))
                .anyMatch(allowedScene -> allowedScene == sceneId);
    }
}
