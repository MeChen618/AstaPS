package emu.grasscutter.game.quest.content;

import static emu.grasscutter.game.quest.enums.QuestContent.*;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScriptSceneData;
import emu.grasscutter.data.excels.CookRecipeData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.city.CityInfoData;
import emu.grasscutter.game.managers.cooking.CookingManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.world.TestWorld;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class ContentCurrentStateTest {
    @Test
    void registersEveryStateAliasWithItsClientEnumValue() {
        var handlers = new Int2ObjectOpenHashMap<BaseContent>();
        new QuestSystem(null).registerHandler(handlers, ContentCurrentState.class);
        for (int id : new int[] {109, 150, 160, 161, 162, 168, 169, 170, 172, 177, 186}) {
            assertInstanceOf(ContentCurrentState.class, handlers.get(id));
        }
    }

    @Test
    void cityLevelGateRechecksTheCorrectSavedStatueLevel() {
        var player = new Player();
        var data = new Gson().fromJson(
                "{\"subId\":46902,\"mainId\":469,\"finishCond\":[],\"failCond\":[]}",
                QuestData.class);
        var quest = new GameQuest(null, data) {
            @Override
            public Player getOwner() {
                return player;
            }
        };
        var condition = new QuestData.QuestContentCondition();
        condition.setType(QUEST_CONTENT_CITY_LEVEL_UP);
        condition.setParam(new int[] {1, 2});
        var handler = new ContentCurrentState();

        player.setCityInfoData(null);
        assertFalse(handler.execute(quest, condition, "", 1));
        assertNull(player.getCityInfoData());

        var otherCity = new CityInfoData(2);
        otherCity.setLevel(10);
        player.setCityInfoData(Map.of(2, otherCity));
        assertFalse(handler.execute(quest, condition, "", 1));

        var city = new CityInfoData(1);
        player.setCityInfoData(Map.of(1, city, 2, otherCity));
        assertFalse(handler.execute(quest, condition, "", 1));
        city.setLevel(2);
        assertTrue(handler.execute(quest, condition, "", 1));
        assertFalse(handler.execute(quest, condition, "", 2));
        city.setLevel(4);
        assertTrue(handler.execute(quest, condition, "", 1));
        assertTrue(handler.execute(quest, condition, ""));
        assertTrue(ContentCurrentState.checkOnStart(QUEST_CONTENT_CITY_LEVEL_UP));

        condition.setParam(new int[] {1, 0});
        assertFalse(handler.execute(quest, condition, "", 1));
        condition.setParam(new int[] {1});
        assertFalse(handler.execute(quest, condition, "", 1));
    }

    @Test
    void rechecksLiveGlobalValueAndRejectsAnotherVariablesEvent() {
        var player = new Player();
        var manager = new QuestManager(player);
        Player owner =
                new Player() {
                    @Override
                    public QuestManager getQuestManager() {
                        return manager;
                    }
                };
        var data =
                new Gson()
                        .fromJson(
                                "{\"subId\":7710407,\"mainId\":77104,\"finishCond\":[],\"failCond\":[]}",
                                QuestData.class);
        var quest =
                new GameQuest(null, data) {
                    @Override
                    public Player getOwner() {
                        return owner;
                    }
                };
        var condition = new QuestData.QuestContentCondition();
        condition.setType(QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL);
        condition.setParam(new int[] {72, 2});
        var handler = new ContentCurrentState();
        player.getQuestGlobalVariables().put(72, 2);
        assertTrue(handler.execute(quest, condition, "", 72, 999));
        assertFalse(handler.execute(quest, condition, "", 73, 2));
        player.getQuestGlobalVariables().put(72, 1);
        assertFalse(handler.execute(quest, condition, "", 72, 2));
        assertFalse(ContentCurrentState.compare(QUEST_CONTENT_ITEM_NUM_GREATER, 2, 2));
        assertTrue(ContentCurrentState.compare(QUEST_CONTENT_ITEM_NUM_GREATER, 3, 2));
    }

    @Test
    void rangeUsesTheConditionPointIncludingOptionalRotation() {
        // 7710244 的条件中心与导航中心不同。
        var point =
                ContentCurrentState.parsePoint(
                        "[3_14622:8769.230,87.200,8503.074,0.000,299.816,0.000]", 3);
        assertNotNull(point);
        assertEquals(8769.230f, point.getX(), 0.001f);
        assertNull(ContentCurrentState.parsePoint("[4_14622:1,2,3]", 3));
        assertNull(ContentCurrentState.parsePoint("[3_14622:NaN,2,3]", 3));
        assertNull(ContentCurrentState.parsePoint("[3_14622:1,2]", 3));
    }

    @Test
    void namedRangeChecksTheLoadedScenePointAndTheConfiguredRadius() {
        var gson = new Gson();
        var scripts = GameData.getScriptSceneDataMap();
        String key = "flat.luas.scenes.full_globals.lua.json";
        var previousCombat = GameData.getConfigGlobalCombat();
        var gameConfig = Grasscutter.getConfig().server.game;
        boolean scriptsEnabled = gameConfig.enableScriptInBigWorld;
        var previous = scripts.put(key, gson.fromJson("""
                {"scriptObjectList":{
                    "3/scene3_dummy_points.lua":{"dummy_points":{
                        "QuestGate.pos":[10,20,30],
                        "QuestGate.rot":[0,180,0],
                        "Incomplete.pos":[1,2],
                        "MissingCoordinate.pos":[1,null,3]
                    }},
                    "4/scene4_dummy_points.lua":{"dummy_points":{
                        "OtherSceneOnly.pos":[10,20,30]
                    }}
                }}
                """, ScriptSceneData.class));
        try {
            gameConfig.enableScriptInBigWorld = false;
            var player = new Player();
            player.setSceneLoadState(Player.SceneLoadState.LOADED);
            var world = TestWorld.create(player);
            var matchingScene = new TestWorld.TestScene(world, 3, SceneType.SCENE_WORLD);
            player.setScene(matchingScene);
            var data = gson.fromJson(
                    "{\"mainId\":10101,\"subId\":1010101,\"finishCond\":[],\"failCond\":[]}",
                    QuestData.class);
            var quest = new GameQuest(null, data) {
                @Override
                public Player getOwner() {
                    return player;
                }
            };
            var condition = new QuestData.QuestContentCondition();
            condition.setType(QUEST_CONTENT_LEAVE_SCENE_RANGE);
            condition.setParam(new int[] {3, 5});
            condition.setParamStr(" QuestGate ");
            var handler = new ContentCurrentState();

            player.getPosition().set(13, 24, 30);
            assertFalse(handler.execute(quest, condition, "", 3));
            player.getPosition().set(16, 20, 30);
            assertTrue(handler.execute(quest, condition, "", 3));
            assertFalse(handler.execute(quest, condition, "", 4));

            player.setScene(new TestWorld.TestScene(world, 4, SceneType.SCENE_WORLD));
            assertFalse(handler.execute(quest, condition, "", 3));
            player.setScene(matchingScene);
            for (String name : new String[] {
                    "MissingPoint", "OtherSceneOnly", "Incomplete", "MissingCoordinate"
            }) {
                condition.setParamStr(name);
                assertFalse(handler.execute(quest, condition, "", 3), name);
            }
        } finally {
            gameConfig.enableScriptInBigWorld = scriptsEnabled;
            GameData.setConfigGlobalCombat(previousCombat);
            if (previous == null) scripts.remove(key);
            else scripts.put(key, previous);
        }
    }

    @Test
    void malformedAndUnrelatedConditionsDoNotReadPlayerState() {
        var condition = new QuestData.QuestContentCondition();
        condition.setType(QUEST_CONTENT_ITEM_NUM_EQUAL);
        condition.setParam(new int[] {2030, 1});
        var handler = new ContentCurrentState();
        assertFalse(handler.execute(null, condition, "", 2031));
        condition.setParam(new int[0]);
        assertFalse(handler.execute(null, condition, ""));
    }

    @Test
    void recipeAlternativesAcceptOneKnownRecipeAndRecheckTheCurrentUnlocks() {
        var gson = new Gson();
        var old = new java.util.HashMap<Integer, CookRecipeData>();
        for (int id : new int[] {3107, 2105, 2102}) {
            old.put(
                    id,
                    GameData.getCookRecipeDataMap()
                            .put(id, gson.fromJson("{\"id\":" + id + "}", CookRecipeData.class)));
        }
        try {
            var player =
                    new Player() {
                        private final CookingManager cooking = new CookingManager(this);

                        @Override
                        public CookingManager getCookingManager() {
                            return cooking;
                        }
                    };
            var data =
                    gson.fromJson(
                            "{\"subId\":4001503,\"mainId\":40015,\"finishCond\":[],\"failCond\":[]}",
                            QuestData.class);
            var quest =
                    new GameQuest(null, data) {
                        @Override
                        public Player getOwner() {
                            return player;
                        }
                    };
            var condition = new QuestData.QuestContentCondition();
            condition.setType(QUEST_CONTENT_UNLOCKED_RECIPE);
            condition.setParamStr("3107,2105,2102");
            var handler = new ContentCurrentState();
            assertFalse(handler.execute(quest, condition, "", 3107));
            player.getUnlockedRecipies().put(3107, 0);
            assertTrue(handler.execute(quest, condition, "", 3107));
            player.getUnlockedRecipies().put(2105, 0);
            player.getUnlockedRecipies().put(2102, 0);
            assertTrue(handler.execute(quest, condition, "", 2102));
            assertFalse(handler.execute(null, condition, "", 9999));
            condition.setType(QUEST_CONTENT_NOT_UNLOCKED_RECIPE);
            condition.setParamStr("2102");
            assertFalse(handler.execute(quest, condition, "", 2102));
            player.getUnlockedRecipies().remove(2102);
            assertTrue(handler.execute(quest, condition, "", 2102));
            condition.setParamStr("999999");
            assertFalse(handler.execute(quest, condition, ""));
            assertTrue(ContentCurrentState.checkOnStart(QUEST_CONTENT_UNLOCKED_RECIPE));
            assertTrue(ContentCurrentState.checkOnStart(QUEST_CONTENT_NOT_UNLOCKED_RECIPE));
        } finally {
            old.forEach(
                    (id, row) -> {
                        if (row == null) GameData.getCookRecipeDataMap().remove((int) id);
                        else GameData.getCookRecipeDataMap().put((int) id, row);
                    });
        }
    }

    @Test
    void recipeListsRejectEmptyMalformedAndNonPositiveIds() {
        for (String spec : new String[] {null, "", "3107,", "3107,NaN", "0", "-1"}) {
            assertEquals(0, ContentCurrentState.parseIds(spec).length);
        }
        assertArrayEquals(
                new int[] {3107, 2105, 2102}, ContentCurrentState.parseIds("3107, 2105,2102"));
    }
}
