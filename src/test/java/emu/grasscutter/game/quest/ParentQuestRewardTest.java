package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.mongodb.client.MongoClients;

import dev.morphia.Morphia;
import dev.morphia.mapping.MapperOptions;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.PlayerProgressManager;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.quest.exec.ExecSetQuestVar;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.game.world.TestWorld;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.game.GameSession;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

@ExtendWith(ServerResourceFixture.class)
final class ParentQuestRewardTest {
    private static final int MAIN = 20039;
    private static final int SUB = 2003917;
    private static final int ITEM = 991805;
    private static final int OTHER = 991806;
    private static final int FIRST = 991807;
    private static final int SECOND = 991808;
    private static final int THIRD = 991809;
    private final Gson gson = new Gson();
    private final Map<Integer, ItemData> oldItems = new HashMap<>();
    private final Map<Integer, RewardData> oldRewards = new HashMap<>();
    private MainQuestData oldMain;
    private QuestData oldSub;
    private ConfigGlobalCombat oldCombat;
    private RecordingPlayer player;
    private RecordingMainQuest parent;

    @BeforeEach
    void setup() {
        oldMain = GameData.getMainQuestDataMap().get(MAIN);
        oldSub = GameData.getQuestDataMap().get(SUB);
        oldCombat = GameData.getConfigGlobalCombat();
        for (int id : new int[] {ITEM, OTHER, 102, 201, 202}) {
            var item =
                    gson.fromJson(
                            "{id:"
                                    + id
                                    + ",itemType:'"
                                    + (id < 1000 ? "ITEM_VIRTUAL" : "ITEM_MATERIAL")
                                    + "',stackLimit:999999}",
                            ItemData.class);
            oldItems.put(id, GameData.getItemDataMap().put(id, item));
        }
        for (int id : new int[] {FIRST, SECOND, THIRD})
            oldRewards.put(id, GameData.getRewardDataMap().get(id));
        reward(FIRST, ITEM, 1);
        reward(SECOND, ITEM, 2);
        reward(THIRD, OTHER, 9);
        player = new RecordingPlayer();
        player.setUid(991810);
        var session =
                new GameSession(null) {
                    @Override
                    public void send(BasePacket packet) {
                        player.sent.add(packet);
                    }
                };
        session.setPlayer(player);
        configure("[]", "[]", "[]", false);
    }

    @AfterEach
    void restore() throws Exception {
        QuestManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        GameData.setConfigGlobalCombat(oldCombat);
        if (oldMain == null) GameData.getMainQuestDataMap().remove(MAIN);
        else GameData.getMainQuestDataMap().put(MAIN, oldMain);
        if (oldSub == null) GameData.getQuestDataMap().remove(SUB);
        else GameData.getQuestDataMap().put(SUB, oldSub);
        oldItems.forEach(
                (id, row) -> {
                    if (row == null) GameData.getItemDataMap().remove(id);
                    else GameData.getItemDataMap().put(id, row);
                });
        oldRewards.forEach(
                (id, row) -> {
                    if (row == null) GameData.getRewardDataMap().remove(id);
                    else GameData.getRewardDataMap().put(id, row);
                });
    }

    @Test
    void rewardSelectorUsesTheRegisteredMainQuestStateHandler() {
        var handlers = new Int2ObjectOpenHashMap<QuestExecHandler>();
        new QuestSystem(null).registerHandler(handlers, ExecSetQuestVar.class);
        assertSame(handlers.get(48), handlers.get(33));
        var action = new QuestData.QuestExecParam();
        action.setType(QuestExec.QUEST_EXEC_UPDATE_PARENT_QUEST_REWARD_INDEX);
        assertTrue(handlers.get(33).execute(child(), action, "1"));
        assertEquals(1, parent.getRewardIndex());
        parent.finish();
        assertEquals(Map.of(ITEM, 2), player.inventory.grants);
    }

