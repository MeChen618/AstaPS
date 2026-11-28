package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.enums.DungeonSubType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.ClimateType;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.TestWorld;
import emu.grasscutter.game.world.TestWorld.TestScene;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.data.SceneBlock;
import emu.grasscutter.server.game.GameSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class DomainEntryInitializationTest {
    private static final int DOMAIN_SCENE = 991820;
    private static final int OUTSIDE_SCENE = 991821;
    private static final int HOST_UID = 991822;
    private static final int GUEST_UID = 991823;
    private static final int START_X = 31;
    private static final int INITIAL_X = -5;

    private ConfigGlobalCombat previousCombat;
    private DomainPlayer player;
    private TestWorld world;

    @BeforeAll
    static void initScripts() throws Exception {
        ScriptLoader.init();
    }

    @BeforeEach
    void createWorld() {
        previousCombat = GameData.getConfigGlobalCombat();
        player = new DomainPlayer(HOST_UID);
        world = TestWorld.create(player);
    }

    @AfterEach
    void restoreCombatConfig() {
        GameData.setConfigGlobalCombat(previousCombat);
    }

    @Test
    void waitsForScriptReadinessBeforeLoadingGroupsAndPlacingThePlayer() throws Exception {
        var scene = enter(player, START_X);
        DomainDungeonHelper.onPlayerEnterDomain(player);
        drainScriptEvents();

        assertEquals(0, scene.blockLoads);
        assertEquals(INITIAL_X, player.getPosition().getX());

        scene.scripts.complete(true);
        drainScriptEvents();

        assertTrue(scene.blockLoads > 0);
        assertEquals(START_X, player.getPosition().getX());
    }

    @Test
    void failedScriptInitializationDoesNotRebuildSceneGroups() throws Exception {
        var scene = enter(player, START_X);
        DomainDungeonHelper.onPlayerEnterDomain(player);
        scene.scripts.complete(false);
        drainScriptEvents();

        assertEquals(0, scene.blockLoads);
        assertEquals(INITIAL_X, player.getPosition().getX());
    }

    @Test
    void delayedCallbackDoesNotTouchTheDomainOrPlayerAfterLeaving() throws Exception {
        var scene = enter(player, START_X);
        DomainDungeonHelper.onPlayerEnterDomain(player);
        scene.getPlayers().remove(player);
        var outside = new TestScene(world, OUTSIDE_SCENE, SceneType.SCENE_DUNGEON);
        attach(player, outside);
        player.getPosition().setX(72);
        scene.scripts.complete(true);
        drainScriptEvents();

        assertEquals(0, scene.blockLoads);
        assertEquals(72, player.getPosition().getX());
    }

    @Test
    void sameSceneIdInAnotherRunLoadsItsOwnGroups() throws Exception {
        var first = enter(player, START_X);
        DomainDungeonHelper.onPlayerEnterDomain(player);
        first.scripts.complete(true);
        drainScriptEvents();
        assertEquals(START_X, player.getPosition().getX());
        assertTrue(first.blockLoads > 0);

        first.getPlayers().remove(player);
        var second = enter(player, 52);
        assertEquals(first.getId(), second.getId());
        assertNotSame(first.getDungeonManager(), second.getDungeonManager());
        DomainDungeonHelper.onPlayerEnterDomain(player);
        second.scripts.complete(true);
        drainScriptEvents();

        assertEquals(first.blockLoads, second.blockLoads);
        assertEquals(52, player.getPosition().getX());
    }

    @Test
    void joiningAnInitializedRunPlacesTheGuestWithoutResettingTheHostsGroups() throws Exception {
        var scene = enter(player, START_X);
        DomainDungeonHelper.onPlayerEnterDomain(player);
        scene.scripts.complete(true);
        drainScriptEvents();
        int initialLoads = scene.blockLoads;
        assertTrue(initialLoads > 0);
        player.getPosition().setX(60);

        var guest = new DomainPlayer(GUEST_UID);
        guest.setWorld(world);
        world.getPlayers().add(guest);
        attach(guest, scene);
        DomainDungeonHelper.onPlayerEnterDomain(guest);
        drainScriptEvents();

        assertEquals(initialLoads, scene.blockLoads);
        assertEquals(START_X, guest.getPosition().getX());
        assertEquals(60, player.getPosition().getX());
    }

    private DomainScene enter(Player entrant, int spawnX) {
        var scene = new DomainScene(world);
        scene.setDungeonManager(new DungeonManager(scene, new DomainData(spawnX)));
        attach(entrant, scene);
        return scene;
    }

    private static void attach(Player entrant, TestScene scene) {
        entrant.setScene(scene);
        entrant.setSceneId(scene.getId());
        scene.getPlayers().add(entrant);
    }

    /**
     * The script pool is FIFO with a fixed worker count. Once every worker holds one of these
     * barriers, all previously submitted callbacks have completed.
     */
    private static void drainScriptEvents() throws Exception {
        var pool = (ThreadPoolExecutor) SceneScriptManager.eventExecutor;
        int workers = pool.getCorePoolSize();
        var arrived = new CountDownLatch(workers);
        var release = new CountDownLatch(1);
        List<Future<?>> barriers = new ArrayList<>(workers);
        try {
            for (int worker = 0; worker < workers; worker++) {
                barriers.add(pool.submit(() -> {
                    arrived.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    return null;
                }));
            }
            assertTrue(arrived.await(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
        for (var barrier : barriers) barrier.get(5, TimeUnit.SECONDS);
    }

    private static final class DomainPlayer extends Player {
        private final TeamManager team = new TeamManager(this);

        private DomainPlayer(int uid) {
            setUid(uid);
            var session = new GameSession(null);
            session.setState(GameSession.SessionState.ACTIVE);
            session.setPlayer(this);
            getPosition().setX(INITIAL_X);
        }

        @Override
        public TeamManager getTeamManager() {
            return team;
        }

        @Override
        public synchronized void setWeather(int weatherId, ClimateType climate) {
            // The entry state fixture does not have a network transport.
        }
    }

    private static final class DomainScene extends TestScene {
        private final GatedScripts scripts;
        private int blockLoads;

        private DomainScene(TestWorld world) {
            super(world, DOMAIN_SCENE, SceneType.SCENE_DUNGEON);
            scripts = new GatedScripts(this);
        }

        @Override
        public SceneScriptManager getScriptManager() {
            return scripts == null ? super.getScriptManager() : scripts;
        }

        @Override
        public boolean loadBlock(SceneBlock block) {
            blockLoads++;
            return true;
        }
    }

    private static final class GatedScripts extends SceneScriptManager {
        private final Map<Integer, SceneBlock> blocks = Map.of(1, new SceneBlock());
        private final List<Consumer<Boolean>> waiting = new ArrayList<>();
        private volatile Boolean ready;

        private GatedScripts(DomainScene scene) {
            super(scene);
        }

        @Override
        public synchronized void whenInitialized(Consumer<Boolean> callback) {
            if (ready == null) waiting.add(callback);
            else callback.accept(ready);
        }

        @Override
        public boolean isInit() {
            return Boolean.TRUE.equals(ready);
        }

        @Override
        public boolean isInitAttempted() {
            return ready != null;
        }

        @Override
        public Map<Integer, SceneBlock> getBlocks() {
            return blocks;
        }

        private synchronized void complete(boolean result) {
            ready = result;
            waiting.forEach(callback -> callback.accept(result));
            waiting.clear();
        }
    }

    private static final class DomainData extends DungeonData {
        private final Position start;

        private DomainData(int spawnX) {
            start = new Position(spawnX, 4, 6);
        }

        @Override
        public int getSceneId() {
            return DOMAIN_SCENE;
        }

        @Override
        public DungeonSubType getSubType() {
            return DungeonSubType.DUNGEON_SUB_WEAPON;
        }

        @Override
        public Position getStartPosition() {
            return start;
        }

        @Override
        public Position getStartRotation() {
            return new Position();
        }
    }
}
