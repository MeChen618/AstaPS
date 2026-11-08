package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_SKILL;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;

@QuestValueContent(QUEST_CONTENT_SKILL)
public class ContentSkill extends BaseContent {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestContentCondition condition, String paramStr, int... params) {
        int expect = condition.getParam()[0];
        int got = params.length > 0 ? params[0] : -1;
        boolean ok = expect == got;
        // 教学关用的是“通用技能 id”（10000~10100），但角色实际技能 id 是 11xxx/10067 等，
        // 这里做兼容：只要任务要的是通用 id，任何技能释放都算达成
        if (!ok && expect >= 10000 && expect <= 10100 && got > 0) {
            ok = true;
        }
        emu.grasscutter.Grasscutter.getLogger().warn("[QUEST_SKILL] quest=" + quest.getSubQuestId()
                + " expect=" + condition.getParam()[0] + " got=" + (params.length > 0 ? params[0] : -1)
                + " -> " + ok);
        return ok;
    }
}
