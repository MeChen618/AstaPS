package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.expedition.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionGetRewardReqOuterClass.AvatarExpeditionGetRewardReq;
import emu.grasscutter.net.proto.AvatarExpeditionGetRewardRspOuterClass.AvatarExpeditionGetRewardRsp;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import sun.misc.Unsafe;

import java.util.*;

final class ExpeditionRewardClaimTest {
    private static final int ITEM = 991340;
    private static final int SPOT = 991341;
    private RecordingPlayer player;
    private CaptureSession session;
    private ItemData oldItem;
    private ExpeditionSystem expeditions;

    @BeforeEach
    void setup() throws Exception {
        var gson = new Gson();
        oldItem =
                GameData.getItemDataMap()
                        .put(
                                ITEM,
                                gson.fromJson(
                                        "{\"id\":991340,\"itemType\":\"ITEM_MATERIAL\","
                                            + "\"materialType\":\"MATERIAL_NONE\",\"stackLimit\":9999}",
                                        ItemData.class));
        expeditions =
                new ExpeditionSystem(null) {
                    @Override
                    public synchronized void load() {}
                };
        expeditions
                .getExpeditionRewardDataList()
                .put(
                        SPOT,
                        List.of(
                                gson.fromJson(
                                        "{\"hourTime\":4,\"expeditionRewardData\":["
                                            + "{\"itemId\":991340,\"minCount\":2,\"maxCount\":2}]}",
                                        ExpeditionRewardDataList.class)));
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var server = (GameServer) ((Unsafe) field.get(null)).allocateInstance(GameServer.class);
        var systemField = GameServer.class.getDeclaredField("expeditionSystem");
        systemField.setAccessible(true);
        systemField.set(server, expeditions);
        player = new RecordingPlayer();
        player.server = server;
        session = new CaptureSession(player);
        addFinished(42);
    }

    @AfterEach
    void restore() {
        if (oldItem == null) GameData.getItemDataMap().remove(ITEM);
        else GameData.getItemDataMap().put(ITEM, oldItem);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void inventoryRefusalPreservesEveryClaimAndRetryPaysOnlyOnce(boolean claimAll)
            throws Exception {
        if (claimAll) addFinished(43);
        var expectedGuids = Set.copyOf(player.getExpeditionInfo().keySet());
        player.inventory.refuse = true;
        var failed = claim(claimAll);
        assertNotEquals(0, failed.getRetcode());
        assertTrue(failed.getExpeditionRewardListList().isEmpty());
        assertEquals(expectedGuids, failed.getExpeditionInfoMapMap().keySet());
        assertEquals(expectedGuids, player.getExpeditionInfo().keySet());
        assertTrue(player.getExpeditionInfo().values().stream().allMatch(e -> e.getState() == 2));
        assertTrue(player.inventory.grants.isEmpty());

        player.inventory.refuse = false;
        var succeeded = claim(claimAll);
        assertEquals(0, succeeded.getRetcode());
        assertEquals(expectedGuids.size(), succeeded.getExpeditionRewardListCount());
        assertTrue(player.getExpeditionInfo().isEmpty());
        assertEquals(1, player.inventory.grants.size());
        assertEquals(
                6 * expectedGuids.size(),
                player.inventory.grants.getFirst().stream().mapToInt(GameItem::getCount).sum());

        var duplicate = claim(claimAll);
        assertEquals(0, duplicate.getRetcode());
        assertTrue(duplicate.getExpeditionRewardListList().isEmpty());
        assertEquals(1, player.inventory.grants.size());
    }

    @Test
    void singleClaimLeavesOtherFinishedAndRunningExpeditionsAlone() throws Exception {
        addFinished(43);
        player.addExpeditionInfo(44, SPOT, 4, Utils.getCurrentSeconds());
        var reply = claim(false);
        assertEquals(0, reply.getRetcode());
        assertEquals(42, reply.getExpeditionRewardList(0).getBasicInfo().getAvatarGuid());
        assertEquals(Set.of(43L, 44L), reply.getExpeditionInfoMapMap().keySet());
        assertEquals(2, player.getExpeditionInfo(43).getState());
        assertEquals(1, player.getExpeditionInfo(44).getState());
    }

    @Test
    void claimAllOnlyCollectsFinishedExpeditions() throws Exception {
        addFinished(43);
        player.addExpeditionInfo(44, SPOT, 4, Utils.getCurrentSeconds());
        var reply = claim(true);
        assertEquals(0, reply.getRetcode());
        assertEquals(2, reply.getExpeditionRewardListCount());
        assertEquals(Set.of(44L), player.getExpeditionInfo().keySet());
        assertEquals(1, player.getExpeditionInfo(44).getState());
    }

    @Test
    void elapsedExpeditionRemainsFinishedWhenInventoryRefuses() throws Exception {
        player.getExpeditionInfo(42).setState(1);
        player.inventory.refuse = true;
        var reply = claim(false);
        assertNotEquals(0, reply.getRetcode());
        assertEquals(2, player.getExpeditionInfo(42).getState());
        assertEquals(2, reply.getExpeditionInfoMapOrThrow(42).getStateValue());
    }

    @Test
    void missingRewardConfigurationDoesNotConsumeAnyClaimInTheBatch() throws Exception {
        addFinished(43);
        player.getExpeditionInfo(43).setExpId(SPOT + 1);
        var reply = claim(true);
        assertNotEquals(0, reply.getRetcode());
        assertTrue(reply.getExpeditionRewardListList().isEmpty());
        assertEquals(Set.of(42L, 43L), player.getExpeditionInfo().keySet());
        assertTrue(player.inventory.grants.isEmpty());
    }

    private void addFinished(long guid) {
        player.addExpeditionInfo(guid, SPOT, 4, Utils.getCurrentSeconds() - 4 * 3600 - 1);
        player.getExpeditionInfo(guid).setState(2);
    }

    private AvatarExpeditionGetRewardRsp claim(boolean all) throws Exception {
        session.sent.clear();
        new HandlerAvatarExpeditionGetRewardReq()
                .handle(
                        session,
                        PacketHead.newBuilder().setClientSequenceId(123).build().toByteArray(),
                        AvatarExpeditionGetRewardReq.newBuilder()
                                .setAvatarGuid(42)
                                .setIsClaimAll(all)
                                .build()
                                .toByteArray());
        var packet =
                session.sent.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.AvatarExpeditionGetRewardRsp)
                        .findFirst()
                        .orElseThrow();
        assertEquals(123, PacketHead.parseFrom(packet.getHeader()).getClientSequenceId());
        return AvatarExpeditionGetRewardRsp.parseFrom(packet.getData());
    }

    private static final class RecordingPlayer extends Player {
        final RecordingInventory inventory = new RecordingInventory(this);
        GameServer server;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public void save() {}
    }

    private static final class RecordingInventory extends Inventory {
        final List<List<GameItem>> grants = new ArrayList<>();
        boolean refuse;

        RecordingInventory(Player player) {
            super(player);
        }

        @Override
        public InventoryAddResult addItems(Collection<GameItem> items, ActionReason reason) {
            assertEquals(ActionReason.ExpeditionReward, reason);
            if (refuse)
                return InventoryAddResult.rejected(items, InventoryAddResult.Status.CAPACITY_FULL);
            grants.add(List.copyOf(items));
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

    private static final class CaptureSession extends GameSession {
        final List<BasePacket> sent = new ArrayList<>();
        final Player player;

        CaptureSession(Player player) {
            super(null);
            this.player = player;
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public void send(BasePacket packet) {
            sent.add(packet);
        }
    }
}
