package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.*;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

@QuestValueContent(
        value = QUEST_CONTENT_ENTER_VEHICLE,
        aliases = {QUEST_CONTENT_LEAVE_VEHICLE})
public class ContentEnterVehicle extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest,
            QuestData.QuestContentCondition condition,
            String paramStr,
            int... params) {
        int[] target = condition.getParam();
        return target != null
                && target.length > 0
                && target[0] >= 0
                && params.length > 0
                && params[0] > 0
                && (target[0] == 0 || target[0] == params[0]);
    }
}
