package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.quest.content.BaseContent;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MondstadtQuestHandlerCoverageTest {
    // Exact type census for Genshin-Reverse's 7.1 Mondstadt mainline manifest, using the reviewed
    // companion Resource #14 snapshot at 6dc4400126f3b006028d24349faba8018cf9cecf.
    private static final Set<QuestContent> REQUIRED_CONTENT =
            EnumSet.of(
                    QuestContent.QUEST_CONTENT_ADD_QUEST_PROGRESS,
                    QuestContent.QUEST_CONTENT_CLEAR_GROUP_MONSTER,
                    QuestContent.QUEST_CONTENT_COMPLETE_TALK,
                    QuestContent.QUEST_CONTENT_DESTROY_GADGET,
                    QuestContent.QUEST_CONTENT_ENTER_DUNGEON,
                    QuestContent.QUEST_CONTENT_ENTER_MY_WORLD,
                    QuestContent.QUEST_CONTENT_ENTER_ROOM,
                    QuestContent.QUEST_CONTENT_FAIL_DUNGEON,
                    QuestContent.QUEST_CONTENT_FINISH_DUNGEON,
                    QuestContent.QUEST_CONTENT_FINISH_PLOT,
                    QuestContent.QUEST_CONTENT_GAME_TIME_TICK,
                    QuestContent.QUEST_CONTENT_INTERACT_GADGET,
                    QuestContent.QUEST_CONTENT_LUA_NOTIFY,
                    QuestContent.QUEST_CONTENT_NOT_FINISH_PLOT,
                    QuestContent.QUEST_CONTENT_OBTAIN_ITEM,
                    QuestContent.QUEST_CONTENT_SKILL,
                    QuestContent.QUEST_CONTENT_TEAM_DEAD,
                    QuestContent.QUEST_CONTENT_TRIGGER_FIRE,
                    QuestContent.QUEST_CONTENT_UNLOCK_TRANS_POINT);

    private static final Set<QuestExec> REQUIRED_EXEC =
            EnumSet.of(
                    QuestExec.QUEST_EXEC_ADD_CUR_AVATAR_ENERGY,
                    QuestExec.QUEST_EXEC_ADD_QUEST_PROGRESS,
                    QuestExec.QUEST_EXEC_CHANGE_AVATAR_ELEMET,
                    QuestExec.QUEST_EXEC_DEL_PACK_ITEM,
                    QuestExec.QUEST_EXEC_DEL_PACK_ITEM_BATCH,
                    QuestExec.QUEST_EXEC_GRANT_TRIAL_AVATAR,
                    QuestExec.QUEST_EXEC_LOCK_POINT,
                    QuestExec.QUEST_EXEC_NOTIFY_GROUP_LUA,
                    QuestExec.QUEST_EXEC_REFRESH_GROUP_MONSTER,
                    QuestExec.QUEST_EXEC_REFRESH_GROUP_SUITE,
                    QuestExec.QUEST_EXEC_REMOVE_TRIAL_AVATAR,
                    QuestExec.QUEST_EXEC_ROLLBACK_QUEST,
                    QuestExec.QUEST_EXEC_SET_IS_FLYABLE,
                    QuestExec.QUEST_EXEC_SET_IS_GAME_TIME_LOCKED,
                    QuestExec.QUEST_EXEC_SET_IS_WEATHER_LOCKED,
                    QuestExec.QUEST_EXEC_SET_OPEN_STATE,
                    QuestExec.QUEST_EXEC_SET_QUEST_GLOBAL_VAR,
                    QuestExec.QUEST_EXEC_SET_WEATHER_GADGET,
                    QuestExec.QUEST_EXEC_UNLOCK_AREA,
                    QuestExec.QUEST_EXEC_UNLOCK_POINT);

    @Test
    void allMondstadtPrologueContentTypesHaveRuntimeHandlers() {
        var implemented =
                Grasscutter.reflector.getSubTypesOf(BaseContent.class).stream()
                        .map(type -> type.getAnnotation(QuestValueContent.class))
                        .filter(annotation -> annotation != null)
                        .map(QuestValueContent::value)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(QuestContent.class)));

        assertContainsAll(implemented, REQUIRED_CONTENT);
    }

    @Test
    void allMondstadtPrologueExecTypesHaveRuntimeHandlers() {
        var implemented =
                Grasscutter.reflector.getSubTypesOf(QuestExecHandler.class).stream()
                        .map(type -> type.getAnnotation(QuestValueExec.class))
                        .filter(annotation -> annotation != null)
                        .map(QuestValueExec::value)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(QuestExec.class)));

        assertContainsAll(implemented, REQUIRED_EXEC);
    }

    private static <T extends Enum<T>> void assertContainsAll(Set<T> implemented, Set<T> required) {
        var missing = EnumSet.copyOf(required);
        missing.removeAll(implemented);
        assertTrue(missing.isEmpty(), () -> "Missing Mondstadt quest handlers: " + missing);
    }
}
