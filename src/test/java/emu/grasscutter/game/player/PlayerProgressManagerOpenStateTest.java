package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.config.ConfigContainer;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData.TalkData;
import emu.grasscutter.data.excels.OpenStateData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.OpenStateChangeNotifyOuterClass.OpenStateChangeNotify;
import emu.grasscutter.server.game.GameSession;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(ServerResourceFixture.class)
final class PlayerProgressManagerOpenStateTest {
    private QuestData previousTempleQuest;

    @BeforeEach
    void installTempleQuestDefinition() {
        previousTempleQuest =
                GameData.getQuestDataMap()
                        .put(
                                30904,
                                new Gson()
                                        .fromJson(
                                                "{\"subId\":30904,\"mainId\":309}",
                                                QuestData.class));
    }

    @AfterEach
    void restoreTempleQuestDefinition() {
        if (previousTempleQuest == null) GameData.getQuestDataMap().remove(30904);
        else GameData.getQuestDataMap().put(30904, previousTempleQuest);
    }

    private static GameQuest templeQuest(Player player, QuestState state) {
        var parent = new SavedTalkQuest();
        var quest = new GameQuest();
        quest.setState(state);
        parent.quests.put(30904, quest);
        player.getQuestManager().getMainQuests().put(309, parent);
        return quest;
    }

    private static OpenStateData state(String json) {
        return new Gson().fromJson(json, OpenStateData.class);
    }

    @Test
    void loginBulkUnlockIsOptInEvenWhenQuestingIsOff() {
        var defaultConfig = new ConfigContainer.GameOptions();
        assertFalse(defaultConfig.unlockAllOpenStatesOnLogin);
        assertFalse(defaultConfig.questing.enabled);

        var configured =
                new Gson()
                        .fromJson(
                                "{\"server\":{\"game\":{\"gameOptions\":{\"questing\":{\"enabled\":false},"
                                    + "\"unlockAllOpenStatesOnLogin\":true}}}}",
                                ConfigContainer.class);
        assertTrue(configured.server.game.gameOptions.unlockAllOpenStatesOnLogin);
    }

    @Test
    void realSevenOneQuestConditionIsDecoded() {
        var quest =
                state(
                        "{\"id\":31,\"cond\":[{\"cond_type\":\"OPEN_STATE_COND_QUEST\",\"param\":2010001},{}]}");
        assertEquals(
                OpenStateData.OpenStateCondType.OPEN_STATE_COND_QUEST,
                quest.getCond().get(0).getCondType());
        assertEquals(2010001, quest.getCond().get(0).getParam());
        assertTrue(PlayerProgressManager.isAutomaticOpenStateCandidate(quest));
        assertFalse(PlayerProgressManager.isDefaultOpenState(quest));
    }

    @Test
    void legacyConditionFieldNameRemainsSupported() {
        var level =
                state(
                        "{\"id\":30,\"cond\":[{\"condType\":\"OPEN_STATE_COND_PLAYER_LEVEL\",\"param\":2}]}");
        assertEquals(
                OpenStateData.OpenStateCondType.OPEN_STATE_COND_PLAYER_LEVEL,
                level.getCond().get(0).getCondType());
        assertTrue(PlayerProgressManager.isAutomaticOpenStateCandidate(level));
    }

    @Test
    void emptyConditionsCannotOpenNonDefaultFeatures() {
        var absent = state("{\"id\":47,\"cond\":[{},{}]}");
        assertFalse(PlayerProgressManager.isAutomaticOpenStateCandidate(absent));
        assertFalse(PlayerProgressManager.isDefaultOpenState(absent));
    }

    @Test
    void actualDefaultsAndPaimonRemainOpen() {
        var defaultState = state("{\"id\":10,\"defaultState\":true,\"cond\":[]}");
        assertTrue(PlayerProgressManager.isDefaultOpenState(defaultState));
        assertTrue(PlayerProgressManager.isAutomaticOpenStateCandidate(defaultState));

        var paimon = state("{\"id\":1,\"allowClientOpen\":true,\"cond\":[]}");
        assertTrue(PlayerProgressManager.isDefaultOpenState(paimon));
        assertFalse(PlayerProgressManager.isAutomaticOpenStateCandidate(paimon));

        var clientDefault =
                state("{\"id\":7,\"defaultState\":true,\"allowClientOpen\":true,\"cond\":[]}");
        assertFalse(PlayerProgressManager.isDefaultOpenState(clientDefault));
        assertFalse(PlayerProgressManager.isAutomaticOpenStateCandidate(clientDefault));
    }

