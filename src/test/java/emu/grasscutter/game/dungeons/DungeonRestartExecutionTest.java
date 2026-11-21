package emu.grasscutter.game.dungeons;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.dungeon.DungeonPassConfigData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.TestWorld;
import emu.grasscutter.game.world.TestWorld.TestScene;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.proto.DungeonRestartRspOuterClass.DungeonRestartRsp;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.game.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.*;

@ExtendWith(ServerResourceFixture.class)
final class DungeonRestartExecutionTest {
    private static final int DUNGEON = 991761;
    private static final int SCENE = 991762;
    private static final int PASS = 991763;
    private SceneData previousScene;
    private DungeonPassConfigData previousPass;
    private Map<Integer, Long> domainEnterTimes;
    private Long previousEnterTime;
    private TestDungeonData data;
    private Player player;
    private TestWorld world;
    private TestScene scene;
    private DungeonManager manager;
    private CaptureSession session;

    @BeforeAll
    static void initScripts() throws Exception {
        ScriptLoader.init();
    }

    @BeforeEach
    void setup() throws Exception {
        data =
                new Gson()
                        .fromJson(
                                "{id:"
                                        + DUNGEON
                                        + ",sceneId:"
                                        + SCENE
                                        + ",passCond:"
                                        + PASS
                                        + ",subType:'DUNGEON_SUB_WEAPON'}",
                                TestDungeonData.class);
        previousScene =
                GameData.getSceneDataMap()
                        .put(SCENE, TestWorld.sceneData(SCENE, SceneType.SCENE_DUNGEON));
        previousPass =
                GameData.getDungeonPassConfigDataMap()
                        .put(
                                PASS,
                                new Gson()
                                        .fromJson(
                                                "{id:"
                                                        + PASS
                                                        + ",logicType:'LOGIC_AND',conds:[{condType:'DUNGEON_COND_KILL_MONSTER',param:[1]}]}",
                                                DungeonPassConfigData.class));
        player = new Player();
        player.setUid(991760);
        var clock = DomainDungeonHelper.class.getDeclaredField("LAST_ENTER_MS");
        clock.setAccessible(true);
        @SuppressWarnings("unchecked")
        var times = (Map<Integer, Long>) clock.get(null);
        domainEnterTimes = times;
        // 本组只验证重开边界，跳过另有测试覆盖的异步场景组加载。
        previousEnterTime = times.put(player.getUid(), Long.MAX_VALUE);
        world = TestWorld.create(player);
        scene = new TestScene(world, SCENE, SceneType.SCENE_DUNGEON);
        scene.getPlayers().add(player);
        player.setScene(scene);
        player.setSceneId(SCENE);
        scene.setPrevScene(3);
        scene.setPrevScenePoint(57);
        manager = new DungeonManager(scene, data);
        manager.getFinishedConditions()[0] = 1;
        manager.getRewardedPlayersForClaims().add(player.getUid());
        scene.setDungeonManager(manager);
        var server = allocate(GameServer.class);
        var field = GameServer.class.getDeclaredField("dungeonSystem");
        field.setAccessible(true);
        field.set(server, new DungeonSystem(null));
        session = new CaptureSession(server);
        session.setPlayer(player);
        world.transferResult = false;
    }

    @AfterEach
    void restore() {
        if (previousEnterTime == null) domainEnterTimes.remove(player.getUid());
        else domainEnterTimes.put(player.getUid(), previousEnterTime);
        if (previousScene == null) GameData.getSceneDataMap().remove(SCENE);
        else GameData.getSceneDataMap().put(SCENE, previousScene);
        if (previousPass == null) GameData.getDungeonPassConfigDataMap().remove(PASS);
        else GameData.getDungeonPassConfigDataMap().put(PASS, previousPass);
    }

    @Test
    void deniedTransferKeepsTheClaimAndSceneAndReturnsFailure() throws Exception {
        String claim = manager.getClaimOperationId();
        var response = request(new byte[0]);

        assertNotEquals(0, response.getRetcode());
        assertEquals(DUNGEON, response.getDungeonId());
        assertEquals(57, response.getPointId());
        assertSame(scene, player.getScene());
        assertEquals(List.of(player), scene.getPlayers());
        assertTrue(manager.isFinishedSuccessfully());
        assertTrue(manager.hasClaimedDomainReward(player.getUid()));
        assertEquals(claim, manager.getClaimOperationId());
        assertEquals(1, world.transfers.size());
    }

