package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueExec;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

import java.util.LinkedHashSet;

/** 任务锁点和地图显隐共用参数校验，显隐不改变永久解锁进度。 */
@QuestValueExec(
        value = QuestExec.QUEST_EXEC_LOCK_POINT,
        aliases = {QuestExec.QUEST_EXEC_HIDE_SCENE_POINT, QuestExec.QUEST_EXEC_UNHIDE_SCENE_POINT})
public final class ExecLockPoint extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... params) {
        if (condition == null
                || condition.getType() == null
                || params == null
                || params.length != 2) return false;
        int sceneId;
        var pointIds = new LinkedHashSet<Integer>();
        try {
            sceneId = Integer.parseInt(params[0]);
            for (var value : params[1].split(",", -1)) {
                int pointId = Integer.parseInt(value.trim());
                if (!validPoint(sceneId, pointId)) return false;
                pointIds.add(pointId);
            }
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
        var manager = quest.getOwner().getProgressManager();
        return switch (condition.getType()) {
            case QUEST_EXEC_HIDE_SCENE_POINT ->
                    manager.setTransPointVisibility(sceneId, pointIds, false);
            case QUEST_EXEC_UNHIDE_SCENE_POINT ->
                    manager.setTransPointVisibility(sceneId, pointIds, true);
            case QUEST_EXEC_LOCK_POINT -> {
                for (int pointId : pointIds) manager.lockTransPoint(sceneId, pointId);
                yield true;
            }
            default -> false;
        };
    }

    static boolean validPoint(int sceneId, int pointId) {
        return sceneId > 0 && pointId > 0;
    }
}
