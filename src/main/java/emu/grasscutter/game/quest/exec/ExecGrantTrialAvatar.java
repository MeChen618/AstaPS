package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(QuestExec.QUEST_EXEC_GRANT_TRIAL_AVATAR)
public class ExecGrantTrialAvatar extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        if (paramStr == null || paramStr.length == 0) {
            Grasscutter.getLogger().warn(
                    "Trial avatar grant missing ID: main={} sub={}",
                    quest.getMainQuestId(), quest.getSubQuestId());
            return false;
        }

        try {
            int trialAvatarId = Integer.parseInt(paramStr[0]);
            quest.getOwner().getTeamManager().addTrialAvatar(trialAvatarId, quest.getMainQuestId());
            Grasscutter.getLogger().info(
                    "[quest-trial] granted uid={} main={} sub={} trialAvatarId={}",
                    quest.getOwner().getUid(),
                    quest.getMainQuestId(),
                    quest.getSubQuestId(),
                    trialAvatarId);
            return true;
        } catch (RuntimeException exception) {
            Grasscutter.getLogger().error(
                    "Trial avatar grant failed: main={} sub={} trialAvatar={}",
                    quest.getMainQuestId(),
                    quest.getSubQuestId(),
                    paramStr[0],
                    exception);
            return false;
        }
    }
}
