package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class PlayerProgressQuestOwnershipTest {
    @Test
    void starterStatueBypassIsOnlyUsedWhenQuestRuntimeCannotRun() {
        assertFalse(PlayerProgressManager.shouldBypassStarterStatueQuest(true, true));
        assertTrue(PlayerProgressManager.shouldBypassStarterStatueQuest(false, true));
        assertTrue(PlayerProgressManager.shouldBypassStarterStatueQuest(true, false));
        assertTrue(PlayerProgressManager.shouldBypassStarterStatueQuest(false, false));
    }
}
