package emu.grasscutter.data.excels.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.binout.MainQuestData;
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
    void actualSubQuestMappingRestoresAmberTeachingSlimeAndTrialGrant() {
        // Reproduce the real resource loader sequence: QuestExcel rows first, BinOutput rows second.
        var slimeExcel = GSON.fromJson(
                "{\"subId\":35302,\"mainId\":353,\"beginExec\":[],\"finishExec\":[],\"failExec\":[]}",
                QuestData.class);
        var slimeNative = GSON.fromJson(
                "{\"subId\":35302,\"beginExec\":[{\"type\":\"QUEST_EXEC_REFRESH_GROUP_SUITE\","
                        + "\"param\":[\"3\",\"133003002,2\"]}]}",
                MainQuestData.SubQuestData.class);
        // Exercise the merge policy directly. applyFrom() also emits server startup
        // diagnostics, and must not bootstrap the full Grasscutter runtime in a unit test.
        var slimeActions = QuestData.effectiveExecList(
                slimeExcel.getBeginExec(), slimeNative.getBeginExec());

        assertEquals(1, slimeActions.size());
        var slimeExec = slimeActions.get(0);
        assertEquals("QUEST_EXEC_REFRESH_GROUP_SUITE", slimeExec.getType().name());
        assertEquals(List.of("3", "133003002,2"), List.of(slimeExec.getParam()));

        var amberExcel = GSON.fromJson(
                "{\"subId\":35301,\"mainId\":353,\"beginExec\":[],\"finishExec\":[],\"failExec\":[]}",
                QuestData.class);
        var amberNative = GSON.fromJson(
                "{\"subId\":35301,\"finishExec\":[{\"type\":\"QUEST_EXEC_GRANT_TRIAL_AVATAR\","
                        + "\"param\":[\"1\"]}]}",
                MainQuestData.SubQuestData.class);
        var amberActions = QuestData.effectiveExecList(
                amberExcel.getFinishExec(), amberNative.getFinishExec());

        assertEquals("QUEST_EXEC_GRANT_TRIAL_AVATAR", amberActions.get(0).getType().name());
        assertEquals(List.of("1"), List.of(amberActions.get(0).getParam()));
    }

    @Test
    void nonemptyExcelActionsStayAuthoritative() {
        var excel = exec("QUEST_EXEC_GRANT_TRIAL_AVATAR");
        var bin = exec("QUEST_EXEC_REFRESH_GROUP_SUITE");
        assertEquals(List.of(excel), QuestData.effectiveExecList(List.of(excel), List.of(bin)));
    }

    @Test
    void parsesActualNative353QuestActionsAndPreservesSlimeSuite() {
        // Mirrors BinOutput/Quest/353.json: Amber dialogue, trial grant, then skill tutorial slime.
        String nativeSubquests =
                """
                {"id":353,"subQuests":[
                    {"subId":35301,
                     "beginExec":[{"type":"QUEST_EXEC_REFRESH_GROUP_SUITE",
                                   "param":["3","133003002,1"]}],
                     "finishExec":[{"type":"QUEST_EXEC_GRANT_TRIAL_AVATAR","param":["1"]}]},
                    {"subId":35302,
                     "beginExec":[{"type":"QUEST_EXEC_REFRESH_GROUP_SUITE",
                                   "param":["3","133003002,2"]}]}
                ]}
                """;
        var nativeMain =
                GSON.fromJson(
                        nativeSubquests, emu.grasscutter.data.binout.MainQuestData.class);
        var subs = nativeMain.getSubQuests();
        assertEquals(35301, subs[0].getSubId());
        assertEquals(
                "QUEST_EXEC_GRANT_TRIAL_AVATAR",
                subs[0].getFinishExec().get(0).getType().name());
        assertEquals(35302, subs[1].getSubId());

        var recoveredBegin = QuestData.effectiveExecList(List.of(), subs[1].getBeginExec());
        assertEquals(1, recoveredBegin.size());
        assertEquals("QUEST_EXEC_REFRESH_GROUP_SUITE", recoveredBegin.get(0).getType().name());
        assertEquals("133003002,2", recoveredBegin.get(0).getParam()[1]);
    }

    @Test
    void invalidOrAbsentActionsAreIgnored() {
        var unknown = exec("NOT_AN_EXEC_TYPE");
        assertTrue(QuestData.effectiveExecList(List.of(), List.of(unknown)).isEmpty());
        assertTrue(QuestData.effectiveExecList(null, null).isEmpty());
    }
}
