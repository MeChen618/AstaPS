package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.*;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestContent;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import org.junit.jupiter.api.Test;

import java.util.*;

/** Quest completion/failure events must match their own scene or monster group. */
final class QuestContentEventSourceTest {
    @Test
    void enteringUnrelatedSceneCannotSatisfySceneThreeQuest() {
        assertTrue(ContentEnterMyWorld.matchesScene(3, 3, 3));
        assertFalse(ContentEnterMyWorld.matchesScene(3, 2001, 2001));
        assertFalse(ContentEnterMyWorld.matchesScene(3, 3, 2001));
        assertFalse(ContentEnterMyWorld.matchesScene(2001, 3, 3));
        assertFalse(ContentEnterMyWorld.matchesScene(0, 0, 0));
    }

    @Test
    void malformedSceneEventDoesNotThrowOrFinish() {
        var condition = new QuestData.QuestContentCondition();
        condition.setParam(new int[] {3, 0});
        assertFalse(new ContentEnterMyWorld().execute(null, condition, ""));
    }

    @Test
    void otherGroupKillCannotClearThisQuestGroup() {
        // Act III 39703 and 38802 use two different group clear objectives.
        assertTrue(ContentClearGroupMonster.matchesGroupEvent(133002233, 133002233));
        assertTrue(ContentClearGroupMonster.matchesGroupEvent(133004015, 133004015));
        assertFalse(ContentClearGroupMonster.matchesGroupEvent(133002233, 133004015));
        assertFalse(ContentClearGroupMonster.matchesGroupEvent(133004015, 133002233));
        assertFalse(ContentClearGroupMonster.matchesGroupEvent(133002233));
        assertFalse(ContentClearGroupMonster.matchesGroupEvent(0, 0));
    }

    @Test
    void unrelatedGroupEventDoesNotNeedSceneManager() {
        var condition = new QuestData.QuestContentCondition();
        condition.setParam(new int[] {133002233, 0});
        assertFalse(new ContentClearGroupMonster().execute(null, condition, "", 133004015));
        assertFalse(new ContentClearGroupMonster().execute(null, condition, ""));
    }

    @Test
    void gadgetStateConditionMatchesGroupConfigAndNewState() {
        var condition = new QuestData.QuestContentCondition();
        condition.setParam(new int[] {133707072, 72010});
        condition.setParamStr("201");
        var handler = new ContentGadgetStateChange();

        assertTrue(handler.execute(null, condition, "", 133707072, 72010, 201));
        assertFalse(handler.execute(null, condition, "", 133707002, 72010, 201));
        assertFalse(handler.execute(null, condition, "", 133707072, 72011, 201));
        assertFalse(handler.execute(null, condition, "", 133707072, 72010, 0));
        assertFalse(handler.execute(null, condition, "", 133707072, 72010));
    }

    @Test
    void unrelatedInventoryEventsDoNotEvaluateItemConditions() {
        var condition = new QuestData.QuestContentCondition();
        condition.setParam(new int[] {100305, 1});

        assertFalse(new ContentObtainItem().execute(null, condition, "", 100313, 1));
        assertFalse(new ContentItemLessThan().execute(null, condition, "", 100313, 1));
        assertFalse(new ContentObtainItem().execute(null, condition, ""));
        assertFalse(new ContentItemLessThan().execute(null, condition, ""));
    }

    @Test
    void nativeEnumAliasesHaveRealRegisteredHandlers() {
        var handlers = new Int2ObjectOpenHashMap<BaseContent>();
        var system = new QuestSystem(null);
        system.registerHandler(handlers, ContentUnlockTransPoint.class);
        system.registerHandler(handlers, ContentQuestVarEqual.class);
        system.registerHandler(handlers, ContentEnterVehicle.class);
        assertInstanceOf(ContentUnlockTransPoint.class, handlers.get(167));
        assertInstanceOf(ContentQuestVarEqual.class, handlers.get(171));
        assertInstanceOf(ContentEnterVehicle.class, handlers.get(182));
    }

