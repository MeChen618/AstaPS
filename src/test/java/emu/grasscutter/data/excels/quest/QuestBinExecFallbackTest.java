package emu.grasscutter.data.excels.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.binout.MainQuestData;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
    void bundled7_1BaselineRestoresMissingSlimeAndAmberActions() throws Exception {
        var stream =
                QuestBinExecFallbackTest.class.getResourceAsStream(
                        "/defaults/data/quest-7.1-intro-exec-baseline.json");
        assertNotNull(stream);
        List<QuestData.QuestExecFallbackRow> rows;
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            rows =
                    GSON.fromJson(
                            reader,
                            new TypeToken<List<QuestData.QuestExecFallbackRow>>() {}.getType());
        }

        var slime = rows.stream().filter(row -> row.getSubId() == 35302).findFirst().orElseThrow();
        var emptyQuest = GSON.fromJson(
                "{\"subId\":35302,\"beginExec\":[],\"finishExec\":[],\"failExec\":[]}",
                QuestData.class);
        assertEquals(1, emptyQuest.fillMissingExecutions(slime));
        assertEquals(
                "133003002,2",
                emptyQuest.getBeginExec().get(0).getParam()[1]);
        assertEquals(0, emptyQuest.fillMissingExecutions(slime));

        var amber = rows.stream().filter(row -> row.getSubId() == 35301).findFirst().orElseThrow();
        assertEquals(
                "QUEST_EXEC_GRANT_TRIAL_AVATAR",
                amber.getFinishExec().get(0).getType().name());
    }

    @Test
    void missingExcelActionsUseNativeSlimeSuiteRefresh() {
        var bin = exec("QUEST_EXEC_REFRESH_GROUP_SUITE");
        var result = QuestData.effectiveExecList(List.of(), List.of(bin));
        assertEquals(1, result.size());
        assertEquals("133003002,2", result.get(0).getParam()[1]);
    }

    @Test
    void actualSubQuestMappingRestoresAmberTeachingSlimeAndTrialGrant() {
        // Reproduce the real resource loader sequence: QuestExcel rows first, BinOutput rows second.
        var slimeExcel = GSON.fromJson(
                "{\"subId\":35302,\"mainId\":353,\"beginExec\":[],\"finishExec\":[],\"failExec\":[]}",
                QuestData.class);
        var slimeNative = GSON.fromJson(
                "{\"subId\":35302,\"beginExec\":[{\"type\":\"QUEST_EXEC_REFRESH_GROUP_SUITE\","
                        + "\"param\":[\"3\",\"133003002,2\"]}]}",
                MainQuestData.SubQuestData.class);
        slimeExcel.applyFrom(slimeNative);

        assertEquals(1, slimeExcel.getBeginExec().size());
        var slimeExec = slimeExcel.getBeginExec().get(0);
        assertEquals("QUEST_EXEC_REFRESH_GROUP_SUITE", slimeExec.getType().name());
        assertEquals(List.of("3", "133003002,2"), List.of(slimeExec.getParam()));

        var amberExcel = GSON.fromJson(
                "{\"subId\":35301,\"mainId\":353,\"beginExec\":[],\"finishExec\":[],\"failExec\":[]}",
                QuestData.class);
        var amberNative = GSON.fromJson(
                "{\"subId\":35301,\"finishExec\":[{\"type\":\"QUEST_EXEC_GRANT_TRIAL_AVATAR\","
                        + "\"param\":[\"1\"]}]}",
                MainQuestData.SubQuestData.class);
        amberExcel.applyFrom(amberNative);

        assertEquals("QUEST_EXEC_GRANT_TRIAL_AVATAR", amberExcel.getFinishExec().get(0).getType().name());
        assertEquals(List.of("1"), List.of(amberExcel.getFinishExec().get(0).getParam()));
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
