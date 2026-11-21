package emu.grasscutter.game.entity.gadget;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.TestWorld;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GadgetChainLevelChangeNotifyOuterClass.GadgetChainLevelChangeNotify;
import emu.grasscutter.net.proto.GadgetChainLevelUpdateNotifyOuterClass.GadgetChainLevelUpdateNotify;
import emu.grasscutter.net.proto.PlayerOfferingDataNotifyOuterClass.PlayerOfferingDataNotify;
import emu.grasscutter.net.proto.PlayerOfferingRspOuterClass.PlayerOfferingRsp;
import emu.grasscutter.net.proto.PlayerPropNotifyOuterClass.PlayerPropNotify;
import emu.grasscutter.net.proto.SceneOwnerGadgetChainLevelNotifyOuterClass.SceneOwnerGadgetChainLevelNotify;
import emu.grasscutter.net.proto.TakeOfferingLevelRewardRspOuterClass.TakeOfferingLevelRewardRsp;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.game.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@ExtendWith(ServerResourceFixture.class)
final class OfferingRequestTest {
    private static final int UID = 991781;
    private static final int SIGIL = 304;
    private static final int SECOND = 991782;
    private static final int PRIZE = 991783;
    private static final int REWARD = 991784;
    private static final Path STATE = Path.of("data/offering_levels.json");
    private final Map<Integer, ItemData> previousItems = new HashMap<>();
    private RewardData previousReward;
    private Map<Object, Object> previousRows;
    private boolean previousLoaded;
    private Object previousChains;
    private byte[] previousState;
    private RecordingPlayer player;
    private GameSession session;

    @BeforeEach
    void setup() throws Exception {
        previousState = Files.exists(STATE) ? Files.readAllBytes(STATE) : null;
        previousRows = new HashMap<>(cache("LEVEL_ROWS"));
        previousLoaded = loaded().getBoolean(null);
        var chainField = OfferingHelper.class.getDeclaredField("gadgetChains");
        chainField.setAccessible(true);
        previousChains = chainField.get(null);
        cache("LEVELS").remove(UID);
        cache("TAKEN").remove(UID);
        for (int id : new int[] {SIGIL, SECOND, PRIZE, 202}) {
            previousItems.put(
                    id,
                    GameData.getItemDataMap()
                            .put(
                                    id,
                                    new Gson()
                                            .fromJson(
                                                    "{id:"
                                                            + id
                                                            + ",itemType:'"
                                                            + (id == 202
                                                                    ? "ITEM_VIRTUAL"
                                                                    : "ITEM_MATERIAL")
                                                            + "',stackLimit:999999}",
                                                    ItemData.class)));
        }
        previousReward =
                GameData.getRewardDataMap()
                        .put(
                                REWARD,
                                new Gson()
                                        .fromJson(
                                                "{rewardId:"
                                                        + REWARD
                                                        + ",rewardItemList:[{id:"
                                                        + PRIZE
                                                        + ",count:3}],scoin:7}",
                                                RewardData.class));
        config(
                "[{offeringId:2,level:1,rewardId:"
                        + REWARD
                        + ",consumeItemConfigVec:[{id:304,count:25},{}],condVec:[{},{}]},"
                        + "{offeringId:2,level:2,rewardId:"
                        + REWARD
                        + ",consumeItemConfigVec:[{id:304,count:25},{}],condVec:[{},{}]}]");
        player = new RecordingPlayer();
        player.setUid(UID);
        player.getOpenStates().put(OfferingHelper.OPEN_STATE_ORAIONOKAMI, 1);
        session = new GameSession(null);
        session.setState(GameSession.SessionState.ACTIVE);
        session.setPlayer(player);
        player.inventory.stock(SIGIL, 60);
    }

    @AfterEach
    void restore() throws Exception {
        cache("LEVEL_ROWS").clear();
        cache("LEVEL_ROWS").putAll(previousRows);
        loaded().setBoolean(null, previousLoaded);
        var chainField = OfferingHelper.class.getDeclaredField("gadgetChains");
        chainField.setAccessible(true);
        chainField.set(null, previousChains);
        emu.grasscutter.game.quest.QuestManager.eventExecutor
                .submit(() -> {})
                .get(5, TimeUnit.SECONDS);
        cache("LEVELS").remove(UID);
        cache("TAKEN").remove(UID);
        for (var item : previousItems.entrySet()) {
            if (item.getValue() == null) GameData.getItemDataMap().remove(item.getKey());
            else GameData.getItemDataMap().put(item.getKey(), item.getValue());
        }
        if (previousReward == null) GameData.getRewardDataMap().remove(REWARD);
        else GameData.getRewardDataMap().put(REWARD, previousReward);
        if (previousState == null) Files.deleteIfExists(STATE);
        else Files.write(STATE, previousState);
    }

