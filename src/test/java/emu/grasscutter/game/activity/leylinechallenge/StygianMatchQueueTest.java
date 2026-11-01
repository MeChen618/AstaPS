package emu.grasscutter.game.activity.leylinechallenge;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StygianMatchQueueTest {
    @Test
    void pairsOnlyMatchingRunsAndRequiresBothConfirmationsAndPermits() {
        var queue = new StygianMatchQueue();
        assertTrue(queue.enqueue(1, 20003, 5269012, 4, 100));
        assertTrue(queue.enqueue(2, 20004, 5269012, 4, 100));
        assertTrue(queue.enqueue(3, 20003, 5269013, 4, 100));
        assertTrue(queue.enqueue(4, 20003, 5269012, 5, 100));
        assertTrue(queue.pair(101).isEmpty());
        assertTrue(queue.enqueue(5, 20003, 5269012, 4, 101));
        var paired = queue.pair(102);
        assertEquals(1, paired.size());
        var run = paired.get(0);
        assertEquals(java.util.List.of(1, 5), run.members);
        assertFalse(queue.beginTransfer(run, 103));
        assertTrue(queue.confirm(1, 103));
        assertFalse(queue.permit(1, 1, 103));
        assertTrue(queue.confirm(5, 104));
        assertFalse(queue.permit(1, 99, 104));
        assertTrue(queue.permit(1, 1, 104));
        assertFalse(queue.beginTransfer(run, 104));
        assertTrue(queue.permit(5, 1, 105));
        assertTrue(queue.beginTransfer(run, 105));
        assertFalse(queue.beginTransfer(run, 105));
        assertSame(run, queue.remove(1));
        assertNull(queue.get(1));
        assertNull(queue.get(5));
        assertNotNull(queue.get(2));
    }

    @Test
    void refusesExpiredQueueAndConfirmation() {
        var queue = new StygianMatchQueue();
        queue.enqueue(1, 20003, 1, 1, 100);
        queue.enqueue(2, 20003, 1, 1, 100);
        assertTrue(queue.pair(400).isEmpty());
        queue.remove(1);
        queue.remove(2);
        queue.enqueue(3, 20003, 1, 1, 500);
        queue.enqueue(4, 20003, 1, 1, 500);
        var run = queue.pair(501).get(0);
        assertFalse(queue.confirm(3, run.deadline));
    }
}
