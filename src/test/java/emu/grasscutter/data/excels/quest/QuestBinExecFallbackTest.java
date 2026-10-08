package emu.grasscutter.data.excels.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuestBinExecFallbackTest {
    private static final Gson GSON = new Gson();

    private static QuestData.QuestExecParam exec(String type) {
        return GSON.fromJson(
                "{\"type\":\"" + type + "\",\"param\":[\"3\",\"133003002,2\"]}",
                QuestData.QuestExecParam.class);
    }

    @Test
    void missingExcelActionsUseNativeSlimeSuiteRefresh() {
        var bin = exec("QUEST_EXEC_REFRESH_GROUP_SUITE");
        var result = QuestData.effectiveExecList(List.of(), List.of(bin));
        assertEquals(1, result.size());
        assertEquals("133003002,2", result.get(0).getParam()[1]);
    }

    @Test
    void nonemptyExcelActionsStayAuthoritative() {
        var excel = exec("QUEST_EXEC_GRANT_TRIAL_AVATAR");
        var bin = exec("QUEST_EXEC_REFRESH_GROUP_SUITE");
        assertEquals(List.of(excel), QuestData.effectiveExecList(List.of(excel), List.of(bin)));
    }

    @Test
    void invalidOrAbsentActionsAreIgnored() {
        var unknown = exec("NOT_AN_EXEC_TYPE");
        assertTrue(QuestData.effectiveExecList(List.of(), List.of(unknown)).isEmpty());
        assertTrue(QuestData.effectiveExecList(null, null).isEmpty());
    }
}
