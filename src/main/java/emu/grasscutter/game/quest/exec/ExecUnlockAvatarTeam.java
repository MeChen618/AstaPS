package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

/**
 * QUEST_EXEC_UNLOCK_AVATAR_TEAM
 *
 * <p>Carries <b>no parameters</b> in the quest data and unlocks the "avatar team" feature in the
 * client UI. The server has no team-count gate (a player can always create teams), so there is
 * nothing to change server side.
 *
 * <p>Handled explicitly instead of falling through to the "no handler registered" branch, so the
 * quest data is no longer silently dropped. Logged at debug for traceability.
 */
@QuestValueExec(QuestExec.QUEST_EXEC_UNLOCK_AVATAR_TEAM)
public class ExecUnlockAvatarTeam extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        Grasscutter.getLogger()
                .debug(
                        "[QuestExec] UNLOCK_AVATAR_TEAM (client-side UI unlock, no server state) subQuest={}",
                        quest.getSubQuestId());
        return true;
    }
}
