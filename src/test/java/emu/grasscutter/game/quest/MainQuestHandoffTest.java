package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.utils.JsonUtils;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class MainQuestHandoffTest {
    @AfterEach
    void clearData() {
        GameData.getMainQuestDataMap().remove(352);
        GameData.getQuestDataMap().remove(35200);
        GameData.getBeginCondQuestMap()
                .values()
                .forEach(quests -> quests.removeIf(quest -> quest.getSubId() == 35200));
        GameData.getBeginCondQuestMap().entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private static void loadOpening(int predecessor, int state) {
        var quest =
                JsonUtils.decode(
                        """
                        {"subId":35200,"mainId":352,"order":1,
                         "acceptCond":[{"type":"QUEST_COND_STATE_EQUAL","param":[%d,%d]}],
                         "finishCond":[],"failCond":[],"beginExec":[],"finishExec":[],"failExec":[]}
                        """
                                .formatted(predecessor, state),
                        QuestData.class);
        quest.onLoad();
        GameData.getQuestDataMap().put(quest.getId(), quest);

        var main =
                JsonUtils.decode(
                        """
                        {"id":352,"subQuests":[{"subId":35200,"order":1,"isRewind":true}]}
                        """,
                        MainQuestData.class);
        GameData.getMainQuestDataMap().put(main.getId(), main);
        main.onLoad();
    }

    private static <T> T decodeSaved(String json, Class<T> type) {
        return new GsonBuilder()
                .setExclusionStrategies(
                        new ExclusionStrategy() {
                            @Override
                            public boolean shouldSkipField(FieldAttributes field) {
                                return field.getAnnotation(dev.morphia.annotations.Transient.class) != null;
                            }

                            @Override
                            public boolean shouldSkipClass(Class<?> ignored) {
                                return false;
                            }
                        })
                .create()
                .fromJson(json, type);
    }

    private static GameMainQuest savedParent(String state, boolean finished, String children) {
        return decodeSaved(
                """
                {"parentQuestId":352,"state":"%s","isFinished":%s,"childQuests":%s}
                """
                        .formatted(state, finished, children),
                GameMainQuest.class);
    }

    @Test
    void onlyQuestZeroFinishedIsAnUnlinkedOpening() {
        loadOpening(0, QuestState.QUEST_STATE_FINISHED.getValue());
        assertTrue(QuestManager.opensUnlinked(352));

        loadOpening(35102, QuestState.QUEST_STATE_FINISHED.getValue());
        assertFalse(QuestManager.opensUnlinked(352));

        loadOpening(0, QuestState.QUEST_STATE_UNSTARTED.getValue());
        assertFalse(QuestManager.opensUnlinked(352));
    }

    @Test
    void missingOrEmptyParentCanReceiveTheHandoff() {
        assertTrue(QuestManager.canResumeUnlinkedParent(null));

        var empty =
                savedParent(
                        "PARENT_QUEST_STATE_NONE",
                        false,
                        """
                        {"35200":{"state":"QUEST_STATE_UNSTARTED"},
                         "35201":{"state":"QUEST_STATE_UNSTARTED"}}
                        """);
        assertTrue(QuestManager.canResumeUnlinkedParent(empty));
    }

    @Test
    void realProgressPreventsForcedRestart() {
        for (String state :
                List.of(
                        "QUEST_STATE_UNFINISHED",
                        "QUEST_STATE_FINISHED",
                        "QUEST_STATE_FAILED")) {
            var parent =
                    savedParent(
                            "PARENT_QUEST_STATE_NONE",
                            false,
                            """
                            {"35200":{"state":"QUEST_STATE_UNSTARTED"},
                             "35201":{"state":"%s"}}
                            """
                                    .formatted(state));
            assertFalse(QuestManager.canResumeUnlinkedParent(parent), state);
        }
    }

    @Test
    void finishedParentCannotReceiveTheHandoffAgain() {
        var byFlag =
                savedParent(
                        "PARENT_QUEST_STATE_NONE",
                        true,
                        """
                        {"35200":{"state":"QUEST_STATE_UNSTARTED"}}
                        """);
        var byState =
                savedParent(
                        "PARENT_QUEST_STATE_FINISHED",
                        false,
                        """
                        {"35200":{"state":"QUEST_STATE_UNSTARTED"}}
                        """);

        assertFalse(QuestManager.canResumeUnlinkedParent(byFlag));
        assertFalse(QuestManager.canResumeUnlinkedParent(byState));
    }
}