    @ParameterizedTest
    @ValueSource(ints = {30902, 30904})
    void savedLisaTalkUnlocksStarterBoundaryWithoutStartingHiddenQuest(int talkId)
            throws Exception {
        var previous = new ArrayList<>(GameData.getOpenStateList());
        try {
            GameData.getOpenStateList().clear();
            var gate =
                    state(
                            "{\"id\":47,\"cond\":[{\"cond_type\":\"OPEN_STATE_COND_QUEST\",\"param\":35001},{}]}");
            gate.onLoad();
            var session = new CaptureSession();
            var player = new Player();
            templeQuest(player, QuestState.QUEST_STATE_FINISHED);
            session.setPlayer(player);
            var parent = new SavedTalkQuest();
            player.getQuestManager().getMainQuests().put(311, parent);
            player.getProgressManager().tryUnlockOpenStates(false);
            assertEquals(0, player.getProgressManager().getOpenState(47));

            player.getTalkManager().saveTalkToQuest(talkId, 311);

            assertEquals(1, parent.saves);
            assertEquals(1, player.getProgressManager().getOpenState(47));
            assertNull(player.getQuestManager().getMainQuestById(350));
            assertTrue(
                    session.sent.stream()
                            .anyMatch(
                                    packet -> {
                                        try {
                                            return packet.getOpcode()
                                                            == emu.grasscutter.net.packet
                                                                    .PacketOpcodes
                                                                    .OpenStateChangeNotify
                                                    && OpenStateChangeNotify.parseFrom(
                                                                            packet.getData())
                                                                    .getOpenStateMapOrDefault(47, 0)
                                                            == 1;
                                        } catch (Exception exception) {
                                            throw new AssertionError(exception);
                                        }
                                    }));

            // 旧账号已有对话记录时，登录重查也能补回遗漏的状态。
            player.getOpenStates().remove(47);
            player.getProgressManager().tryUnlockOpenStates(false);
            assertEquals(1, player.getProgressManager().getOpenState(47));
            assertEquals(1, parent.saves);
        } finally {
            awaitQuestEvents();
            GameData.getOpenStateList().clear();
            GameData.getOpenStateList().addAll(previous);
        }
    }

    @Test
    void otherTalksAndOtherParentsCannotUnlockTheStarterBoundary() {
        var previous = new ArrayList<>(GameData.getOpenStateList());
        try {
            GameData.getOpenStateList().clear();
            var gate =
                    state(
                            "{\"id\":47,\"cond\":[{\"cond_type\":\"OPEN_STATE_COND_QUEST\",\"param\":35001}]}");
            gate.onLoad();
            var player = new Player();
            var temple = templeQuest(player, QuestState.QUEST_STATE_UNFINISHED);
            var lisa = new SavedTalkQuest();
            lisa.talks.put(30901, new TalkData(30901, ""));
            player.getQuestManager().getMainQuests().put(311, lisa);
            var unrelated = new SavedTalkQuest();
            unrelated.talks.put(30902, new TalkData(30902, ""));
            player.getQuestManager().getMainQuests().put(350, unrelated);

            player.getProgressManager().tryUnlockOpenStates(false);

            assertEquals(0, player.getProgressManager().getOpenState(47));
            assertFalse(lisa.talks.containsKey(30902));
            temple.setState(QuestState.QUEST_STATE_FINISHED);
            player.getProgressManager().tryUnlockOpenStates(false);
            assertEquals(0, player.getProgressManager().getOpenState(47));
            temple.setState(QuestState.QUEST_STATE_UNFINISHED);
            lisa.talks.put(30902, new TalkData(30902, ""));
            player.getProgressManager().tryUnlockOpenStates(false);
            assertEquals(0, player.getProgressManager().getOpenState(47));
        } finally {
            awaitQuestEvents();
            GameData.getOpenStateList().clear();
            GameData.getOpenStateList().addAll(previous);
        }
    }

    private static void awaitQuestEvents() {
        try {
            emu.grasscutter.game.quest.QuestManager.eventExecutor
                    .submit(() -> {})
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class SavedTalkQuest extends GameMainQuest {
        private final Map<Integer, TalkData> talks = new HashMap<>();
        private final Map<Integer, GameQuest> quests = new HashMap<>();

        @Override
        public Map<Integer, GameQuest> getChildQuests() {
            return quests;
        }

        private int saves;

        @Override
        public Map<Integer, TalkData> getTalks() {
            return talks;
        }

        @Override
        public void save() {
            saves++;
        }
    }

    private static final class CaptureSession extends GameSession {
        private final List<BasePacket> sent = new ArrayList<>();

        private CaptureSession() {
            super(null);
        }

        @Override
        public void send(BasePacket packet) {
            sent.add(packet);
        }
    }
}
