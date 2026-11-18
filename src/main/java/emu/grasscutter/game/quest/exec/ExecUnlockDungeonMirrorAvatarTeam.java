package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

/**
 * QUEST_EXEC_UNLOCK_DUNGEON_MIRROR_AVATAR_TEAM
 *
 * <p>param[0] = dungeon id (e.g. "1333"). Like the mirror variant this only tells the client to
 * allow building the mirror team for that dungeon; the server has no gating state for it.
 */
@QuestValueExec(QuestExec.QUEST_EXEC_UNLOCK_DUNGEON_MIRROR_AVATAR_TEAM)
public class ExecUnlockDungeonMirrorAvatarTeam extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        Grasscutter.getLogger()
                .debug(
                        "[QuestExec] UNLOCK_DUNGEON_MIRROR_AVATAR_TEAM dungeon={} (client-side, no server state)",
                        paramStr != null && paramStr.length > 0 ? paramStr[0] : "?");
        return true;
    }
}