    @Test
    void nativeSubmitPaysTheCompleteAffordableBatchOnce() throws Exception {
        var reply = offer("1802");
        assertEquals(0, reply.getRetcode());
        assertEquals(2, reply.getOfferingData().getOfferingId());
        assertEquals(2, reply.getOfferingData().getLevel());
        assertEquals(10, player.inventory.getItemCountById(SIGIL));
        assertEquals(1, player.inventory.payments);
        assertEquals(1, player.saves);
        assertTrue(player.inventory.grants.isEmpty());
        assertTrue(reply.getItemListList().isEmpty());
        assertTrue(Files.readString(STATE).contains("\"2\": 2"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0802", "1a0102", "180280", "18ffffffff0f"})
    void invalidOrLegacyFieldsCannotDefaultToSakuraOrSpendMaterials(String hex) throws Exception {
        assertNotEquals(0, offer(hex).getRetcode());
        assertEquals(60, player.inventory.getItemCountById(SIGIL));
        assertEquals(0, player.inventory.payments);
        assertEquals(0, OfferingHelper.getLevel(player, 2));
        assertEquals(0, player.saves);
    }

    @Test
    void rejectedPaymentNeverAdvancesOfferingProgress() throws Exception {
        player.inventory.rejectPayment = true;
        assertNotEquals(0, offer("1802").getRetcode());
        assertEquals(60, player.inventory.getItemCountById(SIGIL));
        assertEquals(0, OfferingHelper.getLevel(player, 2));
        assertEquals(0, player.saves);
    }

    @Test
    void allConfiguredCostsAreRequiredBeforeAnyPayment() throws Exception {
        config(
                "[{offeringId:2,level:1,rewardId:"
                        + REWARD
                        + ",consumeItemConfigVec:[{id:304,count:25},{id:"
                        + SECOND
                        + ",count:1}]}]");
        assertNotEquals(0, offer("1802").getRetcode());
        assertEquals(60, player.inventory.getItemCountById(SIGIL));
        assertEquals(0, player.inventory.payments);
        player.inventory.stock(SECOND, 1);
        assertEquals(0, offer("1802").getRetcode());
        assertEquals(35, player.inventory.getItemCountById(SIGIL));
        assertEquals(0, player.inventory.getItemCountById(SECOND));
        assertEquals(1, OfferingHelper.getLevel(player, 2));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "consumeItemConfigVec:[{id:304,count:-1}]",
                "consumeItemConfigVec:[]",
                "consumeItemConfigVec:[{id:304,count:25}],condVec:[{actionType:'OFFERING_ACTION_OPEN_ROUTINE',param:'ROUTINE_SNOW_MOUNTAIN'}]",
                "consumeItemConfigVec:[{id:304,count:25}],condVec:[{actionType:'OFFERING_ACTION_IMPROVE_TPS_STAMINA',param:'-40'}]"
            })
    void malformedOrUnsupportedLevelsAreNotCharged(String row) throws Exception {
        config("[{offeringId:2,level:1,rewardId:" + REWARD + "," + row + "}]");
        assertNotEquals(0, offer("1802").getRetcode());
        assertEquals(60, player.inventory.getItemCountById(SIGIL));
        assertEquals(0, OfferingHelper.getLevel(player, 2));
        assertEquals(0, player.inventory.payments);
    }

    @Test
    void aClosedOfferingDoesNotAcceptSubmission() throws Exception {
        player.getOpenStates().put(OfferingHelper.OPEN_STATE_ORAIONOKAMI, 0);
        assertNotEquals(0, offer("1802").getRetcode());
        assertEquals(0, player.inventory.payments);
    }

    @Test
    void tpsOfferingRaisesOnlyItsOwnStaminaAndNotifiesNativeProperties() throws Exception {
        config(tpsLevels(5, "40"));
        player.setProperty(PlayerProperty.PROP_MAX_TPS_STAMINA, 20_000, false);
        player.setProperty(PlayerProperty.PROP_CUR_PERSIST_TPS_STAMINA, 12_500, false);
        player.setProperty(PlayerProperty.PROP_MAX_STAMINA, 24_000, false);
        player.setProperty(PlayerProperty.PROP_CUR_PERSIST_STAMINA, 18_000, false);
        var reply = offer("1813");
        assertEquals(0, reply.getRetcode());
        assertEquals(5, reply.getOfferingData().getLevel());
        assertEquals(40_000, player.getProperty(PlayerProperty.PROP_MAX_TPS_STAMINA));
        assertEquals(12_500, player.getProperty(PlayerProperty.PROP_CUR_PERSIST_TPS_STAMINA));
        assertEquals(24_000, player.getProperty(PlayerProperty.PROP_MAX_STAMINA));
        assertEquals(18_000, player.getProperty(PlayerProperty.PROP_CUR_PERSIST_STAMINA));
        assertEquals(5, player.lastSavedVariables.get(910019));
        assertEquals(40_000, player.lastSavedProperties.get(10080));
        assertEquals(12_500, player.lastSavedProperties.get(10081));
        Map<Integer, Long> notified = new HashMap<>();
        for (var packet : player.sent) {
            if (packet.getOpcode() == PacketOpcodes.PlayerPropNotify)
                PlayerPropNotify.parseFrom(packet.getData())
                        .getPropMapMap()
                        .forEach((id, value) -> notified.put(id, value.getVal()));
        }
        assertEquals(40_000L, notified.get(10080));
        assertEquals(12_500L, notified.get(10081));
        assertEquals(35, player.inventory.getItemCountById(SIGIL));
        assertNotEquals(0, offer("1813").getRetcode());
        assertEquals(1, player.inventory.payments);
    }

    @Test
    void tpsStaminaRestoresFromOfferingProgressWithoutStackingOrRefilling() throws Exception {
        config(tpsLevels(5, "40"));
        player.getQuestGlobalVariables().put(910019, 3);
        player.getProperties().remove(10080);
        player.getProperties().remove(10081);
        OfferingHelper.onPlayerLogin(player);
        assertEquals(32_000, player.getProperty(PlayerProperty.PROP_MAX_TPS_STAMINA));
        assertEquals(20_000, player.getProperty(PlayerProperty.PROP_CUR_PERSIST_TPS_STAMINA));
        assertTrue(player.setProperty(PlayerProperty.PROP_CUR_PERSIST_TPS_STAMINA, 31_000, false));
        assertFalse(player.setProperty(PlayerProperty.PROP_CUR_PERSIST_TPS_STAMINA, 32_001, false));
        cache("LEVELS").remove(UID);
        Files.deleteIfExists(STATE);
        OfferingHelper.onPlayerLogin(player);
        OfferingHelper.onPlayerLogin(player);
        assertEquals(32_000, player.getProperty(PlayerProperty.PROP_MAX_TPS_STAMINA));
        assertEquals(31_000, player.getProperty(PlayerProperty.PROP_CUR_PERSIST_TPS_STAMINA));
        var snapshot =
                player.sent.stream()
                        .filter(
                                packet ->
                                        packet.getOpcode()
                                                == PacketOpcodes.PlayerOfferingDataNotify)
                        .reduce((first, last) -> last)
                        .orElseThrow();
        var offering =
                PlayerOfferingDataNotify.parseFrom(snapshot.getData())
                        .getOfferingDataListList()
                        .stream()
                        .filter(data -> data.getOfferingId() == 19)
                        .findFirst()
                        .orElseThrow();
        assertEquals(3, offering.getLevel());
        assertEquals(0, player.inventory.payments);
    }

    @Test
    void refusedTpsPaymentDoesNotChangeOrSaveStamina() throws Exception {
        config(tpsLevels(1, "40"));
        player.setProperty(PlayerProperty.PROP_MAX_TPS_STAMINA, 20_000, false);
        player.setProperty(PlayerProperty.PROP_CUR_PERSIST_TPS_STAMINA, 12_500, false);
        player.inventory.rejectPayment = true;
        assertNotEquals(0, offer("1813").getRetcode());
        assertEquals(20_000, player.getProperty(PlayerProperty.PROP_MAX_TPS_STAMINA));
        assertEquals(12_500, player.getProperty(PlayerProperty.PROP_CUR_PERSIST_TPS_STAMINA));
        assertEquals(0, OfferingHelper.getLevel(player, 19));
        assertEquals(0, player.saves);
        assertEquals(60, player.inventory.getItemCountById(SIGIL));
    }

    @Test
    void tpsStaminaAndGadgetChainUpgradeTogetherAfterAllActionsPass() throws Exception {
        config(
                "[{offeringId:19,level:1,consumeItemConfigVec:[{id:304,count:3}],"
                    + "condVec:[{actionType:'OFFERING_ACTION_IMPROVE_TPS_STAMINA',param:'40'},"
                    + "{actionType:'OFFERING_ACTION_SET_GADGET_CHAIN_LEVEL',param:'100011,2'}]}]");
        chains("[]");
        player.setProperty(PlayerProperty.PROP_MAX_TPS_STAMINA, 20_000, false);
        assertNotEquals(0, offer("1813").getRetcode());
        assertEquals(0, player.inventory.payments);
        assertEquals(20_000, player.getProperty(PlayerProperty.PROP_MAX_TPS_STAMINA));
        chains("[{chainId:100011,initLevel:1,maxLevel:10}]");
        assertEquals(0, offer("1813").getRetcode());
        assertEquals(24_000, player.getProperty(PlayerProperty.PROP_MAX_TPS_STAMINA));
        assertEquals(1, OfferingHelper.getLevel(player, 19));
        assertEquals(57, player.inventory.getItemCountById(SIGIL));
        var change =
                player.sent.stream()
                        .filter(packet -> packet.getOpcode() == 21874)
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                Map.of(100011, 2),
                GadgetChainLevelChangeNotify.parseFrom(change.getData())
                        .getGadgetChainLevelMapMap());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-40", "201", "2147483647", "40,40", "abc"})
    void invalidTpsStaminaIncrementsAreNotCharged(String increment) throws Exception {
        config(tpsLevels(1, increment));
        assertNotEquals(0, offer("1813").getRetcode());
        assertEquals(0, player.inventory.payments);
        assertEquals(60, player.inventory.getItemCountById(SIGIL));
        assertEquals(0, OfferingHelper.getLevel(player, 19));
    }

    @Test
    void tpsOfferingStopsBeforeTheFirstTierExceedingItsLimit() throws Exception {
        config(tpsLevels(6, "40"));
        assertEquals(0, offer("1813").getRetcode());
        assertEquals(5, OfferingHelper.getLevel(player, 19));
        assertEquals(40_000, player.getProperty(PlayerProperty.PROP_MAX_TPS_STAMINA));
        assertEquals(35, player.inventory.getItemCountById(SIGIL));
    }

    private static String tpsLevels(int count, String increment) {
        var rows = new ArrayList<String>();
        for (int level = 1; level <= count; level++)
            rows.add(
                    "{offeringId:19,level:"
                            + level
                            + ",consumeItemConfigVec:[{id:304,count:5}],"
                            + "condVec:[{actionType:'OFFERING_ACTION_IMPROVE_TPS_STAMINA',param:'"
                            + increment
                            + "'}]}");
        return "[" + String.join(",", rows) + "]";
    }

    @Test
    void refusedRewardCanBeRetriedAndIncludesCurrencyFields() throws Exception {
        OfferingHelper.setLevel(player, 2, 1);
        player.inventory.rejectGrant = true;
        assertNotEquals(0, reward("08025001").getRetcode());
        assertTrue(player.inventory.grants.isEmpty());
        player.inventory.rejectGrant = false;
        var reply = reward("08025001");
        assertEquals(0, reply.getRetcode());
        assertEquals(1, reply.getTakeLevel());
        assertEquals(2, reply.getOfferingId());
        assertEquals(
                Set.of(PRIZE, 202),
                reply.getItemListList().stream()
                        .map(item -> item.getItemId())
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, player.inventory.grants.size());
        assertNotEquals(0, reward("08025001").getRetcode());
        assertEquals(2, player.inventory.grants.size());
    }

    @Test
    void missingRewardConfigurationDoesNotConsumeTheClaim() throws Exception {
        OfferingHelper.setLevel(player, 2, 1);
        var definition = GameData.getRewardDataMap().remove(REWARD);
        assertNotEquals(0, reward("08025001").getRetcode());
        GameData.getRewardDataMap().put(REWARD, definition);
        assertEquals(0, reward("08025001").getRetcode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0802", "5001", "0802500180"})
    void incompleteClaimsNeverGuessAnOfferingOrLevel(String hex) throws Exception {
        OfferingHelper.setLevel(player, 2, 1);
        assertNotEquals(0, reward(hex).getRetcode());
        assertTrue(player.inventory.grants.isEmpty());
    }

    @Test
    void twoConcurrentClaimsGrantOneReward() throws Exception {
        OfferingHelper.setLevel(player, 2, 1);
        byte[] payload = HexFormat.of().parseHex("08025001");
        var ready = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> claim =
                    () -> {
                        ready.await();
                        return OfferingHelper.handleRewardRequest(player, payload, null);
                    };
            var first = pool.submit(claim);
            var second = pool.submit(claim);
            ready.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertTrue(second.get(5, TimeUnit.SECONDS));
            assertEquals(2, player.inventory.grants.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void automaticRewardsAreAdmittedBeforePaymentAndAreClaimedOnce() throws Exception {
        config(
                "[{offeringId:2,level:1,rewardId:"
                        + REWARD
                        + ",isAutoTakeReward:true,consumeItemConfigVec:[{id:304,count:25}]}]");
        player.inventory.rejectGrant = true;
        assertNotEquals(0, offer("1802").getRetcode());
        assertEquals(0, player.inventory.payments);
        assertEquals(0, OfferingHelper.getLevel(player, 2));
        player.inventory.rejectGrant = false;
        player.inventory.rejectPayment = true;
        assertNotEquals(0, offer("1802").getRetcode());
        assertTrue(player.inventory.grants.isEmpty());
        assertEquals(0, OfferingHelper.getLevel(player, 2));
        player.inventory.rejectPayment = false;
        var reply = offer("1802");
        assertEquals(0, reply.getRetcode());
        assertEquals(35, player.inventory.getItemCountById(SIGIL));
        assertEquals(2, reply.getItemListCount());
        assertEquals(2, player.inventory.grants.size());
        assertEquals(List.of(1), reply.getOfferingData().getTakenLevelRewardListList());
        assertNotEquals(0, reward("08025001").getRetcode());
        assertEquals(2, player.inventory.grants.size());
    }

    @Test
    void missingAutomaticRewardDefinitionCannotSpendMaterials() throws Exception {
        config(
                "[{offeringId:2,level:1,rewardId:"
                        + REWARD
                        + ",isAutoTakeReward:true,consumeItemConfigVec:[{id:304,count:25}]}]");
        GameData.getRewardDataMap().remove(REWARD);
        assertNotEquals(0, offer("1802").getRetcode());
        assertEquals(0, player.inventory.payments);
        assertEquals(0, OfferingHelper.getLevel(player, 2));
    }

    @Test
    void uniqueGiftAndQuestVariableAreCommittedWithTheLevel() throws Exception {
        String action = "{actionType:'OFFERING_ACTION_GRANT_UNIQUE_ITEM',param:'" + SECOND + "'}";
        config(
                "[{offeringId:2,level:1,rewardId:"
                        + REWARD
                        + ",consumeItemConfigVec:[{id:304,count:25}],condVec:["
                        + action
                        + "]},"
                        + "{offeringId:2,level:2,rewardId:"
                        + REWARD
                        + ",consumeItemConfigVec:[{id:304,count:25}],condVec:["
                        + action
                        + ",{actionType:'OFFERING_ACTION_SET_QUEST_GLOBAL_VAR',param:'7603901,1'}]}]");
        player.inventory.rejectGrant = true;
        assertNotEquals(0, offer("1802").getRetcode());
        assertEquals(0, player.inventory.payments);
        assertFalse(player.getQuestGlobalVariables().containsKey(7603901));
        player.inventory.rejectGrant = false;
        var reply = offer("1802");
        assertEquals(0, reply.getRetcode());
        assertEquals(1, reply.getItemListCount());
        assertEquals(SECOND, reply.getItemList(0).getItemId());
        assertEquals(1, reply.getItemList(0).getCount());
        assertEquals(1, player.getQuestGlobalVariables().get(7603901));
        assertEquals(1, player.lastSavedVariables.get(7603901));
        assertEquals(1, player.inventory.grants.size());
    }

    @Test
    void anOwnedUniqueGiftDoesNotBlockAnOtherwiseAffordableUpgrade() throws Exception {
        config(
                "[{offeringId:2,level:1,consumeItemConfigVec:[{id:304,count:25}],"
                        + "condVec:[{actionType:'OFFERING_ACTION_GRANT_UNIQUE_ITEM',param:'"
                        + SECOND
                        + "'}]}]");
        player.inventory.stock(SECOND, 1);
        player.inventory.rejectGrant = true;
        assertEquals(0, offer("1802").getRetcode());
        assertTrue(player.inventory.grants.isEmpty());
        assertEquals(1, player.inventory.getItemCountById(SECOND));
    }

    @Test
    void missingGadgetChainConfigAndMalformedArgumentsAreRejectedBeforePayment() throws Exception {
        config(
                "[{offeringId:2,level:1,consumeItemConfigVec:[{id:304,count:25}],"
                    + "condVec:[{actionType:'OFFERING_ACTION_SET_GADGET_CHAIN_LEVEL',param:'100002,2'}]}]");
        chains("[]");
        assertNotEquals(0, offer("1802").getRetcode());
        assertEquals(0, player.inventory.payments);
        config(
                "[{offeringId:2,level:1,consumeItemConfigVec:[{id:304,count:25}],"
                    + "condVec:[{actionType:'OFFERING_ACTION_SET_QUEST_GLOBAL_VAR',param:'7603901'}]}]");
        assertNotEquals(0, offer("1802").getRetcode());
        assertEquals(0, player.inventory.payments);
    }

    @Test
    void automaticPoolLevelsRestoreTheirChainFromThePlayerSave() throws Exception {
        config(
                "[{offeringId:3,level:1,rewardId:"
                        + REWARD
                        + ",isAutoTakeReward:true,consumeItemConfigVec:[{id:304,count:25}],"
                        + "condVec:[{actionType:'OFFERING_ACTION_SET_GADGET_CHAIN_LEVEL',param:'100003,1'}]}]");
        chains("[{chainId:100003,maxLevel:10}]");
        assertEquals(0, offer("1803").getRetcode());
        var change =
                player.sent.stream()
                        .filter(packet -> packet.getOpcode() == 21874)
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                Map.of(100003, 1),
                GadgetChainLevelChangeNotify.parseFrom(change.getData())
                        .getGadgetChainLevelMapMap());
        Files.deleteIfExists(STATE);
        cache("LEVELS").remove(UID);
        assertEquals(1, OfferingHelper.getLevel(player, 3));
        player.sent.clear();
        OfferingHelper.onPlayerLogin(player);
        var snapshot =
                player.sent.stream()
                        .filter(packet -> packet.getOpcode() == 26386)
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                Map.of(100003, 1),
                GadgetChainLevelUpdateNotify.parseFrom(snapshot.getData())
                        .getGadgetChainLevelMapMap());
    }

    @Test
    void sceneEntryUsesTheHostsGadgetChainInsteadOfTheVisitors() throws Exception {
        config(
                "[{offeringId:2,level:3,consumeItemConfigVec:[{id:304,count:25}],"
                    + "condVec:[{actionType:'OFFERING_ACTION_SET_GADGET_CHAIN_LEVEL',param:'100002,2'}]}]");
        chains("[{chainId:100002,initLevel:1,maxLevel:15}]");
        OfferingHelper.setLevel(player, 2, 3);
        var world = TestWorld.create(player);
        var guest = new RecordingPlayer();
        guest.setUid(UID + 1);
        guest.setWorld(world);
        guest.getQuestGlobalVariables().put(910002, 0);
        OfferingHelper.sendSceneOwnerChainLevels(guest, true);
        var packet = guest.sent.getFirst();
        assertEquals(29077, packet.getOpcode());
        var snapshot = SceneOwnerGadgetChainLevelNotify.parseFrom(packet.getData());
        assertTrue(snapshot.getIsEnterWorld());
        assertEquals(Map.of(100002, 2), snapshot.getGadgetChainLevelMapMap());
        assertEquals(0, guest.getQuestGlobalVariables().get(910002));
    }

    @Test
    void notifyGroupReceivesEveryCommittedLevelInOrder() throws Exception {
        ScriptLoader.init();
        var config = emu.grasscutter.Grasscutter.getConfig().server.game;
        boolean previous = config.enableScriptInBigWorld;
        config.enableScriptInBigWorld = false;
        try {
            var world = TestWorld.create(player);
            List<ScriptArgs> events = new ArrayList<>();
            var scene =
                    new TestWorld.TestScene(world, 3, SceneType.SCENE_WORLD) {
                        private final SceneScriptManager manager =
                                new SceneScriptManager(this) {
                                    @Override
                                    public Future<?> callEvent(ScriptArgs args) {
                                        events.add(args);
                                        return CompletableFuture.completedFuture(null);
                                    }
                                };

                        @Override
                        public SceneScriptManager getScriptManager() {
                            return manager;
                        }
                    };
            player.setScene(scene);
            config(
                    "[{offeringId:6,level:1,isAutoTakeReward:true,consumeItemConfigVec:[{id:304,count:25}],"
                        + "condVec:[{actionType:'OFFERING_ACTION_NOTIFY_GROUP',param:'133317140'}]},"
                        + "{offeringId:6,level:2,isAutoTakeReward:true,consumeItemConfigVec:[{id:304,count:25}],"
                        + "condVec:[{actionType:'OFFERING_ACTION_NOTIFY_GROUP',param:'133317140'}]}]");
            var reply = offer("1806");
            assertEquals(0, reply.getRetcode());
            assertEquals(List.of(1, 2), events.stream().map(event -> event.param2).toList());
            assertTrue(
                    events.stream()
                            .allMatch(
                                    event ->
                                            event.param1 == 6
                                                    && event.group_id == 133317140
                                                    && event.type
                                                            == EventType.EVENT_OFFERING_LEVELUP));
            assertEquals(List.of(1, 2), reply.getOfferingData().getTakenLevelRewardListList());
            assertTrue(player.inventory.grants.isEmpty());
        } finally {
            config.enableScriptInBigWorld = previous;
        }
    }

    private static void chains(String json) throws Exception {
        var parser = OfferingHelper.class.getDeclaredMethod("parseGadgetChains", JsonArray.class);
        parser.setAccessible(true);
        var field = OfferingHelper.class.getDeclaredField("gadgetChains");
        field.setAccessible(true);
        field.set(null, parser.invoke(null, JsonParser.parseString(json).getAsJsonArray()));
    }

    @Test
    void gadgetChainNotificationsUseTheNativeMapAndEntryTags() throws Exception {
        var changeWire = HexFormat.of().parseHex("120608a28d061002");
        var change = GadgetChainLevelChangeNotify.parseFrom(changeWire);
        assertEquals(Map.of(100002, 2), change.getGadgetChainLevelMapMap());
        assertArrayEquals(changeWire, change.toByteArray());
        var updateWire = HexFormat.of().parseHex("7a0608a28d061002");
        var update = GadgetChainLevelUpdateNotify.parseFrom(updateWire);
        assertEquals(Map.of(100002, 2), update.getGadgetChainLevelMapMap());
        assertArrayEquals(updateWire, update.toByteArray());
        var ownerWire = HexFormat.of().parseHex("18015a0608a28d061002");
        var owner = SceneOwnerGadgetChainLevelNotify.parseFrom(ownerWire);
        assertTrue(owner.getIsEnterWorld());
        assertEquals(Map.of(100002, 2), owner.getGadgetChainLevelMapMap());
        assertArrayEquals(ownerWire, owner.toByteArray());
    }

    @Test
    void nativeReplyAndNestedProgressFieldsRoundTrip() throws Exception {
        var offeringWire = HexFormat.of().parseHex("10017208380258077a020102");
        var offering = PlayerOfferingRsp.parseFrom(offeringWire);
        assertEquals(1, offering.getRetcode());
        assertEquals(2, offering.getOfferingData().getOfferingId());
        assertEquals(7, offering.getOfferingData().getLevel());
        assertEquals(List.of(1, 2), offering.getOfferingData().getTakenLevelRewardListList());
        assertArrayEquals(offeringWire, offering.toByteArray());
        var rewardWire = HexFormat.of().parseHex("2a04087b1001380158026001");
        var reward = TakeOfferingLevelRewardRsp.parseFrom(rewardWire);
        assertEquals(123, reward.getItemList(0).getItemId());
        assertEquals(1, reward.getTakeLevel());
        assertEquals(2, reward.getOfferingId());
        assertEquals(1, reward.getRetcode());
        assertArrayEquals(rewardWire, reward.toByteArray());
    }

    @Test
    void unknownOpcodesAreNeverSniffedAsOfferingRequests() {
        assertFalse(OfferingHelper.tryHandleUnknownOfferingReq(player, 0, new byte[] {8, 2}));
        assertFalse(OfferingHelper.tryHandleUnknownOfferingReq(player, 30000, new byte[] {24, 2}));
        assertEquals(0, player.inventory.payments);
    }

    private PlayerOfferingRsp offer(String hex) throws Exception {
        player.sent.clear();
        byte[] header = new byte[] {8, 42};
        new GameServerPacketHandler(PacketHandler.class)
                .handle(session, 148, header, HexFormat.of().parseHex(hex));
        var packet =
                player.sent.stream().filter(p -> p.getOpcode() == 20556).findFirst().orElseThrow();
        assertArrayEquals(header, packet.getHeader());
        return PlayerOfferingRsp.parseFrom(packet.getData());
    }

    private TakeOfferingLevelRewardRsp reward(String hex) throws Exception {
        player.sent.clear();
        new GameServerPacketHandler(PacketHandler.class)
                .handle(session, 26724, new byte[0], HexFormat.of().parseHex(hex));
        var packet =
                player.sent.stream().filter(p -> p.getOpcode() == 24799).findFirst().orElseThrow();
        return TakeOfferingLevelRewardRsp.parseFrom(packet.getData());
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> cache(String name) throws Exception {
        Field field = OfferingHelper.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(null);
    }

    private static Field loaded() throws Exception {
        var field = OfferingHelper.class.getDeclaredField("levelsLoaded");
        field.setAccessible(true);
        return field;
    }

    private static void config(String json) throws Exception {
        var parser = OfferingHelper.class.getDeclaredMethod("parseLevelConfigs", JsonArray.class);
        parser.setAccessible(true);
        var rows = (Map<?, ?>) parser.invoke(null, JsonParser.parseString(json).getAsJsonArray());
        cache("LEVEL_ROWS").clear();
        cache("LEVEL_ROWS").putAll(rows);
        loaded().setBoolean(null, true);
    }

    private static final class RecordingPlayer extends Player {
        private final List<BasePacket> sent = new CopyOnWriteArrayList<>();
        private final RecordingInventory inventory = new RecordingInventory(this);
        private int saves;
        private Map<Integer, Integer> lastSavedVariables;
        private Map<Integer, Integer> lastSavedProperties;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public void save() {
            saves++;
            lastSavedVariables = new HashMap<>(getQuestGlobalVariables());
            lastSavedProperties = new HashMap<>(getProperties());
        }

        @Override
        public void sendPacket(BasePacket packet) {
            sent.add(packet);
        }
    }

    private static final class RecordingInventory extends Inventory {
        private final List<GameItem> grants = new ArrayList<>();
        private boolean rejectPayment;
        private boolean rejectGrant;
        private int payments;

        private RecordingInventory(Player player) {
            super(player);
        }

        private void stock(int id, int count) {
            getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(new GameItem(id, count));
        }

        @Override
        public synchronized boolean removeItem(GameItem item, int count) {
            if (item == null || count <= 0 || item.getCount() < count) return false;
            item.setCount(item.getCount() - count);
            return true;
        }

        @Override
        public synchronized boolean payItems(
                Iterable<emu.grasscutter.data.common.ItemParamData> items,
                int quantity,
                ActionReason reason) {
            payments++;
            return !rejectPayment && super.payItems(items, quantity, reason);
        }

        @Override
        public synchronized InventoryAddResult addItems(
                Collection<GameItem> items,
                ActionReason reason,
                InventoryAddPolicy policy,
                java.util.function.BooleanSupplier authorize,
                Runnable confirmed) {
            if (rejectGrant)
                return InventoryAddResult.rejected(items, InventoryAddResult.Status.CAPACITY_FULL);
            if (!authorize.getAsBoolean())
                return InventoryAddResult.rejected(
                        items, InventoryAddResult.Status.AUTHORIZATION_FAILED);
            confirmed.run();
            return addItems(items, reason, policy);
        }

        @Override
        public synchronized InventoryAddResult addItems(
                Collection<GameItem> items, ActionReason reason, InventoryAddPolicy policy) {
            if (rejectGrant)
                return InventoryAddResult.rejected(items, InventoryAddResult.Status.CAPACITY_FULL);
            grants.addAll(items);
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
