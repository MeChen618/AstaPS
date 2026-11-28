package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueExec;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import java.util.HashSet;

@QuestValueExec(QuestExec.QUEST_EXEC_DEL_ALL_SPECIFIC_PACK_ITEM)
public final class ExecDelAllSpecificPackItem extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... params) {
        if (params == null || params.length != 1 || params[0] == null) return false;

        var itemIds = new HashSet<Integer>();
        try {
            for (String token : params[0].split(",", -1)) {
                itemIds.add(Integer.parseInt(token.trim()));
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return quest.getOwner().getInventory().removeAllMaterialStacks(itemIds);
    }
}