    @Test
    void completionPaysOnlyTheSelectedBranchIncludingCurrenciesAndAdventureExp() {
        GameData.getRewardDataMap()
                .put(
                        SECOND,
                        gson.fromJson(
                                "{rewardId:"
                                        + SECOND
                                        + ",rewardItemList:[{id:"
                                        + ITEM
                                        + ",count:2}],scoin:7,hcoin:3,playerExp:5}",
                                RewardData.class));
        assertTrue(parent.updateRewardIndex("1"));
        parent.finish();
        assertTrue(parent.isFinished());
        assertEquals(ParentQuestState.PARENT_QUEST_STATE_FINISHED, parent.getState());
        assertEquals(Map.of(ITEM, 2, 202, 7, 201, 3, 102, 5), player.inventory.grants);
        assertEquals(Boolean.TRUE, parent.getCompletionRewardGranted());
        assertEquals(Boolean.TRUE, parent.lastSavedGranted);
        assertEquals(1, player.inventory.admissions);
        parent.finish();
        player.getQuestManager().retryPendingCompletionRewards();
        assertEquals(1, player.inventory.admissions);
    }

    @Test
    void finalSubquestAppliesItsRewardIndexBeforeClosingTheParent() throws Exception {
        configure("[]", selector("1"), "[]", true);
        attachSceneAndActivities();
        child().finish();
        assertEquals(QuestState.QUEST_STATE_FINISHED, child().getState());
        assertTrue(parent.isFinished());
        assertEquals(1, parent.getRewardIndex());
        assertEquals(Map.of(ITEM, 2), player.inventory.grants);
    }

