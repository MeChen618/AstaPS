package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;
import dev.morphia.Morphia;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.scene.MapLayerData;
import emu.grasscutter.data.excels.scene.MapLayerGroupData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.PlayerProgress;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.QuestSystem;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.MapLayerInfo._MapLayerInfo;
import emu.grasscutter.net.proto.PlayerWorldSceneInfoListNotifyOuterClass.PlayerWorldSceneInfoListNotify;
import emu.grasscutter.server.packet.send.PacketPlayerWorldSceneInfoListNotify;
import emu.grasscutter.utils.JsonUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class ExecSetMapLayerUnlockStateTest {
    private static final int WORLD_SCENE = 3;
    private static final int FIRST_LAYER = 991821;
    private static final int SECOND_LAYER = 991822;
    private static final String LAYERS = FIRST_LAYER + ";" + SECOND_LAYER;
    private SceneData previousScene;
    private MapLayerData previousFirstLayer;
    private MapLayerData previousSecondLayer;
    private MapLayerGroupData previousGroup;
    private RecordingPlayer player;
    private GameQuest quest;
    private QuestSystem questSystem;

    @BeforeEach
    void setUp() {
        previousScene = GameData.getSceneDataMap().put(WORLD_SCENE, new SceneData());
        previousFirstLayer = GameData.getMapLayerDataMap().put(FIRST_LAYER, new MapLayerData());
        previousSecondLayer = GameData.getMapLayerDataMap().put(SECOND_LAYER, new MapLayerData());
        previousGroup = GameData.getMapLayerGroupDataMap().put(FIRST_LAYER, new MapLayerGroupData());
        player = new RecordingPlayer();
        var data =
                JsonUtils.decode(
                        "{subId:991821,mainId:99182,finishCond:[],failCond:[]}", QuestData.class);
        quest =
                new GameQuest(null, data) {
                    @Override
                    public Player getOwner() {
                        return player;
                    }
                };
        questSystem = new QuestSystem(null);
    }

    @AfterEach
    void restore() {
        var scenes = GameData.getSceneDataMap();
        var layers = GameData.getMapLayerDataMap();
        var groups = GameData.getMapLayerGroupDataMap();
        if (previousScene == null) scenes.remove(WORLD_SCENE);
        else scenes.put(WORLD_SCENE, previousScene);
        if (previousFirstLayer == null) layers.remove(FIRST_LAYER);
        else layers.put(FIRST_LAYER, previousFirstLayer);
        if (previousSecondLayer == null) layers.remove(SECOND_LAYER);
        else layers.put(SECOND_LAYER, previousSecondLayer);
        if (previousGroup == null) groups.remove(FIRST_LAYER);
        else groups.put(FIRST_LAYER, previousGroup);
    }

    @Test
    void dispatchSavesTheChangedBatchBeforeOneWorldSnapshot() throws Exception {
        var initial = mapLayers(new PacketPlayerWorldSceneInfoListNotify(player));
        var allLayers = Set.copyOf(initial.getUnlockMapLayerListList());
        var groups = Set.copyOf(initial.getUnlockMapLayerGroupListList());
        assertTrue(allLayers.containsAll(Set.of(FIRST_LAYER, SECOND_LAYER)));
        assertTrue(groups.contains(FIRST_LAYER));

        dispatch(LAYERS + ";" + FIRST_LAYER, "0");
        assertEquals(
                Set.of(FIRST_LAYER, SECOND_LAYER), player.getPlayerProgress().getLockedMapLayerIds());
        assertEquals(player.getPlayerProgress().getLockedMapLayerIds(), player.savedLocks);
        assertEquals(List.of("save", "send"), player.events);
        assertInstanceOf(PacketPlayerWorldSceneInfoListNotify.class, player.packets.getFirst());
        var locked = mapLayers(player.packets.getFirst());
        var remainingLayers = new HashSet<>(allLayers);
        remainingLayers.removeAll(Set.of(FIRST_LAYER, SECOND_LAYER));
        assertEquals(remainingLayers, Set.copyOf(locked.getUnlockMapLayerListList()));
        assertEquals(groups, Set.copyOf(locked.getUnlockMapLayerGroupListList()));

        dispatch(LAYERS, "0");
        assertEquals(List.of("save", "send"), player.events);
        assertEquals(1, player.packets.size());

        dispatch(LAYERS, "1");
        assertTrue(player.getPlayerProgress().getLockedMapLayerIds().isEmpty());
        assertEquals(Set.of(), player.savedLocks);
        assertEquals(List.of("save", "send", "save", "send"), player.events);
        var unlocked = mapLayers(player.packets.getLast());
        assertEquals(allLayers, Set.copyOf(unlocked.getUnlockMapLayerListList()));
        assertEquals(groups, Set.copyOf(unlocked.getUnlockMapLayerGroupListList()));
    }

    @Test
    void invalidBatchLeavesExistingLocksAndNotificationsUntouched() throws Exception {
        dispatch(LAYERS, "0");
        var handler = new ExecSetMapLayerUnlockState();
        var action = action(LAYERS, "1");
        for (String layers :
                List.of(
                        FIRST_LAYER + ";bad",
                        FIRST_LAYER + ";",
                        FIRST_LAYER + ";0",
                        FIRST_LAYER + ";" + Integer.MAX_VALUE,
                        "")) {
            assertFalse(handler.execute(quest, action, layers, "1"), layers);
        }
        assertFalse(handler.execute(quest, action, LAYERS, "2"));
        assertFalse(handler.execute(quest, action, LAYERS));
        assertEquals(
                Set.of(FIRST_LAYER, SECOND_LAYER), player.getPlayerProgress().getLockedMapLayerIds());
        assertEquals(List.of("save", "send"), player.events);
        assertEquals(1, player.packets.size());
    }

    @Test
    void morphiaPreservesLocksAndReadsOlderProgressWithoutThem() throws Exception {
        dispatch(LAYERS, "0");
        // Mapping a document is local and needs no MongoDB server.
        try (var client = MongoClients.create("mongodb://127.0.0.1:1")) {
            var mapper = Morphia.createDatastore(client, "test").getMapper();
            mapper.map(PlayerProgress.class);
            var document = mapper.toDocument(player.getPlayerProgress());
            var restored = mapper.fromDocument(PlayerProgress.class, document);
            assertEquals(Set.of(FIRST_LAYER, SECOND_LAYER), restored.getLockedMapLayerIds());

            document.remove("lockedMapLayerIds");
            var oldProgress = mapper.fromDocument(PlayerProgress.class, document);
            assertTrue(oldProgress.getLockedMapLayerIds().isEmpty());
        }
    }

    private void dispatch(String layers, String state) throws Exception {
        var action = action(layers, state);
        assertEquals(QuestExec.QUEST_EXEC_SET_MAP_LAYER_UNLOCK_STATE, action.getType());
        questSystem.triggerExec(quest, action, action.getParam());
        QuestManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
    }

    private static QuestData.QuestExecParam action(String layers, String state) {
        return JsonUtils.decode(
                """
                {"type":"QUEST_EXEC_SET_MAP_LAYER_UNLOCK_STATE","param":["%s","%s"]}
                """.formatted(layers, state),
                QuestData.QuestExecParam.class);
    }

    private static _MapLayerInfo mapLayers(BasePacket packet) throws Exception {
        return PlayerWorldSceneInfoListNotify.parseFrom(packet.getData())
                .getInfoListList().stream()
                .filter(scene -> scene.getSceneId() == WORLD_SCENE)
                .findFirst()
                .orElseThrow()
                .getMapLayerInfo();
    }

    private static final class RecordingPlayer extends Player {
        private final List<String> events = new ArrayList<>();
        private final List<BasePacket> packets = new ArrayList<>();
        private Set<Integer> savedLocks;

        @Override
        public void save() {
            events.add("save");
            savedLocks = getPlayerProgress().getLockedMapLayerIds();
        }

        @Override
        public void sendPacket(BasePacket packet) {
            events.add("send");
            packets.add(packet);
        }
    }
}
