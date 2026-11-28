package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData.QuestExecParam;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueExec;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import java.util.HashSet;

@QuestValueExec(QuestExec.QUEST_EXEC_SET_MAP_LAYER_UNLOCK_STATE)
public final class ExecSetMapLayerUnlockState extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestExecParam condition, String... params) {
        if (params == null || params.length != 2 || params[0] == null || params[1] == null) {
            return false;
        }

        String state = params[1].trim();
        if (!"0".equals(state) && !"1".equals(state)) return false;

        var layerIds = new HashSet<Integer>();
        try {
            for (String token : params[0].split(";", -1)) {
                layerIds.add(Integer.parseInt(token.trim()));
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return quest.getOwner().getProgressManager()
                .setMapLayerUnlockState(layerIds, "1".equals(state));
    }
}
