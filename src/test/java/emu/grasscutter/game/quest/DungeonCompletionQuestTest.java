package emu.grasscutter.game.quest;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_FINISH_DUNGEON;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.LogicType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class DungeonCompletionQuestTest {
    private static final int FIRST_DUNGEON = 1001;
    private static final int SECOND_DUNGEON = 1003;

    @Test
    void checksSavedClearsWhenStartingOrResumingAQuest() {
        var player = new Player();
        var events = new QuestEventRecorder(player);
        var quest = dungeonQuest(player);
        var dungeonConditions = quest.getQuestData().getFinishCond();
        var system = new QuestSystem(null);
        player.getPlayerProgress().getCompletedDungeons().add(FIRST_DUNGEON);

        events.manager().checkQuestAlreadyFulfilled(quest);
        assertTrue(events.types().contains(QUEST_CONTENT_FINISH_DUNGEON));
        assertTrue(system.triggerContent(quest, dungeonConditions.getFirst(), ""));
        assertFalse(system.triggerContent(quest, dungeonConditions.getLast(), ""));
    }

    @Test
    void requiresTheMatchingDungeonClearForEachPartOfACombinedObjective() {
        var player = new Player();
        var quest = dungeonQuest(player);
        var conditions = quest.getQuestData().getFinishCond();
        var system = new QuestSystem(null);
        var completed = player.getPlayerProgress().getCompletedDungeons();
        var progress = quest.getFinishProgressList();

        assertFalse(system.triggerContent(quest, conditions.getFirst(), "", FIRST_DUNGEON));
        completed.add(FIRST_DUNGEON);
        assertFalse(system.triggerContent(quest, conditions.getFirst(), "", SECOND_DUNGEON));
        assertTrue(QuestProgress.recordMatch(
                progress, 0, system.triggerContent(quest, conditions.getFirst(), "", FIRST_DUNGEON)));
        assertFalse(LogicType.calculate(quest.getQuestData().getFinishCondComb(), progress));

        completed.add(SECOND_DUNGEON);
        assertTrue(QuestProgress.recordMatch(
                progress, 1, system.triggerContent(quest, conditions.getLast(), "", SECOND_DUNGEON)));
        assertTrue(LogicType.calculate(quest.getQuestData().getFinishCondComb(), progress));

        var invalid = new QuestData.QuestContentCondition();
        invalid.setType(QUEST_CONTENT_FINISH_DUNGEON);
        for (int[] params : List.of(new int[0], new int[] {0}, new int[] {-1, 0})) {
            invalid.setParam(params);
            assertFalse(system.triggerContent(quest, invalid, ""));
        }
        invalid.setParam(null);
        assertFalse(system.triggerContent(quest, invalid, ""));
    }

    private static GameQuest dungeonQuest(Player player) {
        var data = new Gson().fromJson(
                """
                {
                  "subId": 30901,
                  "mainId": 309,
                  "finishCondComb": "LOGIC_AND",
                  "finishCond": [
                    {"type": "QUEST_CONTENT_FINISH_DUNGEON", "param": [1001, 0]},
                    {"type": "QUEST_CONTENT_FINISH_DUNGEON", "param": [1003, 0]}
                  ],
                  "failCond": []
                }
                """, QuestData.class);
        return new GameQuest(null, data) {
            @Override
            public Player getOwner() {
                return player;
            }
        };
    }
}
