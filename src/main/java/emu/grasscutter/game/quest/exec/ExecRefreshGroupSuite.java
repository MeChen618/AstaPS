package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import lombok.val;

@QuestValueExec(QuestExec.QUEST_EXEC_REFRESH_GROUP_SUITE)
public class ExecRefreshGroupSuite extends QuestExecHandler {
    // The upstream scheduler's integer delay API is one real-time second per step.
    // Script loading happens on its own thread, so retry without blocking the quest/event thread.
    private static final int MAX_SCRIPT_INIT_RETRIES = 10;

    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        return executeWhenReady(quest, paramStr, 0);
    }

    private boolean executeWhenReady(GameQuest quest, String[] paramStr, int attempt) {
        if (paramStr.length < 2) {
            Grasscutter.getLogger()
                    .warn(
                            "Quest {} refresh-group-suite exec has invalid params {}",
                            quest.getSubQuestId(),
                            java.util.Arrays.toString(paramStr));
            return false;
        }

        final int sceneId;
        try {
            sceneId = Integer.parseInt(paramStr[0]);
        } catch (NumberFormatException e) {
            Grasscutter.getLogger()
                    .warn(
                            "Quest {} refresh-group-suite exec has invalid scene id {}",
                            quest.getSubQuestId(),
                            paramStr[0]);
            return false;
        }

        var world = quest.getOwner().getWorld();
        var scene = world != null ? world.getSceneById(sceneId) : null;
        if (scene == null) {
            Grasscutter.getLogger()
                    .warn(
                            "Quest {} could not refresh group suite: scene {} is unavailable",
                            quest.getSubQuestId(),
                            sceneId);
            return false;
        }

        val scriptManager = scene.getScriptManager();
        if (!scriptManager.isInit()) {
            if (!scriptManager.isInitAttempted() && attempt < MAX_SCRIPT_INIT_RETRIES) {
                if (attempt == 0) {
                    Grasscutter.getLogger()
                            .debug(
                                    "Quest {} deferring group-suite refresh in scene {} until scripts initialize",
                                    quest.getSubQuestId(),
                                    sceneId);
                }

                scene.getScheduler()
                        .scheduleDelayedTask(
                                () -> executeWhenReady(quest, paramStr, attempt + 1), 1);
                return true;
            }

            Grasscutter.getLogger()
                    .warn(
                            "Quest {} could not refresh group suite in scene {}: scripts failed to initialize after {} retry(s)",
                            quest.getSubQuestId(),
                            sceneId,
                            attempt);
            return false;
        }

        boolean result = true;
        for (var entry : paramStr[1].split(";")) {
            val entryArray = entry.split(",");
            if (entryArray.length < 2) {
                Grasscutter.getLogger()
                        .warn(
                                "Quest {} refresh-group-suite exec has invalid entry {}",
                                quest.getSubQuestId(),
                                entry);
                result = false;
                continue;
            }

            final int groupId;
            final int suiteId;
            try {
                groupId = Integer.parseInt(entryArray[0]);
                suiteId = Integer.parseInt(entryArray[1]);
            } catch (NumberFormatException e) {
                Grasscutter.getLogger()
                        .warn(
                                "Quest {} refresh-group-suite exec has invalid entry {}",
                                quest.getSubQuestId(),
                                entry);
                result = false;
                continue;
            }

            val group = scriptManager.getGroupById(groupId);
            if (group == null) {
                Grasscutter.getLogger()
                        .warn(
                                "Quest {} could not refresh group {} suite {} in scene {}: group is unavailable",
                                quest.getSubQuestId(),
                                groupId,
                                suiteId,
                                sceneId);
                result = false;
                continue;
            }

            // Quest-owned groups must survive visibility unloading after the suite is activated.
            group.dontUnload = true;

            if (!scriptManager.refreshGroupSuite(groupId, suiteId, quest)) {
                Grasscutter.getLogger()
                        .warn(
                                "Quest {} failed to refresh group {} suite {} in scene {}",
                                quest.getSubQuestId(),
                                groupId,
                                suiteId,
                                sceneId);
                result = false;
            }
        }

        return result;
    }
}
