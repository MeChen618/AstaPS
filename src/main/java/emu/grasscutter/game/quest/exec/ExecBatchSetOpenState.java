package emu.grasscutter.game.quest.exec;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

/**
 * QUEST_EXEC_BATCH_SET_OPEN_STATE
 *
 * <p>7.2 quest data stores this as two comma separated lists of equal length:
 *
 * <pre>param[0] = "7010,7026,7060"   (openState ids)
 * param[1] = "0,0,0"                (values)</pre>
 *
 * <p>Pairs are applied through the same path as QUEST_EXEC_SET_OPEN_STATE
 * ({@code PlayerProgressManager.forceSetOpenState}) so behaviour stays identical.
 */
@QuestValueExec(QuestExec.QUEST_EXEC_BATCH_SET_OPEN_STATE)
public class ExecBatchSetOpenState extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestData.QuestExecParam condition, String... paramStr) {
        if (paramStr == null || paramStr.length < 2) {
            Grasscutter.getLogger()
                    .debug("[QuestExec] BATCH_SET_OPEN_STATE needs 2 params, got {}",
                            paramStr == null ? 0 : paramStr.length);
            return false;
        }

        var ids = paramStr[0].split(",");
        var values = paramStr[1].split(",");
        var count = Math.min(ids.length, values.length);
        var progressManager = quest.getOwner().getProgressManager();
        if (progressManager == null) return false;

        var applied = 0;
        for (var i = 0; i < count; i++) {
            try {
                var openState = Integer.parseInt(ids[i].trim());
                var value = Integer.parseInt(values[i].trim());
                progressManager.forceSetOpenState(openState, value);
                applied++;
            } catch (NumberFormatException e) {
                Grasscutter.getLogger()
                        .warn(
                                "[QuestExec] BATCH_SET_OPEN_STATE bad pair at index {}: {} / {}",
                                i,
                                ids[i],
                                values[i]);
            }
        }

        if (applied != count) {
            // Report a partial failure instead of pretending everything was applied.
            return false;
        }
        return applied > 0;
    }
}
