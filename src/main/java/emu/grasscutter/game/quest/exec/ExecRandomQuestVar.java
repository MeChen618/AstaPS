package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueExec;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.utils.Utils;

@QuestValueExec(
        value = QuestExec.QUEST_EXEC_RANDOM_QUEST_VAR,
        aliases = {QuestExec.QUEST_EXEC_RANDOM_CLOSED_QUEST_VAR})
public class ExecRandomQuestVar extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... params) {
        if (params == null || params.length != 3) return false;
        int index;
        int low;
        int high;
        try {
            index = Integer.parseInt(params[0]);
            low = Integer.parseInt(params[1]);
            high = Integer.parseInt(params[2]);
        } catch (NumberFormatException e) {
            return false;
        }

        var main = quest.getMainQuest();
        var vars = main.getQuestVars();
        if (vars == null || index < 0 || index >= vars.length || low > high) return false;

        if (condition.getType() == QuestExec.QUEST_EXEC_RANDOM_CLOSED_QUEST_VAR) {
            // The widened bound also supports a closed range ending at Integer.MAX_VALUE.
            main.setQuestVar(index, (int) Utils.random.nextLong(low, (long) high + 1));
            // Exec handlers can run after the quest completion save was queued.
            main.save();
        } else {
            if (low == high) return false;
            main.randomQuestVar(index, low, high);
        }
        return true;
    }
}
