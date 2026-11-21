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
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerOfferingRspOuterClass.PlayerOfferingRsp;
import emu.grasscutter.net.proto.TakeOfferingLevelRewardRspOuterClass.TakeOfferingLevelRewardRsp;
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
    private byte[] previousState;
    private RecordingPlayer player;
    private GameSession session;

    @BeforeEach
    void setup() throws Exception {
        previousState = Files.exists(STATE) ? Files.readAllBytes(STATE) : null;
        previousRows = new HashMap<>(cache("LEVEL_ROWS"));
        previousLoaded = loaded().getBoolean(null);
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
                "consumeItemConfigVec:[{id:304,count:25}],isAutoTakeReward:true",
                "consumeItemConfigVec:[{id:304,count:25}],condVec:[{actionType:'OFFERING_ACTION_NOTIFY_GROUP',param:'133301220'}]"
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

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public void save() {
            saves++;
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
