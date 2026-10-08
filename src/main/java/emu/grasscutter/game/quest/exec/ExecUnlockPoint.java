package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.managers.StatueTalkQuests;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(QuestExec.QUEST_EXEC_UNLOCK_POINT)
public class ExecUnlockPoint extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        int sceneId = Integer.parseInt(paramStr[0]);
        int pointId = Integer.parseInt(paramStr[1]);

        var scenePointEntry = GameData.getScenePointEntryById(sceneId, pointId);
        boolean isStatue =
                scenePointEntry != null && isStatuePoint(scenePointEntry.getPointData());

        boolean unlocked = quest.getOwner().getProgressManager().unlockTransPoint(sceneId, pointId, isStatue);
        if (sceneId == 3 && (pointId == 6 || pointId == 7)) {
            Grasscutter.getLogger()
                    .info(
                            "[quest-point] source=quest-exec uid={} main={} sub={} scene={} point={} unlocked={}",
                            quest.getOwner().getUid(),
                            quest.getMainQuestId(),
                            quest.getSubQuestId(),
                            sceneId,
                            pointId,
                            unlocked);
        }
        return unlocked;
    }

    static boolean isStatuePoint(PointData pointData) {
        return StatueTalkQuests.isStatuePoint(pointData);
    }
}
