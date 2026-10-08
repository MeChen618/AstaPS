package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.enums.QuestCond;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuestNativeChapterActivationTest {
    private static final Gson GSON = new Gson();

    @Test
    void chapter1001StartsFrom35202CompletionNotQuestZeroOrSceneEntry() {
        var quest = GSON.fromJson(
                """
                {
                  "subId": 36301, "mainId": 363, "order": 1,
                  "acceptCond": [{"type":"QUEST_COND_STATE_EQUAL","param":[0,3,0],"param_str":""}],
                  "finishCond":[], "failCond":[],
                  "beginExec":[], "finishExec":[], "failExec":[]
                }
                """, QuestData.class);
        var parent = GSON.fromJson(
                """
                {"id":363,"series":1001,"subQuests":[{"subId":36301,"order":1}]}
                """, MainQuestData.class);
        var nativeEntry = GSON.fromJson(
                """
                {"subId":36301, "order":1,
                 "acceptCond":[{"type":"QUEST_COND_STATE_EQUAL","param":[35202,3,0],"param_str":""}]}
                """, MainQuestData.SubQuestData.class);
        var oldKey = QuestData.questConditionKey(QuestCond.QUEST_COND_STATE_EQUAL, 0, "");
        var nativeKey = QuestData.questConditionKey(QuestCond.QUEST_COND_STATE_EQUAL, 35202, "");
        try {
            quest.onLoad();
            assertTrue(GameData.getBeginCondQuestMap().getOrDefault(oldKey, List.of()).contains(quest));
            quest.applyFrom(nativeEntry);
            GameData.getMainQuestDataMap().put(363, parent);
            GameData.getQuestDataMap().put(36301, quest);
            assertEquals(35202, quest.getAcceptCond().get(0).getParam()[0]);
            assertEquals(3, quest.getAcceptCond().get(0).getParam()[1]);
            assertFalse(GameData.getBeginCondQuestMap().getOrDefault(oldKey, List.of()).contains(quest));
            assertTrue(GameData.getBeginCondQuestMap().getOrDefault(nativeKey, List.of()).contains(quest));
            assertFalse(QuestManager.opensUnlinked(363));
        } finally {
            GameData.getMainQuestDataMap().remove(363);
            GameData.getQuestDataMap().remove(36301);
            for (var key : List.of(oldKey, nativeKey)) {
                var entries = GameData.getBeginCondQuestMap().get(key);
                if (entries != null) {
                    entries.remove(quest);
                    if (entries.isEmpty()) GameData.getBeginCondQuestMap().remove(key);
                }
            }
        }
    }
}
