package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.PlayerProgressManager;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestSystem;
import emu.grasscutter.game.quest.QuestValueExec;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.GetScenePointRspOuterClass.GetScenePointRsp;
import emu.grasscutter.net.proto.SceneTransToPointReqOuterClass.SceneTransToPointReq;
import emu.grasscutter.net.proto.SceneTransToPointRspOuterClass.SceneTransToPointRsp;
import emu.grasscutter.net.proto.UnlockTransPointReqOuterClass.UnlockTransPointReq;
import emu.grasscutter.net.proto.UnlockTransPointRspOuterClass.UnlockTransPointRsp;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerSceneTransToPointReq;
import emu.grasscutter.server.packet.recv.HandlerUnlockTransPointReq;
import emu.grasscutter.server.packet.send.PacketGetScenePointRsp;
import emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

@ExtendWith(ServerResourceFixture.class)
final class ExecLockPointTest {
    private final Gson gson = new Gson();
    private RecordingPlayer player;
    private GameQuest quest;
    private ScenePointEntry previousPoint;
    private List<Integer> previousScenePoints;

    @BeforeEach
    void setup() {
        player = new RecordingPlayer();
        player.setUid(42);
        var data =
                gson.fromJson(
                        "{subId:7004501,mainId:70045,finishCond:[],failCond:[]}", QuestData.class);
        quest =
                new GameQuest(null, data) {
                    @Override
                    public Player getOwner() {
                        return player;
                    }
                };
        var point =
                gson.fromJson(
                        "{id:831,$type:'SceneTransPoint',mapVisibility:'Never'}", PointData.class);
        previousPoint =
                GameData.getScenePointEntryMap()
                        .put((3 << 16) + 831, new ScenePointEntry(3, point));
        previousScenePoints = GameData.getScenePointsPerScene().put(3, List.of(831, 960));
    }

    @AfterEach
    void restore() {
        if (previousPoint == null) GameData.getScenePointEntryMap().remove((3 << 16) + 831);
        else GameData.getScenePointEntryMap().put((3 << 16) + 831, previousPoint);
        if (previousScenePoints == null) GameData.getScenePointsPerScene().remove(3);
        else GameData.getScenePointsPerScene().put(3, previousScenePoints);
    }

    @Test
    void lockPointActionIsRegisteredToOpcode17() {
        var registration = ExecLockPoint.class.getAnnotation(QuestValueExec.class);
        assertNotNull(registration);
        assertEquals(QuestExec.QUEST_EXEC_LOCK_POINT, registration.value());
        assertEquals(17, registration.value().getValue());
    }

    @Test
    void pointValidationRejectsInvalidSceneAndPoint() {
        assertTrue(ExecLockPoint.validPoint(3, 1720));
        assertFalse(ExecLockPoint.validPoint(0, 1720));
        assertFalse(ExecLockPoint.validPoint(3, 0));
        assertFalse(ExecLockPoint.validPoint(-1, 1720));
    }

    @Test
    void nativeVisibilityActionsShareARegisteredHandler() {
        var handlers = new Int2ObjectOpenHashMap<QuestExecHandler>();
        new QuestSystem(null).registerHandler(handlers, ExecLockPoint.class);
        assertSame(handlers.get(17), handlers.get(72));
        assertSame(handlers.get(17), handlers.get(73));
        assertInstanceOf(ExecLockPoint.class, handlers.get(72));
    }

    @Test
    void nativePointNotificationFixturesKeepUnlockLockAndVisibilitySeparate() {
        assertEquals(
                "2204bf06c0074803",
                HexFormat.of()
                        .formatHex(
                                new PacketScenePointUnlockNotify(3, List.of(831, 960)).getData()));
        assertEquals(
                "48035204bf06c007",
                HexFormat.of()
                        .formatHex(
                                PacketScenePointUnlockNotify.lock(3, List.of(831, 960)).getData()));
        assertEquals(
                "1204bf06c0074803",
                HexFormat.of()
                        .formatHex(
                                PacketScenePointUnlockNotify.visibility(3, List.of(831, 960), false)
                                        .getData()));
        assertEquals(
                "3a04bf06c0074803",
                HexFormat.of()
                        .formatHex(
                                PacketScenePointUnlockNotify.visibility(3, List.of(831, 960), true)
                                        .getData()));
    }

    @Test
    void visibilityBatchSchedulesSaveBeforeNotifyAndReplaysInTheSceneSnapshot() throws Exception {
        player.getUnlockedScenePoints(3).add(831);
        assertTrue(exec(QuestExec.QUEST_EXEC_HIDE_SCENE_POINT, "3", "831,960,831"));
        assertEquals(1, player.saves);
        assertEquals(List.of("save", "send"), player.events);
        assertEquals(Map.of(831, false, 960, false), player.savedVisibility);
        assertTrue(player.isScenePointHidden(3, 831));
        assertFalse(player.isScenePointHidden(102, 831));
        assertEquals(Set.of(831), player.getUnlockedScenePoints(3));
        assertTrue(player.getForceLockedScenePoints(3).isEmpty());
        var hidden = GetScenePointRsp.parseFrom(new PacketGetScenePointRsp(player, 3).getData());
        assertEquals(List.of(831, 960), hidden.getHidePointListList());
        assertTrue(hidden.getUnhidePointListList().isEmpty());
        assertEquals(List.of(831), hidden.getUnlockedPointListList());
        assertTrue(exec(QuestExec.QUEST_EXEC_UNHIDE_SCENE_POINT, "3", "831,960"));
        assertTrue(exec(QuestExec.QUEST_EXEC_LOCK_POINT, "3", "831"));
        assertFalse(player.isScenePointHidden(3, 831));
        assertTrue(player.isScenePointForceLocked(3, 831));
        var packet = GetScenePointRsp.parseFrom(new PacketGetScenePointRsp(player, 3).getData());
        assertEquals(List.of(831), packet.getLockedPointListList());
        assertEquals(Set.of(831, 960), Set.copyOf(packet.getUnhidePointListList()));
        assertTrue(packet.getHidePointListList().isEmpty());
        assertTrue(packet.getUnlockedPointListList().isEmpty());
        assertEquals(42, packet.getBelongUid());
        assertEquals(3, packet.getSceneId());
        assertTrue(player.progress.unlockTransPoint(3, 831, false));
        assertFalse(player.isScenePointForceLocked(3, 831));
        assertFalse(player.isScenePointHidden(3, 831));
        assertTrue(player.getPendingTransPointRewards(3).isEmpty());
    }

