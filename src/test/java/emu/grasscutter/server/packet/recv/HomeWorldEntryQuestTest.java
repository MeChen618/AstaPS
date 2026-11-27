package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_ENTER_MY_HOME_WORLD;
import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.home.HomeWorld;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.quest.QuestEventRecorder;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.content.ContentEnterMyHomeWorld;
import emu.grasscutter.game.systems.TestPlayers;
import emu.grasscutter.game.world.TestWorld;
import emu.grasscutter.server.game.GameSession;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class HomeWorldEntryQuestTest {
    @Test
    void usesTheConfiguredOutdoorRealmIds() {
        var handler = new ContentEnterMyHomeWorld();
        var condition = new QuestData.QuestContentCondition();
        condition.setType(QUEST_CONTENT_ENTER_MY_HOME_WORLD);
        condition.setParam(new int[] {0, 0});
        condition.setParamStr("2001,2002,2003,2004,2005,2006,2007,2008");

        assertTrue(handler.execute(null, condition, "", 2008));
        assertFalse(handler.execute(null, condition, "", 3));
        assertFalse(handler.execute(null, condition, ""));

        condition.setParamStr("2001");
        assertTrue(handler.execute(null, condition, "", 2001));
        assertFalse(handler.execute(null, condition, "", 2008));

        condition.setParamStr("2001,invalid");
        assertFalse(handler.execute(null, condition, "", 2001));
    }

    @Test
    void queuesOnlyTheHostsCompletedHomeWorldEntry() {
        var server = TestPlayers.server();
        var owner = new RecordingPlayer(701, new GameSession(server));
        var guest = new RecordingPlayer(702, new GameSession(server));
        var home = new HomeWorld(server, owner) {
            @Override
            public void refreshModuleManager() {}
        };
        var outdoorScene = new TestWorld.TestScene(home, 2008, SceneType.SCENE_HOME_WORLD);
        owner.setScene(outdoorScene);
        guest.setScene(outdoorScene);

        owner.setSceneLoadState(SceneLoadState.INIT);
        HandlerPostEnterSceneReq.queueHomeEntryQuestEvent(owner, outdoorScene);
        assertTrue(owner.events.types().isEmpty());

        owner.setSceneLoadState(SceneLoadState.LOADED);
        guest.setSceneLoadState(SceneLoadState.LOADED);
        HandlerPostEnterSceneReq.queueHomeEntryQuestEvent(owner, outdoorScene);
        HandlerPostEnterSceneReq.queueHomeEntryQuestEvent(guest, outdoorScene);

        assertEquals(List.of(QUEST_CONTENT_ENTER_MY_HOME_WORLD), owner.events.types());
        assertEquals(List.of(2008), owner.events.firstParams());
        assertTrue(guest.events.types().isEmpty());
    }

    private static final class RecordingPlayer extends Player {
        private final QuestEventRecorder events = new QuestEventRecorder(this);
        private final GameHome home;

        RecordingPlayer(int uid, GameSession session) {
            setUid(uid);
            this.home = GameHome.create(uid);
            setSession(session);
        }

        @Override
        public GameHome getHome() {
            return home;
        }

        @Override
        public QuestManager getQuestManager() {
            return events.manager();
        }
    }
}
