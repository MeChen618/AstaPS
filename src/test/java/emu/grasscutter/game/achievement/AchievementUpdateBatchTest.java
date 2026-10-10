package emu.grasscutter.game.achievement;

import static emu.grasscutter.net.proto.AchievementOuterClass.Achievement.Status.Status_FINISHED;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AchievementUpdateBatchTest {
    private static Achievement achievement(int id) {
        return new Achievement(Status_FINISHED, id, 5, 5, 123);
    }

    @Test
    void collectsUniqueUpdatesAndCountsRealStatusChanges() {
        var changes = new Achievements.UpdateBatch();
        var a = achievement(10001);
        var b = achievement(10002);
        changes.record(a, true);
        changes.record(b, false);
        changes.record(a, false); // Linked stages can be reached more than once.

        var packets = new ArrayList<List<Achievement>>();
        changes.flush(packets::add);

        assertEquals(1, changes.changedCount());
        assertEquals(1, packets.size());
        assertEquals(List.of(a, b), packets.getFirst());
    }

    @Test
    void splitsLargeUpdatesIntoBoundedPacketsWithoutDroppingIds() {
        var changes = new Achievements.UpdateBatch();
        for (int id = 1; id <= 270; id++) {
            changes.record(achievement(id), true);
        }

        var packets = new ArrayList<List<Achievement>>();
        changes.flush(packets::add);

        assertEquals(270, changes.changedCount());
        assertEquals(List.of(128, 128, 14), packets.stream().map(List::size).toList());
        assertEquals(
                IntStream.rangeClosed(1, 270).boxed().toList(),
                packets.stream().flatMap(List::stream).map(Achievement::getId).toList());
    }

    @Test
    void emptyBatchDoesNotSendPackets() {
        var changes = new Achievements.UpdateBatch();
        var packets = new ArrayList<List<Achievement>>();

        changes.flush(packets::add);

        assertTrue(changes.isEmpty());
        assertEquals(0, changes.changedCount());
        assertTrue(packets.isEmpty());
    }
}
