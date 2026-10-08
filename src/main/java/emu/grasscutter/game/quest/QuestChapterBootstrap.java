package emu.grasscutter.game.quest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.quest.enums.QuestState;
import java.util.List;

/** Restores chapter-controller quests linked to active main-quest series by resource data. */
public final class QuestChapterBootstrap {
    private QuestChapterBootstrap() {}

    /**
     * The first PostEnterSceneRsp only means that a scene exists. It does not mean that
     * Quest 351's opening cinematic and Paimon dialogue have ended. Starting the linked
     * chapter controller here displays the Prologue: Act I banner on top of that dialogue.
     */
    public static boolean isOpeningStoryPending(QuestManager questManager) {
        if (questManager == null) return false;
        var opening = questManager.getMainQuestById(351);
        if (opening == null) return false;
        var intro = opening.getChildQuestById(35104);
        var dialogue = opening.getChildQuestById(35100);
        if (intro == null || dialogue == null) return false;
        return shouldDeferOpeningPresentation(intro.getState(), dialogue.getState());
    }

    static boolean shouldDeferOpeningPresentation(QuestState intro, QuestState dialogue) {
        return intro != null && intro != QuestState.QUEST_STATE_UNSTARTED
                && dialogue != QuestState.QUEST_STATE_FINISHED;
    }

    public static void startForActiveMainQuests(QuestManager questManager) {
        if (questManager == null) return;
        if (isOpeningStoryPending(questManager)) {
            Grasscutter.getLogger().info(
                    "[intro-presentation] uid={} defer chapter controller until 35100 finishes",
                    questManager.getPlayer().getUid());
            return;
        }

        // Starting a controller mutates mainQuests, so snapshot the source quests first.
        List<Integer> sourceMainQuestIds =
                questManager.getMainQuests().values().stream()
                        .filter(mainQuest -> !mainQuest.isFinished())
                        .map(GameMainQuest::getParentQuestId)
                        .toList();

        for (int sourceMainQuestId : sourceMainQuestIds) {
            int controllerMainQuestId = controllerMainQuestFor(sourceMainQuestId);
            if (controllerMainQuestId <= 0 || controllerMainQuestId == sourceMainQuestId) continue;

            if (QuestManager.opensUnlinked(controllerMainQuestId)) {
                var sourceData = GameData.getMainQuestDataMap().get(sourceMainQuestId);
                var chapter =
                        sourceData != null
                                ? GameData.getChapterDataMap().get(sourceData.getSeries())
                                : null;
                Grasscutter.getLogger()
                        .debug(
                                "Chapter bootstrap: uid={} sourceMain={} series={} chapter={} beginSub={} controllerMain={}",
                                questManager.getPlayer().getUid(),
                                sourceMainQuestId,
                                sourceData != null ? sourceData.getSeries() : 0,
                                chapter != null ? chapter.getId() : 0,
                                chapter != null ? chapter.getBeginQuestId() : 0,
                                controllerMainQuestId);
                questManager.startMainQuestIfUnlinked(controllerMainQuestId);
            }
        }
    }

    static int controllerMainQuestFor(int sourceMainQuestId) {
        var sourceData = GameData.getMainQuestDataMap().get(sourceMainQuestId);
        if (sourceData == null || sourceData.getSeries() <= 0) return 0;

        var chapter = GameData.getChapterDataMap().get(sourceData.getSeries());
        if (chapter == null || chapter.getBeginQuestId() <= 0) return 0;

        var beginQuestData = GameData.getQuestDataMap().get(chapter.getBeginQuestId());
        return beginQuestData != null ? beginQuestData.getMainId() : 0;
    }
}
