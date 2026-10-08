package emu.grasscutter.game.quest;

import static emu.grasscutter.game.quest.enums.QuestState.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class QuestChapterIntroPresentationTest {
    @Test
    void deferBannerWhenOpeningCinematicOrPaimonDialogueIsInProgress() {
        assertTrue(QuestChapterBootstrap.shouldDeferOpeningPresentation(
                QUEST_STATE_UNFINISHED, QUEST_STATE_UNSTARTED));
        assertTrue(QuestChapterBootstrap.shouldDeferOpeningPresentation(
                QUEST_STATE_FINISHED, QUEST_STATE_UNFINISHED));
    }

    @Test
    void releaseBannerAfterOpeningDialogueFinishes() {
        assertFalse(QuestChapterBootstrap.shouldDeferOpeningPresentation(
                QUEST_STATE_FINISHED, QUEST_STATE_FINISHED));
        assertFalse(QuestChapterBootstrap.shouldDeferOpeningPresentation(
                QUEST_STATE_UNSTARTED, QUEST_STATE_UNSTARTED));
    }
}
