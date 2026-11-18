package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.server.packet.send.PacketDailyTaskProgressNotify;

/**
 * QUEST_EXEC_NOTIFY_DAILY_TASK
 *
 * <p>param[0] = daily task id (e.g. "20500"). The quest only asks the server to push the
 * commission's current state to the client, so this sends the very same progress notify that
 * {@code DailyTaskManager} already uses internally.
 *
 * <p>It deliberately does <b>not</b> touch progress: changing state here would double count.
 */
@QuestValueExec(QuestExec.QUEST_EXEC_NOTIFY_DAILY_TASK)
public class ExecNotifyDailyTask extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        if (paramStr == null || paramStr.length < 1) return false;

        final int dailyTaskId;
        try {
            dailyTaskId = Integer.parseInt(paramStr[0].trim());
        } catch (NumberFormatException e) {
            Grasscutter.getLogger()
                    .warn("[QuestExec] NOTIFY_DAILY_TASK bad task id: {}", paramStr[0]);
            return false;
        }

        var player = quest.getOwner();
        var manager = player.getDailyTaskManager();
        if (manager == null) return false;

        var task = manager.getDailyTask(dailyTaskId);
        if (task == null) {
            Grasscutter.getLogger()
                    .debug(
                            "[QuestExec] NOTIFY_DAILY_TASK: task {} is not active for uid {}",
                            dailyTaskId,
                            player.getUid());
            return false;
        }

        player.sendPacket(new PacketDailyTaskProgressNotify(task));
        return true;
    }
}