    @Test
    void successfulRestartReturnsOriginAndCreatesANewClaimRun() throws Exception {
        String claim = manager.getClaimOperationId();
        var destination = new TestScene(world, SCENE, SceneType.SCENE_DUNGEON);
        world.transferResult = true;
        world.transferEffect =
                (who, properties) -> {
                    scene.getPlayers().remove(who);
                    destination.getPlayers().add(who);
                    who.setScene(destination);
                };

        var response = request(new byte[0]);

        assertEquals(0, response.getRetcode());
        assertEquals(DUNGEON, response.getDungeonId());
        assertEquals(57, response.getPointId());
        assertSame(destination, player.getScene());
        assertEquals(3, destination.getPrevScene());
        assertEquals(57, destination.getPrevScenePoint());
        assertNotSame(manager, destination.getDungeonManager());
        assertNotEquals(claim, destination.getDungeonManager().getClaimOperationId());
        assertFalse(destination.getDungeonManager().hasClaimedDomainReward(player.getUid()));
        assertFalse(destination.getDungeonManager().isFinishedSuccessfully());
        assertNotEquals(0, request(new byte[0]).getRetcode());
        assertEquals(1, world.transfers.size());
    }

    @Test
    void unfinishedUnclaimedAndNonEmptyRequestsAlwaysGetAnError() throws Exception {
        manager.getFinishedConditions()[0] = 0;
        assertNotEquals(0, request(new byte[0]).getRetcode());
        manager.getFinishedConditions()[0] = 1;
        manager.getRewardedPlayersForClaims().clear();
        assertNotEquals(0, request(new byte[0]).getRetcode());
        manager.getRewardedPlayersForClaims().add(player.getUid());
        assertNotEquals(0, request(new byte[] {8, 1}).getRetcode());
        assertTrue(world.transfers.isEmpty());
    }

    @Test
    void missingPlayerStillGetsTheCurrentErrorReply() throws Exception {
        session = new CaptureSession(null);
        assertNotEquals(0, request(new byte[0]).getRetcode());
        assertTrue(world.transfers.isEmpty());
    }

    @Test
    void resourceFailureStillReturnsAnErrorAndKeepsTheOldRun() throws Exception {
        data.failStart = true;
        String claim = manager.getClaimOperationId();
        assertNotEquals(0, request(new byte[0]).getRetcode());
        assertSame(scene, player.getScene());
        assertEquals(List.of(player), scene.getPlayers());
        assertEquals(claim, manager.getClaimOperationId());
    }

    @Test
    void responseUsesTheNativeFieldNumbers() throws Exception {
        var bytes = HexFormat.of().parseHex("1801487b5807");
        var response = DungeonRestartRsp.parseFrom(bytes);
        assertEquals(1, response.getRetcode());
        assertEquals(123, response.getDungeonId());
        assertEquals(7, response.getPointId());
        assertArrayEquals(bytes, response.toByteArray());
    }

    private DungeonRestartRsp request(byte[] payload) throws Exception {
        session.sent.clear();
        byte[] header = new byte[] {8, 42};
        new GameServerPacketHandler(PacketHandler.class).handle(session, 7857, header, payload);
        var replies = session.sent.stream().filter(packet -> packet.getOpcode() == 9666).toList();
        assertEquals(1, replies.size());
        var packet = replies.getFirst();
        assertArrayEquals(header, packet.getHeader());
        return DungeonRestartRsp.parseFrom(packet.getData());
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class TestDungeonData extends DungeonData {
        private boolean failStart;

        @Override
        public emu.grasscutter.game.world.Position getStartPosition() {
            if (failStart) throw new IllegalStateException("Missing scene config");
            return new emu.grasscutter.game.world.Position();
        }

        @Override
        public emu.grasscutter.game.world.Position getStartRotation() {
            return new emu.grasscutter.game.world.Position();
        }
    }

    private static final class CaptureSession extends GameSession {
        private final List<BasePacket> sent = new ArrayList<>();

        private CaptureSession(GameServer server) {
            super(server);
            setState(SessionState.ACTIVE);
        }

        @Override
        public void send(BasePacket packet) {
            sent.add(packet);
        }
    }
}
