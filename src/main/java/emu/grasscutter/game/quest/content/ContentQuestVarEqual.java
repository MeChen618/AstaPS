package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.*;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

@QuestValueContent(
        value = QUEST_CONTENT_QUEST_VAR_EQUAL,
        aliases = {QUEST_CONTENT_QUEST_VAR_NOT_EQUAL})
public class ContentQuestVarEqual extends BaseContent {

    @Override
    public boolean execute(
            GameQuest quest,
            QuestData.QuestContentCondition condition,
            String paramStr,
            int... params) {
        return matches(quest, condition, params);
    }

    static boolean matches(
            GameQuest quest, QuestData.QuestContentCondition condition, int... params) {
        int[] expected = condition.getParam();
        if (expected == null || expected.length < 2 || expected[0] < 0) return false;
        if (params.length > 0 && params[0] != expected[0]) return false;
        var parent = quest.getMainQuest();
        if (params.length > 2 && params[2] != parent.getParentQuestId()) return false;
        int[] vars = parent.getQuestVars();
        if (vars == null || expected[0] >= vars.length) return false;
        int actual = vars[expected[0]];
        return switch (condition.getType()) {
            case QUEST_CONTENT_QUEST_VAR_EQUAL -> actual == expected[1];
            case QUEST_CONTENT_QUEST_VAR_NOT_EQUAL -> actual != expected[1];
            case QUEST_CONTENT_QUEST_VAR_GREATER -> actual > expected[1];
            case QUEST_CONTENT_QUEST_VAR_LESS -> actual < expected[1];
            default -> false;
        };
    }
}
