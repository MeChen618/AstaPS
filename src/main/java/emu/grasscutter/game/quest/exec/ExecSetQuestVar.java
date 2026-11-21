package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(
        value = QuestExec.QUEST_EXEC_SET_QUEST_VAR,
        aliases = QuestExec.QUEST_EXEC_UPDATE_PARENT_QUEST_REWARD_INDEX)
public class ExecSetQuestVar extends QuestExecHandler {
    @Override
    public boolean execute(
            GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        if (condition.getType() == QuestExec.QUEST_EXEC_UPDATE_PARENT_QUEST_REWARD_INDEX)
            return quest.getMainQuest().updateRewardIndex(paramStr);
        quest.getMainQuest()
                .setQuestVar(Integer.parseInt(paramStr[0]), Integer.parseInt(paramStr[1]));
        return true;
    }
}