    @Test
    void specificWidgetConditionChecksTheOwnedSlotOnAttachAndResume() {
        var gson = new Gson();
        var data =
                gson.fromJson(
                        """
                        {"subId":701129,"mainId":7011,"failCond":[],
                         "finishCond":[{"type":"QUEST_CONTENT_SPECIFIC_WIDGET_ATTACHED","param":[220133,0]}]}
                        """,
                        QuestData.class);
        var widgetData = gson.fromJson(
                """
                {"id":220133,"itemType":"ITEM_MATERIAL","materialType":"MATERIAL_WIDGET","stackLimit":1}
                """,
                ItemData.class);
        var item = new GameItem(widgetData, 1);
        var player =
                new Player() {
                    private final Inventory testInventory = new Inventory(this) {
                        @Override
                        public GameItem getItemById(int itemId) {
                            return item.getItemId() == itemId ? item : null;
                        }
                    };

                    @Override
                    public Inventory getInventory() {
                        return testInventory;
                    }
                };
        var quest = new GameQuest(null, data) {
            @Override
            public Player getOwner() {
                return player;
            }
        };
        var condition = data.getFinishCond().getFirst();
        var system = new QuestSystem(null);
        assertEquals(QUEST_CONTENT_SPECIFIC_WIDGET_ATTACHED, condition.getType());
        assertEquals(QUEST_CONTENT_NONE, QuestContent.getContentTriggerByValue(
                QUEST_CONTENT_SPECIFIC_WIDGET_ATTACHED.getValue()));
        assertTrue(ContentCurrentState.checkOnStart(condition.getType()));

        assertFalse(system.triggerContent(quest, condition, "", 220133));
        player.setWidgetId(220133);
        assertTrue(system.triggerContent(quest, condition, "", 220133));
        assertTrue(system.triggerContent(quest, condition, ""));
        assertFalse(system.triggerContent(quest, condition, "", 220132));
        item.setCount(0);
        assertFalse(system.triggerContent(quest, condition, ""));
    }

    @Test
    void anyPointMeansOneOfTheNamedPointsInTheRequestedScene() {
        var player = new Player();
        player.getUnlockedScenePoints(3).add(815);
        var data =
                new Gson()
                        .fromJson(
                                "{\"subId\":7466802,\"mainId\":74668,\"finishCond\":[],\"failCond\":[]}",
                                QuestData.class);
        var quest =
                new GameQuest(null, data) {
                    @Override
                    public Player getOwner() {
                        return player;
                    }
                };
        var condition = new QuestData.QuestContentCondition();
        condition.setType(QUEST_CONTENT_UNLOCK_ANY_TRANS_POINT);
        condition.setParam(new int[] {3, 0});
        condition.setParamStr("814,815,922,923,1121");
        var handler = new ContentUnlockTransPoint();
        assertTrue(handler.execute(quest, condition, "", 3, 815));
        assertTrue(handler.execute(quest, condition, ""));
        assertFalse(handler.execute(null, condition, "", 4, 815));
        player.getUnlockedScenePoints(3).clear();
        player.getUnlockedScenePoints(3).add(9000);
        assertFalse(handler.execute(quest, condition, "", 3, 9000));
        condition.setParamStr("814,broken");
        assertFalse(handler.execute(quest, condition, ""));
    }

    @Test
    void variableConditionsUseTheirParentAndLiveValueRatherThanAnOldPayload() {
        var gson = new Gson();
        var parent =
                new GameMainQuest() {
                    private final int[] vars = new int[] {2, 0, 0, 0, 0};

                    @Override
                    public int getParentQuestId() {
                        return 800;
                    }

                    @Override
                    public int[] getQuestVars() {
                        return vars;
                    }
                };
        var data =
                gson.fromJson(
                        "{\"subId\":80062,\"mainId\":800,\"finishCond\":[],\"failCond\":[]}",
                        QuestData.class);
        var quest = new GameQuest(parent, data);
        var condition = new QuestData.QuestContentCondition();
        condition.setType(QUEST_CONTENT_QUEST_VAR_NOT_EQUAL);
        condition.setParam(new int[] {0, 1});
        var handler = new ContentQuestVarEqual();
        assertTrue(handler.execute(quest, condition, "", 0, 1, 800));
        assertFalse(handler.execute(quest, condition, "", 0, 2, 801));
        assertFalse(handler.execute(null, condition, "", 1, 2, 800));
        parent.getQuestVars()[0] = 1;
        assertFalse(handler.execute(quest, condition, "", 0, 2, 800));
        condition.setParam(new int[] {99, 1});
        assertFalse(handler.execute(quest, condition, "", 99, 2, 800));
        condition.setParam(new int[] {-1, 1});
        assertFalse(handler.execute(null, condition, ""));
    }

    @Test
    void leavingAnyVehicleStillRequiresARealVehicleEvent() {
        var condition = new QuestData.QuestContentCondition();
        condition.setType(QUEST_CONTENT_LEAVE_VEHICLE);
        condition.setParam(new int[] {0, 0});
        var handler = new ContentEnterVehicle();
        assertTrue(handler.execute(null, condition, "", 45004105));
        assertFalse(handler.execute(null, condition, ""));
        assertFalse(handler.execute(null, condition, "", 0));
        condition.setParam(new int[] {45004105, 0});
        assertFalse(handler.execute(null, condition, "", 45003053));
        assertTrue(handler.execute(null, condition, "", 45004105));
    }
}
