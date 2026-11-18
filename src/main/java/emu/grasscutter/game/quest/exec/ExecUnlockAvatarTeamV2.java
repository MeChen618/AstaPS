package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

/**
 * QUEST_EXEC_UNLOCK_AVATAR_TEAM_V2 — same effect as QUEST_EXEC_UNLOCK_AVATAR_TEAM (client-side UI
 * unlock, no parameters, no server state). See that class for the rationale.
 */
@QuestValueExec(QuestExec.QUEST_EXEC_UNLOCK_AVATAR_TEAM_V2)
public class ExecUnlockAvatarTeamV2 extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        Grasscutter.getLogger()
                .debug(
                        "[QuestExec] UNLOCK_AVATAR_TEAM_V2 (client-side UI unlock, no server state) subQuest={}",
                        quest.getSubQuestId());
        return true;
    }
}
