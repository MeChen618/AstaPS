package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.MongoClients;
import dev.morphia.Morphia;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.PlayerProgress;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestSystem;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.exec.ExecModifyDungeonExitPoint;
import emu.grasscutter.game.tower.TowerManager;
import emu.grasscutter.game.world.TestWorld;
import emu.grasscutter.game.world.TestWorld.TestScene;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.utils.JsonUtils;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class QuestDungeonExitTest {
    private static final int DUNGEON = 991811;
    private static final int OTHER_DUNGEON = 991812;
    private static final int DUNGEON_SCENE = 9807;
    private static final int ORIGIN = 9805;
    private static final int DESTINATION = 9806;
    private static final int ENTRY = 101;
    private static final int EXIT = 102;
    private static final int NEXT_EXIT = 103;
    private RecordingPlayer player;
    private TestWorld world;
    private QuestSystem quests;
    private DungeonSystem dungeons;

    @BeforeAll
    static void initScripts() throws Exception {
        ScriptLoader.init();
    }

    @BeforeEach
    void setup() {
        for (int scene : new int[] {ORIGIN, DESTINATION}) {
            GameData.getSceneDataMap().put(scene, TestWorld.sceneData(scene, SceneType.SCENE_WORLD));
        }
        for (int dungeon : new int[] {DUNGEON, OTHER_DUNGEON}) {
            GameData.getDungeonDataMap().put(
                    dungeon,
                    JsonUtils.decode("{id:%d,sceneId:%d}".formatted(dungeon, DUNGEON_SCENE), DungeonData.class));
        }
        putPoint(ORIGIN, ENTRY, "DungeonEntry", 10);
        putPoint(DESTINATION, EXIT, "DungeonExit", 20);
        putPoint(DESTINATION, NEXT_EXIT, "DungeonExit", 30);

        player = new RecordingPlayer();
        world = TestWorld.create(player);
        world.transferResult = true;
        quests = new QuestSystem(null);
        dungeons = new DungeonSystem(null);
    }

    @AfterEach
    void restore() {
        GameData.getDungeonDataMap().remove(DUNGEON);
        GameData.getDungeonDataMap().remove(OTHER_DUNGEON);
        GameData.getSceneDataMap().remove(ORIGIN);
        GameData.getSceneDataMap().remove(DESTINATION);
        GameData.getScenePointEntryMap().remove((ORIGIN << 16) + ENTRY);
        GameData.getScenePointEntryMap().remove((DESTINATION << 16) + EXIT);
        GameData.getScenePointEntryMap().remove((DESTINATION << 16) + NEXT_EXIT);
    }

    @Test
    void finishActionBeforeEntryAndLaterReplacementChooseTheConfiguredWorldPositions() throws Exception {
        assertNull(player.getScene());
        triggerAction(player, DUNGEON, DESTINATION, EXIT);
        triggerAction(player, DUNGEON, DESTINATION, EXIT);
        assertEquals(1, player.saves);

        var scene = enterDungeon(DUNGEON);
        assertReturn(DESTINATION, 20, leave(player));
        triggerAction(player, DUNGEON, DESTINATION, NEXT_EXIT);
        assertReturn(DESTINATION, 30, leave(player));
        assertEquals(2, player.saves);
        assertEquals(ORIGIN, scene.getPrevScene());
        assertEquals(ENTRY, scene.getPrevScenePoint());
    }

    @Test
    void anotherDungeonAndAGuestUseTheOrdinaryReturnPoint() throws Exception {
        triggerAction(player, DUNGEON, DESTINATION, EXIT);
        enterDungeon(OTHER_DUNGEON);
        assertReturn(ORIGIN, 10, leave(player));

        var scene = enterDungeon(DUNGEON);
        var guest = new RecordingPlayer();
        triggerAction(guest, DUNGEON, DESTINATION, NEXT_EXIT);
        guest.setWorld(world);
        placePlayerInDungeon(guest, scene);
        assertReturn(ORIGIN, 10, leave(guest));
        assertReturn(DESTINATION, 20, leave(player));
    }

    @Test
    void invalidActionsKeepThePreviousOverrideAndAMissingExitFallsBack() throws Exception {
        triggerAction(player, DUNGEON, DESTINATION, EXIT);
        var handler = new ExecModifyDungeonExitPoint();
        var quest = questFor(player);
        assertFalse(handler.execute(quest, null, "not-an-id", "" + DESTINATION, "" + EXIT));
        assertFalse(handler.execute(quest, null, "" + DUNGEON, "" + DESTINATION));
        assertFalse(DungeonSystem.setQuestDungeonExit(player, DUNGEON, ORIGIN, ENTRY));
        assertFalse(DungeonSystem.setQuestDungeonExit(player, DUNGEON, DESTINATION, 65536 + EXIT));
        assertFalse(DungeonSystem.setQuestDungeonExit(player, Integer.MAX_VALUE, DESTINATION, EXIT));
        assertEquals(EXIT, player.getPlayerProgress().getQuestDungeonExit(DUNGEON).getPointId());
        assertEquals(1, player.saves);

        GameData.getScenePointEntryMap().remove((DESTINATION << 16) + EXIT);
        enterDungeon(DUNGEON);
        assertReturn(ORIGIN, 10, leave(player));
    }

    @Test
    void morphiaRoundTripsTheExitAndReadsOlderProgressWithoutIt() {
        // The mapper encodes and decodes documents locally.
        try (var client = MongoClients.create("mongodb://127.0.0.1:1")) {
            var mapper = Morphia.createDatastore(client, "test").getMapper();
            mapper.map(PlayerProgress.class, PlayerProgress.DungeonExitPoint.class);
            triggerAction(player, DUNGEON, DESTINATION, EXIT);
            var document = mapper.toDocument(player.getPlayerProgress());
            var restored = mapper.fromDocument(PlayerProgress.class, document);
            assertEquals(DESTINATION, restored.getQuestDungeonExit(DUNGEON).getSceneId());
            assertEquals(EXIT, restored.getQuestDungeonExit(DUNGEON).getPointId());

            document.remove("questDungeonExits");
            var oldProgress = mapper.fromDocument(PlayerProgress.class, document);
            assertNull(oldProgress.getQuestDungeonExit(DUNGEON));
            assertTrue(oldProgress.setQuestDungeonExit(DUNGEON, DESTINATION, NEXT_EXIT));
        }
    }

    private void triggerAction(Player who, int dungeon, int scene, int point) {
        var action = JsonUtils.decode(
                """
                {"type":"QUEST_EXEC_MODIFY_DUNGEON_EXIT_POINT","param":["%d","%d","%d"]}
                """.formatted(dungeon, scene, point), QuestData.QuestExecParam.class);
        assertEquals(QuestExec.QUEST_EXEC_MODIFY_DUNGEON_EXIT_POINT, action.getType());
        quests.triggerExec(questFor(who), action, action.getParam());
    }

    private static GameQuest questFor(Player owner) {
        var data = JsonUtils.decode("{subId:1,mainId:1,finishCond:[],failCond:[]}", QuestData.class);
        return new GameQuest(null, data) {
            @Override
            public Player getOwner() {
                return owner;
            }
        };
    }

    private TestScene enterDungeon(int dungeon) {
        var scene = new TestScene(world, DUNGEON_SCENE, SceneType.SCENE_DUNGEON);
        scene.setPrevScene(ORIGIN);
        scene.setPrevScenePoint(ENTRY);
        var manager = new DungeonManager(scene, GameData.getDungeonDataMap().get(dungeon));
        manager.endDungeon(BaseDungeonResult.DungeonEndReason.COMPLETED);
        scene.setDungeonManager(manager);
        placePlayerInDungeon(player, scene);
        return scene;
    }

    private static void placePlayerInDungeon(Player player, TestScene scene) {
        scene.getPlayers().add(player);
        player.setScene(scene);
        player.setSceneId(scene.getId());
        player.getTeamManager().getActiveTeam().clear();
        player.getTeamManager().getActiveTeam().add(new EntityAvatar(scene, new Avatar()));
    }

    private TeleportProperties leave(Player player) throws Exception {
        world.transfers.clear();
        dungeons.exitDungeon(player);
        player.getQueuedTeleport().get(5, TimeUnit.SECONDS);
        assertEquals(1, world.transfers.size());
        return world.transfers.getFirst();
    }

    private static void putPoint(int scene, int id, String type, int x) {
        var point = JsonUtils.decode(
                "{id:%d,$type:'%s',tranPos:{x:%d,y:11,z:12}}".formatted(id, type, x), PointData.class);
        GameData.getScenePointEntryMap().put((scene << 16) + id, new ScenePointEntry(scene, point));
    }

    private static void assertReturn(int scene, int x, TeleportProperties transfer) {
        assertEquals(scene, transfer.getSceneId());
        assertEquals(x, transfer.getTeleportTo().getX());
        assertEquals(11, transfer.getTeleportTo().getY());
        assertEquals(12, transfer.getTeleportTo().getZ());
    }

    private static final class RecordingPlayer extends Player {
        private final TeamManager team = new TeamManager(this);
        private final TowerManager tower = new TowerManager(this);
        int saves;

        @Override
        public TeamManager getTeamManager() {
            return team;
        }

        @Override
        public TowerManager getTowerManager() {
            return tower;
        }

        @Override
        public void save() {
            saves++;
        }
    }
}