    @Test
    void startSelectorIsAppliedBeforeTheNewQuestCanAdvance() throws Exception {
        configure(selector("2"), "[]", "[]", false);
        attachSceneAndActivities();
        child().setState(QuestState.QUEST_STATE_UNSTARTED);
        child().start();
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, child().getState());
        assertEquals(2, parent.getRewardIndex());
        parent.finish();
        assertEquals(Map.of(OTHER, 9), player.inventory.grants);
    }

    @Test
    void failureSelectorIsAppliedBeforeTheFailureStateEvent() {
        configure("[]", "[]", selector("2"), false);
        child().fail();
        assertEquals(QuestState.QUEST_STATE_FAILED, child().getState());
        assertEquals(2, parent.getRewardIndex());
        parent.finish();
        assertEquals(Map.of(OTHER, 9), player.inventory.grants);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "3", "bad", "", "2147483648"})
    void invalidFinalSelectorDoesNotCompleteOrPayTheDefaultBranch(String index) {
        configure("[]", selector(index), "[]", true);
        child().finish();
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, child().getState());
        assertFalse(parent.isFinished());
        assertTrue(player.inventory.grants.isEmpty());
        assertEquals(0, player.inventory.admissions);
    }

    @Test
    void selectorBatchIsValidatedBeforeChangingThePreviousSelection() {
        var first = new QuestData.QuestExecParam();
        first.setType(QuestExec.QUEST_EXEC_UPDATE_PARENT_QUEST_REWARD_INDEX);
        first.setParam(new String[] {"1"});
        var second = new QuestData.QuestExecParam();
        second.setType(QuestExec.QUEST_EXEC_UPDATE_PARENT_QUEST_REWARD_INDEX);
        second.setParam(new String[] {"bad"});
        assertFalse(parent.prepareRewardIndexActions(List.of(first, second)));
        assertEquals(0, parent.getRewardIndex());
        assertEquals(0, parent.saves);
    }

    @Test
    void refusedAdmissionKeepsTheCompletedRewardPendingForLoginRetry() {
        parent.updateRewardIndex("2");
        player.inventory.refuse = true;
        parent.finish();
        assertTrue(parent.isFinished());
        assertEquals(Boolean.FALSE, parent.getCompletionRewardGranted());
        assertTrue(player.inventory.grants.isEmpty());
        player.inventory.refuse = false;
        player.getQuestManager().retryPendingCompletionRewards();
        assertEquals(Map.of(OTHER, 9), player.inventory.grants);
        assertEquals(Boolean.TRUE, parent.getCompletionRewardGranted());
        player.getQuestManager().retryPendingCompletionRewards();
        assertEquals(Map.of(OTHER, 9), player.inventory.grants);
    }

    @Test
    void pendingRewardKeepsItsSelectedIdAcrossResourceChanges() {
        parent.updateRewardIndex("1");
        player.inventory.refuse = true;
        parent.finish();
        GameData.getMainQuestDataMap()
                .put(
                        MAIN,
                        gson.fromJson(
                                "{id:" + MAIN + ",rewardIdList:[" + THIRD + "," + FIRST + "]}",
                                MainQuestData.class));
        assertFalse(parent.updateRewardIndex("0"));
        player.inventory.refuse = false;
        assertTrue(parent.tryGrantCompletionReward());
        assertEquals(Map.of(ITEM, 2), player.inventory.grants);
    }

    @Test
    void missingRewardDefinitionCanBeRetriedAfterResourcesAreRepaired() {
        GameData.getRewardDataMap().remove(FIRST);
        parent.finish();
        assertEquals(Boolean.FALSE, parent.getCompletionRewardGranted());
        assertEquals(0, player.inventory.admissions);
        reward(FIRST, ITEM, 1);
        assertTrue(parent.tryGrantCompletionReward());
        assertEquals(Map.of(ITEM, 1), player.inventory.grants);
    }

    @Test
    void zeroRewardBranchDoesNotFallThroughToAnotherBranch() {
        GameData.getMainQuestDataMap()
                .put(
                        MAIN,
                        gson.fromJson(
                                "{id:" + MAIN + ",rewardIdList:[0," + SECOND + "]}",
                                MainQuestData.class));
        parent.finish();
        assertEquals(Boolean.TRUE, parent.getCompletionRewardGranted());
        assertTrue(player.inventory.grants.isEmpty());
        assertEquals(0, player.inventory.admissions);
    }

    @Test
    void aLegacyCompletedParentIsNotAutomaticallyPaidAgain() throws Exception {
        player.inventory.refuse = true;
        parent.finish();
        field("completionRewardGranted").set(parent, null);
        field("completionRewardId").set(parent, null);
        player.inventory.refuse = false;
        player.getQuestManager().retryPendingCompletionRewards();
        parent.finish();
        assertTrue(player.inventory.grants.isEmpty());
        assertNull(parent.getCompletionRewardGranted());
    }

    @Test
    void pendingRewardAndLegacyClaimStateSurviveBsonRoundTrip() throws Exception {
        var saved = new GameMainQuest(player, MAIN);
        field("isFinished").setBoolean(saved, true);
        field("state").set(saved, ParentQuestState.PARENT_QUEST_STATE_FINISHED);
        field("rewardIndex").setInt(saved, 1);
        field("completionRewardId").set(saved, SECOND);
        try (var client = MongoClients.create("mongodb://127.0.0.1:1")) {
            var store =
                    Morphia.createDatastore(
                            client,
                            "quest_reward_mapping_only",
                            MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
            var mapper = store.getMapper();
            mapper.map(GameMainQuest.class);
            Document document = mapper.toDocument(saved);
            assertEquals(Boolean.FALSE, document.getBoolean("completionRewardGranted"));
            var restored = mapper.fromDocument(GameMainQuest.class, document);
            assertEquals(Boolean.FALSE, restored.getCompletionRewardGranted());
            assertEquals(1, restored.getRewardIndex());
            assertEquals(SECOND, field("completionRewardId").get(restored));
            document.remove("completionRewardGranted");
            var legacy = mapper.fromDocument(GameMainQuest.class, document);
            assertNull(legacy.getCompletionRewardGranted());
        }
    }

    @Test
    void concurrentCompletionRequestsGrantTheRewardOnce() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(parent::finish);
            var second = executor.submit(parent::finish);
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
        assertEquals(Map.of(ITEM, 1), player.inventory.grants);
        assertEquals(1, player.inventory.admissions);
    }

    private void configure(String begin, String finish, String fail, boolean finishParent) {
        var main =
                gson.fromJson(
                        "{id:"
                                + MAIN
                                + ",rewardIdList:["
                                + FIRST
                                + ","
                                + SECOND
                                + ","
                                + THIRD
                                + "],subQuests:[{subId:"
                                + SUB
                                + "}],talks:[]}",
                        MainQuestData.class);
        GameData.getMainQuestDataMap().put(MAIN, main);
        GameData.getQuestDataMap()
                .put(
                        SUB,
                        gson.fromJson(
                                "{subId:"
                                        + SUB
                                        + ",mainId:"
                                        + MAIN
                                        + ",finishCond:[],failCond:[],gainItems:[],beginExec:"
                                        + begin
                                        + ",finishExec:"
                                        + finish
                                        + ",failExec:"
                                        + fail
                                        + ",finishParent:"
                                        + finishParent
                                        + "}",
                                QuestData.class));
        parent = new RecordingMainQuest(player);
        player.getQuestManager().getMainQuests().put(MAIN, parent);
        child().setState(QuestState.QUEST_STATE_UNFINISHED);
    }

    private GameQuest child() {
        return parent.getChildQuests().get(SUB);
    }

    private void attachSceneAndActivities() throws Exception {
        player.activity = new ActivityManager(player);
        ScriptLoader.init();
        var config = emu.grasscutter.Grasscutter.getConfig().server.game;
        boolean enabled = config.enableScriptInBigWorld;
        config.enableScriptInBigWorld = false;
        try {
            var world = TestWorld.create(player);
            player.setScene(new TestWorld.TestScene(world, 3, SceneType.SCENE_WORLD));
        } finally {
            config.enableScriptInBigWorld = enabled;
        }
    }

    private String selector(String value) {
        return "[{type:'QUEST_EXEC_UPDATE_PARENT_QUEST_REWARD_INDEX',param:['" + value + "']}]";
    }

    private void reward(int id, int item, int count) {
        GameData.getRewardDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{rewardId:"
                                        + id
                                        + ",rewardItemList:[{id:"
                                        + item
                                        + ",count:"
                                        + count
                                        + "}]}",
                                RewardData.class));
    }

    private static Field field(String name) throws Exception {
        var field = GameMainQuest.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class RecordingMainQuest extends GameMainQuest {
        int saves;
        Boolean lastSavedGranted;

        RecordingMainQuest(Player player) {
            super(player, MAIN);
        }

        @Override
        public void save() {
            saves++;
            lastSavedGranted = getCompletionRewardGranted();
        }
    }

    private static final class RecordingPlayer extends Player {
        final List<BasePacket> sent = new CopyOnWriteArrayList<>();
        final RecordingInventory inventory = new RecordingInventory(this);
        final PlayerProgressManager progress = new PlayerProgressManager(this);
        ActivityManager activity;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public PlayerProgressManager getProgressManager() {
            return progress;
        }

        @Override
        public ActivityManager getActivityManager() {
            return activity;
        }

        @Override
        public void save() {}

        @Override
        public void sendPacket(BasePacket packet) {
            sent.add(packet);
        }
    }

    private static final class RecordingInventory extends Inventory {
        final Map<Integer, Integer> grants = new HashMap<>();
        boolean refuse;
        int admissions;

        RecordingInventory(Player player) {
            super(player);
        }

        @Override
        public synchronized InventoryAddResult addItems(
                Collection<GameItem> items,
                ActionReason reason,
                InventoryAddPolicy policy,
                BooleanSupplier authorize,
                Runnable confirmed) {
            admissions++;
            assertEquals(ActionReason.QuestReward, reason);
            assertEquals(InventoryAddPolicy.ALL_OR_NOTHING, policy);
            if (refuse)
                return InventoryAddResult.rejected(items, InventoryAddResult.Status.CAPACITY_FULL);
            assertTrue(authorize.getAsBoolean());
            confirmed.run();
            for (var item : items) grants.merge(item.getItemId(), item.getCount(), Integer::sum);
            return new InventoryAddResult(
                    items.stream()
                            .map(
                                    item ->
                                            new InventoryAddResult.Entry(
                                                    item.getItemId(),
                                                    item.getCount(),
                                                    item.getCount(),
                                                    InventoryAddResult.Status.ADDED))
                            .toList());
        }
    }
}
