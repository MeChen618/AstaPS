package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import lombok.val;

@QuestValueExec(QuestExec.QUEST_EXEC_REFRESH_GROUP_SUITE)
public class ExecRefreshGroupSuite extends QuestExecHandler {
    private static final int MAX_SCRIPT_INIT_RETRIES = 100;

    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        return executeWhenReady(quest, paramStr, 0);
    }

    private boolean executeWhenReady(GameQuest quest, String[] paramStr, int attempt) {
        if (paramStr.length < 2) {
            Grasscutter.getLogger().warn(
                    "Quest {} refresh-group-suite exec has invalid params {}",
                    quest.getSubQuestId(),
                    java.util.Arrays.toString(paramStr));
            return false;
        }

        val sceneId = Integer.parseInt(paramStr[0]);
        val scene = quest.getOwner().getWorld().getSceneById(sceneId);
        if (scene == null) {
            Grasscutter.getLogger().warn(
                    "Quest {} could not refresh group suite: scene {} is unavailable",
                    quest.getSubQuestId(),
                    sceneId);
            return false;
        }

        val scriptManager = scene.getScriptManager();
        if (!scriptManager.isInit()) {
            if (!scriptManager.isInitAttempted() && attempt < MAX_SCRIPT_INIT_RETRIES) {
                if (attempt == 0) {
                    Grasscutter.getLogger().debug(
                            "Quest {} deferring group-suite refresh in scene {} until scripts initialize",
                            quest.getSubQuestId(),
                            sceneId);
                }
                scene.getScheduler()
                        .scheduleDelayedTask(
                                () -> executeWhenReady(quest, paramStr, attempt + 1), 1);
                return true;
            }

            Grasscutter.getLogger().warn(
                    "Quest {} could not refresh group suite in scene {}: scripts failed to initialize after {} attempt(s)",
                    quest.getSubQuestId(),
                    sceneId,
                    attempt);
            return false;
        }

        val entries = paramStr[1].split(";");
        boolean result = true;
        for (var entry : entries) {
            val entryArray = entry.split(",");
            if (entryArray.length < 2) {
                Grasscutter.getLogger().warn(
                        "Quest {} refresh-group-suite exec has invalid entry {}",
                        quest.getSubQuestId(),
                        entry);
                result = false;
                continue;
            }

            val groupId = Integer.parseInt(entryArray[0]);
            val suiteId = Integer.parseInt(entryArray[1]);
            val group = scriptManager.getGroupById(groupId);
            if (group == null) {
                Grasscutter.getLogger().warn(
                        "Quest {} could not refresh group {} suite {} in scene {}: group is unavailable",
                        quest.getSubQuestId(),
                        groupId,
                        suiteId,
                        sceneId);
                result = false;
                continue;
            }

            // Quest-owned groups must survive the normal visibility unload pass. Mark this before
            // switching suites so a concurrently ticking scene cannot immediately discard the quest
            // entities that are about to be spawned.
            group.dontUnload = true;

            boolean applied = scriptManager.refreshGroupSuite(groupId, suiteId, quest);
            // 35302's Suite 2 contains the combat-training slime (config 439). Report the
            // actual world entity too: an active suite alone does not prove that it spawned.
            if (groupId == 133003002 && suiteId == 2) {
                var slime = scene.getEntityByConfigId(439, groupId);
                Grasscutter.getLogger()
                        .info(
                                "[quest-slime] uid={} sub={} group={} suite={} applied={} spawned={} entityId={}",
                                quest.getOwner().getUid(),
                                quest.getSubQuestId(),
                                groupId, suiteId, applied, slime != null,
                                slime != null ? slime.getId() : 0);
            }
            if (quest.getMainQuestId() >= 351 && quest.getMainQuestId() <= 353) {
                var instance = scriptManager.getGroupInstanceById(groupId);
                Grasscutter.getLogger()
                        .info(
                                "[quest-group] uid={} main={} sub={} scene={} group={} suite={} applied={} activeSuite={}",
                                quest.getOwner().getUid(),
                                quest.getMainQuestId(),
                                quest.getSubQuestId(),
                                sceneId, groupId, suiteId, applied,
                                instance != null ? instance.getActiveSuiteId() : 0);
            }
            if (!applied) {
                Grasscutter.getLogger().warn(
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
