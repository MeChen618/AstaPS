package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

/**
 * QUEST_EXEC_UNLOCK_MIRROR_AVATAR_TEAM
 *
 * <p>No parameters in the quest data. "Mirror avatar team" is a client-side feature of certain
 * story modes (the client builds the mirror team itself); the server keeps no state for it.
 *
 * <p>Handled explicitly so the entry is no longer dropped on the "no handler" path.
 */
@QuestValueExec(QuestExec.QUEST_EXEC_UNLOCK_MIRROR_AVATAR_TEAM)
public class ExecUnlockMirrorAvatarTeam extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        Grasscutter.getLogger()
                .debug(
                        "[QuestExec] UNLOCK_MIRROR_AVATAR_TEAM (client-side, no server state) subQuest={}",
                        quest.getSubQuestId());
        return true;
    }
}
