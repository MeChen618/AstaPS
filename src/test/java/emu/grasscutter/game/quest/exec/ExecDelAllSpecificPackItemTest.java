package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.QuestSystem;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.StoreItemDelNotifyOuterClass.StoreItemDelNotify;
import emu.grasscutter.server.packet.send.PacketStoreItemDelNotify;
import emu.grasscutter.utils.JsonUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class ExecDelAllSpecificPackItemTest {
    private static final int FIRST = 991901;
    private static final int SECOND = 991902;
    private static final int UNRELATED = 991903;
    private static final int ABSENT = 991904;
    private static final int WEAPON = 991905;
    private static final int VIRTUAL = 991906;
    private static final int UNKNOWN = 991907;

    private final Map<Integer, ItemData> previousItems = new HashMap<>();
    private RecordingPlayer player;
    private GameQuest quest;
    private QuestData.QuestExecParam action;

    @BeforeEach
    void setUp() {
        for (int id : List.of(FIRST, SECOND, UNRELATED, ABSENT)) {
            register(id, ItemType.ITEM_MATERIAL);
        }
        register(WEAPON, ItemType.ITEM_WEAPON);
        register(VIRTUAL, ItemType.ITEM_VIRTUAL);

        player = new RecordingPlayer();
        player.setUid(991908);
        var questData =
                JsonUtils.decode("{subId:991909,mainId:99191,finishCond:[],failCond:[]}", QuestData.class);
        quest = new GameQuest(null, questData) {
            @Override
            public Player getOwner() {
                return player;
            }
        };
        action = new QuestData.QuestExecParam();
        action.setType(QuestExec.QUEST_EXEC_DEL_ALL_SPECIFIC_PACK_ITEM);
    }

    @AfterEach
    void restoreItems() {
        var items = GameData.getItemDataMap();
        previousItems.forEach(
                (id, previous) -> {
                    if (previous == null) items.remove(id);
                    else items.put(id, previous);
                });
    }

    @Test
    void registeredActionClearsEveryListedStackAfterRollback() throws Exception {
        var firstStack = store(FIRST, 2);
        var legacyDuplicate = store(FIRST, 8);
        var secondStack = store(SECOND, 3);
        var unrelated = store(UNRELATED, 12);
        var inventory = player.getInventory();

        // A preceding authored rollback can reset the quest state before the cleanup executes.
        quest.setState(QuestState.QUEST_STATE_UNSTARTED);
        new QuestSystem(null).triggerExec(quest, action, FIRST + "," + SECOND + "," + FIRST + "," + ABSENT);
        drainQuestEvents();

        var expected = Set.of(firstStack.getGuid(), legacyDuplicate.getGuid(), secondStack.getGuid());
        assertEquals(expected, player.deletedGuids());
        assertEquals(expected.size(), player.deletions.size());
        assertEquals(Set.of(unrelated.getGuid()), inventory.getItems().keySet());
        assertEquals(12, inventory.getItemCountById(UNRELATED));
        for (var removed : List.of(firstStack, legacyDuplicate, secondStack)) {
            assertEquals(0, removed.getCount());
            assertEquals(1, removed.saves);
        }

        assertTrue(new ExecDelAllSpecificPackItem().execute(quest, action, FIRST + "," + SECOND));
        assertEquals(expected.size(), player.deletions.size());
    }

    @Test
    void rejectsInvalidListsBeforeRemovingAnyValidMaterial() {
        var untouched = store(FIRST, 4);
        var handler = new ExecDelAllSpecificPackItem();
        for (var invalid : List.of(
                FIRST + ",",
                FIRST + ",invalid",
                FIRST + ":2",
                FIRST + ",0",
                FIRST + ",2147483648",
                FIRST + "," + UNKNOWN,
                FIRST + "," + VIRTUAL,
                FIRST + "," + WEAPON)) {
            assertFalse(handler.execute(quest, action, invalid), invalid);
        }
        assertFalse(handler.execute(quest, action));
        assertFalse(handler.execute(quest, action, String.valueOf(FIRST), String.valueOf(SECOND)));
        assertSame(untouched, player.getInventory().getItemByGuid(untouched.getGuid()));
        assertEquals(4, untouched.getCount());
        assertEquals(0, untouched.saves);
        assertTrue(player.deletions.isEmpty());
    }

    private void register(int id, ItemType type) {
        var data = JsonUtils.decode("{id:%d,itemType:\"%s\"}".formatted(id, type.name()), ItemData.class);
        previousItems.put(id, GameData.getItemDataMap().put(id, data));
    }

    private StoredItem store(int id, int count) {
        var item = new StoredItem(GameData.getItemDataMap().get(id), count);
        item.setOwner(player);
        var inventory = player.getInventory();
        inventory.getItems().put(item.getGuid(), item);
        inventory.getInventoryTab(item.getItemType()).onAddItem(item);
        return item;
    }

    private static void drainQuestEvents() throws Exception {
        // The first barrier waits for the action, which queues item-count conditions of its own.
        QuestManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        QuestManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
    }

    private static final class StoredItem extends GameItem {
        private int saves;

        private StoredItem(ItemData data, int count) {
            super(data, count);
        }

        @Override
        public void save() {
            saves++;
        }
    }

    private static final class RecordingPlayer extends Player {
        private final List<PacketStoreItemDelNotify> deletions = new ArrayList<>();

        @Override
        public void sendPacket(BasePacket packet) {
            if (packet instanceof PacketStoreItemDelNotify deletion) deletions.add(deletion);
        }

        private Set<Long> deletedGuids() throws Exception {
            var guids = new HashSet<Long>();
            for (var deletion : deletions) {
                guids.addAll(StoreItemDelNotify.parseFrom(deletion.getData()).getGuidListList());
            }
            return guids;
        }
    }
}
