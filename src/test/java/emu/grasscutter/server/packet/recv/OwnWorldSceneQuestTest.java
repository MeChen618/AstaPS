package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_ENTER_MY_WORLD_SCENE;
import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.quest.QuestEventRecorder;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.content.ContentEnterMyWorldScene;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.TestWorld;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class OwnWorldSceneQuestTest {
    @Test
    void queuesWorldRoomAndDungeonEntriesForTheLoadedOwner() {
        var previousCombat = GameData.getConfigGlobalCombat();
        try {
            var owner = new RecordingPlayer(701);
            var guest = new RecordingPlayer(702);
            var world = TestWorld.create(owner);
            world.getPlayers().add(guest);
            guest.setWorld(world);
            var overworld = new TestWorld.TestScene(world, 3, SceneType.SCENE_WORLD);
            var room = new TestWorld.TestScene(world, 1075, SceneType.SCENE_ROOM);
            var dungeon = new TestWorld.TestScene(world, 20253, SceneType.SCENE_DUNGEON);
            var home = new TestWorld.TestScene(world, 2008, SceneType.SCENE_HOME_WORLD);

            enter(owner, room, SceneLoadState.LOADING);
            enter(owner, dungeon, SceneLoadState.INIT);
            enter(owner, home, SceneLoadState.LOADED);
            enter(guest, room, SceneLoadState.LOADED);
            assertTrue(owner.events.types().isEmpty());
            assertTrue(guest.events.types().isEmpty());

            for (var scene : List.of(overworld, room, dungeon)) {
                enter(owner, scene, SceneLoadState.LOADED);
            }
            assertEquals(
                    List.of(
                            QUEST_CONTENT_ENTER_MY_WORLD_SCENE,
                            QUEST_CONTENT_ENTER_MY_WORLD_SCENE,
                            QUEST_CONTENT_ENTER_MY_WORLD_SCENE),
                    owner.events.types());
            assertEquals(List.of(3, 1075, 20253), owner.events.firstParams());

            HandlerAddQuestContentProgressReq.queueReportedProgress(
                    owner, QUEST_CONTENT_ENTER_MY_WORLD_SCENE, room.getId());
            HandlerAddQuestContentProgressReq.queueReportedProgress(
                    guest, QUEST_CONTENT_ENTER_MY_WORLD_SCENE, room.getId());
            assertEquals(List.of(3, 1075, 20253), owner.events.firstParams());
            assertTrue(guest.events.types().isEmpty());

            HandlerAddQuestContentProgressReq.queueReportedProgress(
                    owner, QUEST_CONTENT_ENTER_MY_WORLD_SCENE, dungeon.getId());
            assertEquals(List.of(3, 1075, 20253, 20253), owner.events.firstParams());
        } finally {
            GameData.setConfigGlobalCombat(previousCombat);
        }
    }

    @Test
    void requiresAnExactPositiveConfiguredSceneAndAnEntryEvent() {
        var handler = new ContentEnterMyWorldScene();
        var condition = new QuestData.QuestContentCondition();
        condition.setType(QUEST_CONTENT_ENTER_MY_WORLD_SCENE);
        condition.setParam(new int[] {1075, 0});

        assertTrue(handler.execute(null, condition, "", 1075));
        assertFalse(handler.execute(null, condition, "", 20253));
        assertFalse(handler.execute(null, condition, ""));

        condition.setParam(new int[] {0, 0});
        assertFalse(handler.execute(null, condition, "", 0));
        condition.setParam(new int[0]);
        assertFalse(handler.execute(null, condition, "", 1075));
        condition.setParam(null);
        assertFalse(handler.execute(null, condition, "", 1075));
    }

    private static void enter(RecordingPlayer player, Scene scene, SceneLoadState loadState) {
        player.setScene(scene);
        player.setSceneId(scene.getId());
        player.setSceneLoadState(loadState);
        HandlerPostEnterSceneReq.queueOwnWorldSceneEntryQuestEvent(player, scene);
    }

    private static final class RecordingPlayer extends Player {
        private final QuestEventRecorder events = new QuestEventRecorder(this);

        RecordingPlayer(int uid) {
            setUid(uid);
        }

        @Override
        public QuestManager getQuestManager() {
            return events.manager();
        }
    }
}
