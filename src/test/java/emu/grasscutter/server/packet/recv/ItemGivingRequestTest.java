package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.giving.GivingData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.QuestSystem;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.game.systems.TestPlayers;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ItemGivingReqOuterClass.ItemGivingReq;
import emu.grasscutter.net.proto.ItemGivingRspOuterClass.ItemGivingRsp;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class ItemGivingRequestTest {
    private static final Gson GSON = new Gson();
    private static final int GIVING = 1902401;
    private static final int FIRST_ITEM = 100052;
    private static final int SECOND_ITEM = 100031;

    private final Map<Integer, ItemData> originalItems = new HashMap<>();
    private final List<BasePacket> sent = new ArrayList<>();
    private GivingData originalGiving;
    private TestPlayer player;
    private GameSession session;

    @BeforeEach
    void setUp() throws Exception {
        originalGiving = GameData.getGivingDataMap().put(
                GIVING,
                GSON.fromJson(
                        """
                        {
                          "Id": 1902401, "givingType": "GIVING_TYPE_QUEST",
                          "givingMethod": "GIVING_METHOD_EXACT", "isRemoveItem": true,
                          "exactItems": [{"id": 100052, "count": 1}, {"id": 100031, "count": 1}, {}]
                        }
                        """,
                        GivingData.class));
        for (int id : List.of(FIRST_ITEM, SECOND_ITEM)) {
            var data = GSON.fromJson(
                    """
                    {"id": %d, "itemType": "ITEM_MATERIAL", "stackLimit": 100}
                    """.formatted(id),
                    ItemData.class);
            originalItems.put(id, GameData.getItemDataMap().put(id, data));
        }
        var server = TestPlayers.server();
        var questSystem = GameServer.class.getDeclaredField("questSystem");
        questSystem.setAccessible(true);
        questSystem.set(server, new QuestSystem(server));
        player = new TestPlayer();
        player.setUid(991704);
        session = new GameSession(server) {
            @Override
            public void send(BasePacket packet) {
                sent.add(packet);
            }
        };
        session.setState(GameSession.SessionState.ACTIVE);
        session.setPlayer(player);
    }

    @AfterEach
    void restore() throws Exception {
        awaitEvents();
        if (originalGiving == null) GameData.getGivingDataMap().remove(GIVING);
        else GameData.getGivingDataMap().put(GIVING, originalGiving);
        originalItems.forEach((id, data) -> {
            if (data == null) GameData.getItemDataMap().remove(id.intValue());
            else GameData.getItemDataMap().put(id.intValue(), data);
        });
    }

    @Test
    void completesTheActiveAuthoredStepAndPaysOnlyOnce() throws Exception {
        var quest = activeQuest(false);
        var first = player.inventory.stock(FIRST_ITEM, 3);
        var second = player.inventory.stock(SECOND_ITEM, 3);
        var request = exactRequest();

        var reply = give(request);
        assertEquals(0, reply.getRetcode());
        assertEquals(GIVING, reply.getGivingId());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertCounts(first, 2, second, 2);

        give(request);
        assertCounts(first, 2, second, 2);
    }

    @Test
    void incompleteOrUnavailablePaymentKeepsTheQuestAndMaterials() throws Exception {
        var quest = activeQuest(false);
        var first = player.inventory.stock(FIRST_ITEM, 3);
        var second = player.inventory.stock(SECOND_ITEM, 0);
        assertNotEquals(0, give(exactRequest()).getRetcode());
        assertCounts(first, 3, second, 0);

        second.setCount(3);
        var incomplete = exactRequest().clearItemParamList().addItemParamList(item(FIRST_ITEM, 1));
        assertNotEquals(0, give(incomplete).getRetcode());
        assertCounts(first, 3, second, 3);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertArrayEquals(new int[] {0}, quest.getFinishProgressList());
    }

    @Test
    void aShowOnlyObjectiveKeepsTheItemsWhenBothRequestRepresentationsAgree() throws Exception {
        var quest = activeQuest(false);
        var first = player.inventory.stock(FIRST_ITEM, 3);
        var second = player.inventory.stock(SECOND_ITEM, 3);
        GameData.getGivingDataMap().get(GIVING).setRemoveItem(false);
        var request = exactRequest()
                .putItemGuidCountMap(first.getGuid(), 1)
                .putItemGuidCountMap(second.getGuid(), 1);

        assertEquals(0, give(request).getRetcode());
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertCounts(first, 3, second, 3);
    }

    @Test
    void checksBothRequestRepresentationsAndDoesNotPayAgainWhileAnotherConditionIsPending()
            throws Exception {
        var quest = activeQuest(true);
        var first = player.inventory.stock(FIRST_ITEM, 3);
        var second = player.inventory.stock(SECOND_ITEM, 3);
        var wrong = exactRequest()
                .putItemGuidCountMap(first.getGuid(), 2)
                .putItemGuidCountMap(second.getGuid(), 1);
        assertNotEquals(0, give(wrong).getRetcode());
        assertCounts(first, 3, second, 3);

        var guidOnly = wrong.clearItemParamList().putItemGuidCountMap(first.getGuid(), 1);
        assertEquals(0, give(guidOnly).getRetcode());
        assertCounts(first, 2, second, 2);
        assertEquals(QuestState.QUEST_STATE_UNFINISHED, quest.getState());
        assertArrayEquals(new int[] {1, 0}, quest.getFinishProgressList());

        give(guidOnly);
        assertCounts(first, 2, second, 2);
    }

    private GameQuest activeQuest(boolean hasPlotCondition) {
        var data = GSON.fromJson(
                """
                {
                  "subId": 1902406, "mainId": 19024, "finishCondComb": "LOGIC_AND",
                  "finishCond": [
                    {"type": "QUEST_CONTENT_FINISH_ITEM_GIVING", "param": [1902401, 0]}
                    %s
                  ],
                  "failCond": []
                }
                """.formatted(hasPlotCondition
                        ? ",{\"type\":\"QUEST_CONTENT_FINISH_PLOT\",\"param\":[1902406,0]}"
                        : ""),
                QuestData.class);
        var main = new GameMainQuest() {
            private final Map<Integer, GameQuest> children = new HashMap<>();

            @Override
            public Player getOwner() {
                return player;
            }

            @Override
            public Map<Integer, GameQuest> getChildQuests() {
                return children;
            }

            @Override
            public List<GameQuest> getActiveQuests() {
                return children.values().stream()
                        .filter(quest -> quest.getState() == QuestState.QUEST_STATE_UNFINISHED)
                        .toList();
            }

            @Override
            public ParentQuestState getState() {
                return ParentQuestState.PARENT_QUEST_STATE_NONE;
            }

            @Override
            public void save() {}
        };
        main.setAddedByCommand(true);
        var quest = new GameQuest(main, data) {
            @Override
            public void finish() {
                setState(QuestState.QUEST_STATE_FINISHED);
            }
        };
        quest.setState(QuestState.QUEST_STATE_UNFINISHED);
        main.getChildQuests().put(data.getId(), quest);
        player.getQuestManager().getMainQuests().put(data.getMainId(), main);
        return quest;
    }

    private static ItemGivingReq.Builder exactRequest() {
        return ItemGivingReq.newBuilder()
                .setGivingId(GIVING)
                .addItemParamList(item(FIRST_ITEM, 1))
                .addItemParamList(item(SECOND_ITEM, 1));
    }

    private static ItemParam item(int id, int count) {
        return ItemParam.newBuilder().setItemId(id).setCount(count).build();
    }

    private ItemGivingRsp give(ItemGivingReq.Builder request) throws Exception {
        sent.clear();
        new HandlerItemGivingReq().handle(session, new byte[0], request.build().toByteArray());
        awaitEvents();
        var packet = sent.stream()
                .filter(output -> output.getOpcode() == PacketOpcodes.ItemGivingRsp)
                .findFirst()
                .orElseThrow();
        return ItemGivingRsp.parseFrom(packet.getData());
    }

    private static void awaitEvents() throws Exception {
        QuestManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
    }

    private static void assertCounts(GameItem first, int expectedFirst, GameItem second, int expectedSecond) {
        assertEquals(expectedFirst, first.getCount());
        assertEquals(expectedSecond, second.getCount());
    }

    private static final class TestPlayer extends Player {
        private final TestInventory inventory = new TestInventory(this);

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public void save() {}
    }

    private static final class TestInventory extends Inventory {
        TestInventory(Player player) {
            super(player);
        }

        GameItem stock(int id, int count) {
            var item = new GameItem(id, count);
            item.setOwner(getPlayer());
            getItems().put(item.getGuid(), item);
            getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(item);
            return item;
        }

        @Override
        public synchronized boolean removeItem(GameItem item, int count) {
            if (item == null || count <= 0 || item.getCount() < count) return false;
            item.setCount(item.getCount() - count);
            return true;
        }
    }
}
