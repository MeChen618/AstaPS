package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData.QuestExecParam;
import emu.grasscutter.game.dungeons.DungeonSystem;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueExec;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(QuestExec.QUEST_EXEC_MODIFY_DUNGEON_EXIT_POINT)
public final class ExecModifyDungeonExitPoint extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestExecParam condition, String... params) {
        if (params == null || params.length != 3) return false;

        int dungeonId;
        int sceneId;
        int pointId;
        try {
            dungeonId = Integer.parseInt(params[0]);
            sceneId = Integer.parseInt(params[1]);
            pointId = Integer.parseInt(params[2]);
        } catch (NumberFormatException e) {
            return false;
        }
        return DungeonSystem.setQuestDungeonExit(quest.getOwner(), dungeonId, sceneId, pointId);
    }
}
