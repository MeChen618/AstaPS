package emu.grasscutter.data.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.enums.QuestExec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class QuestBinExecFallbackTest {
    private static final Gson GSON = new Gson();

    @AfterEach
    void cleanUp() {
        GameData.getQuestDataMap().remove(35107);
        GameData.getBeginCondQuestMap()
                .values()
                .forEach(quests -> quests.removeIf(quest -> quest.getSubId() == 35107));
        GameData.getBeginCondQuestMap().entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private static QuestData excel(String finishExecJson) {
        var quest =
                GSON.fromJson(
                        """
                        {
                          "subId": 35107,
                          "mainId": 351,
                          "order": 3,
                          "acceptCond": [],
                          "finishCond": [],
                          "failCond": [],
                          "beginExec": [],
                          "finishExec": %s,
                          "failExec": []
                        }
                        """
                                .formatted(finishExecJson),
                        QuestData.class);
        quest.onLoad();
        GameData.getQuestDataMap().put(quest.getSubId(), quest);
        return quest;
    }

    private static void loadBinFinishExec() {
        var main =
                GSON.fromJson(
                        """
                        {
                          "id": 351,
                          "subQuests": [
                            {
                              "subId": 35107,
                              "order": 3,
                              "finishExec": [
                                {
                                  "param": ["3", "133003429,1"],
                                  "type": "QUEST_EXEC_REFRESH_GROUP_SUITE"
                                }
                              ]
                            }
                          ]
                        }
                        """,
                        MainQuestData.class);
        main.onLoad();
    }

    @Test
    void missingExcelFinishExecFallsBackToBinOutput() {
        var quest = excel("[]");

        loadBinFinishExec();

        assertEquals(1, quest.getFinishExec().size());
        assertEquals(QuestExec.QUEST_EXEC_REFRESH_GROUP_SUITE, quest.getFinishExec().get(0).getType());
        assertArrayEquals(new String[] {"3", "133003429,1"}, quest.getFinishExec().get(0).getParam());
    }

    @Test
    void meaningfulExcelFinishExecIsNotOverwritten() {
        var quest =
                excel(
                        """
                        [
                          {
                            "param": ["3", "133003901,1"],
                            "type": "QUEST_EXEC_REFRESH_GROUP_SUITE"
                          }
                        ]
                        """);

        loadBinFinishExec();

        assertEquals(1, quest.getFinishExec().size());
        assertArrayEquals(new String[] {"3", "133003901,1"}, quest.getFinishExec().get(0).getParam());
    }
}
