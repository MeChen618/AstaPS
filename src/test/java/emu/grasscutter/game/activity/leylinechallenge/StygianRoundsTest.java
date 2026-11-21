package emu.grasscutter.game.activity.leylinechallenge;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StygianRoundsTest {
    @Test
    void requiresThreeDistinctBossDefeatsAndCancelsOnExit() {
        var rounds = new StygianRounds();
        assertEquals(StygianRounds.Phase.PREPARE, rounds.phase());
        assertFalse(rounds.start(0));
        assertTrue(rounds.start(101));
        assertFalse(rounds.start(102));
        assertFalse(rounds.defeated(999));
        assertTrue(rounds.defeated(101));
        assertFalse(rounds.defeated(101));
        assertEquals(2, rounds.round());
        assertTrue(rounds.start(102));
        assertTrue(rounds.defeated(102));
        assertEquals(StygianRounds.Phase.PREPARE, rounds.phase());
        assertTrue(rounds.start(103));
        assertTrue(rounds.defeated(103));
        assertEquals(StygianRounds.Phase.COMPLETE, rounds.phase());
        assertFalse(rounds.start(104));

        var cancelled = new StygianRounds();
        assertTrue(cancelled.start(201));
        cancelled.exit();
        assertFalse(cancelled.defeated(201));
        assertFalse(cancelled.start(202));
        assertEquals(StygianRounds.Phase.EXITED, cancelled.phase());
    }
}