    @Test
    void unhideDoesNotActivateAnUnvisitedPointOrAwardAnUnlock() throws Exception {
        assertTrue(exec(QuestExec.QUEST_EXEC_UNHIDE_SCENE_POINT, "3", "831"));
        int count = player.sent.size();
        assertTrue(exec(QuestExec.QUEST_EXEC_UNHIDE_SCENE_POINT, "3", "831"));
        assertEquals(count, player.sent.size());
        assertEquals(1, player.saves);
        assertTrue(player.getUnlockedScenePoints(3).isEmpty());
        assertTrue(player.getPendingTransPointRewards(3).isEmpty());
        var snapshot = GetScenePointRsp.parseFrom(new PacketGetScenePointRsp(player, 3).getData());
        assertEquals(List.of(831), snapshot.getUnhidePointListList());
        assertTrue(snapshot.getUnlockedPointListList().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"831,bad", "831,", "831,-1", "831,0", ""})
    void malformedVisibilityBatchDoesNotPartiallyChangeState(String points) {
        assertFalse(exec(QuestExec.QUEST_EXEC_HIDE_SCENE_POINT, "3", points));
        assertTrue(player.getScenePointVisibility(3).isEmpty());
        assertEquals(0, player.saves);
        assertTrue(player.sent.isEmpty());
    }

    @Test
    void savedMapPointWireFieldsMatchTheClientSchema() throws Exception {
        var snapshot =
                GetScenePointRsp.parseFrom(
                        HexFormat.of().parseHex("2202bf06302a4a02c00768037202bf06"));
        assertEquals(List.of(831), snapshot.getHidePointListList());
        assertEquals(List.of(960), snapshot.getUnhidePointListList());
        assertEquals(List.of(831), snapshot.getLockedPointListList());
        assertEquals(42, snapshot.getBelongUid());
        assertEquals(3, snapshot.getSceneId());
    }

    @Test
    void clientActivationCannotReleaseAQuestLockOrHiddenPoint() throws Exception {
        var session = session();
        var bytes =
                UnlockTransPointReq.newBuilder()
                        .setSceneId(3)
                        .setPointId(831)
                        .build()
                        .toByteArray();
        assertTrue(exec(QuestExec.QUEST_EXEC_LOCK_POINT, "3", "831"));
        player.sent.clear();
        new HandlerUnlockTransPointReq().handle(session, new byte[0], bytes);
        assertNotEquals(
                0, UnlockTransPointRsp.parseFrom(player.sent.getLast().getData()).getRetcode());
        assertTrue(player.isScenePointForceLocked(3, 831));
        assertTrue(player.getUnlockedScenePoints(3).isEmpty());
        player.getForceLockedScenePoints(3).clear();
        assertTrue(exec(QuestExec.QUEST_EXEC_HIDE_SCENE_POINT, "3", "831"));
        player.sent.clear();
        new HandlerUnlockTransPointReq().handle(session, new byte[0], bytes);
        assertNotEquals(
                0, UnlockTransPointRsp.parseFrom(player.sent.getLast().getData()).getRetcode());
        assertTrue(player.getUnlockedScenePoints(3).isEmpty());
        assertTrue(player.getPendingTransPointRewards(3).isEmpty());
    }

    @Test
    void hiddenUnlockedPointRejectsTeleportWithoutChangingProgress() throws Exception {
        player.getUnlockedScenePoints(3).add(831);
        assertTrue(exec(QuestExec.QUEST_EXEC_HIDE_SCENE_POINT, "3", "831"));
        player.sent.clear();
        var bytes =
                SceneTransToPointReq.newBuilder()
                        .setSceneId(3)
                        .setPointId(831)
                        .build()
                        .toByteArray();
        new HandlerSceneTransToPointReq().handle(session(), new byte[0], bytes);
        assertNotEquals(
                0, SceneTransToPointRsp.parseFrom(player.sent.getLast().getData()).getRetcode());
        assertTrue(player.getUnlockedScenePoints(3).contains(831));
    }

    private boolean exec(QuestExec type, String... params) {
        var action = new QuestData.QuestExecParam();
        action.setType(type);
        return new ExecLockPoint().execute(quest, action, params);
    }

    private GameSession session() {
        var session =
                new GameSession(null) {
                    @Override
                    public void send(BasePacket packet) {
                        player.sent.add(packet);
                    }
                };
        session.setPlayer(player);
        return session;
    }

    private static final class RecordingPlayer extends Player {
        final PlayerProgressManager progress = new PlayerProgressManager(this);
        final List<BasePacket> sent = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        int saves;
        Map<Integer, Boolean> savedVisibility;

        @Override
        public PlayerProgressManager getProgressManager() {
            return progress;
        }

        @Override
        public void save() {
            saves++;
            events.add("save");
            savedVisibility = Map.copyOf(getScenePointVisibility(3));
        }

        @Override
        public void sendPacket(BasePacket packet) {
            events.add("send");
            sent.add(packet);
        }
    }
}
