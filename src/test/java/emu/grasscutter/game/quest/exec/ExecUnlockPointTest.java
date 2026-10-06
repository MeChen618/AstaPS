package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.common.PointData;
import org.junit.jupiter.api.Test;

final class ExecUnlockPointTest {
    private static final Gson GSON = new Gson();

    private static PointData point(String json) {
        return GSON.fromJson(json, PointData.class);
    }

    @Test
    void classifiesStatuesFromPointDataInsteadOfQuestId() {
        assertTrue(ExecUnlockPoint.isStatuePoint(point("{\"maxSpringVolume\":1}")));
        assertTrue(ExecUnlockPoint.isStatuePoint(point("{\"gadgetId\":70130009}")));
        assertTrue(ExecUnlockPoint.isStatuePoint(point("{\"$type\":\"KDEHKECBDBO\"}")));

        assertFalse(ExecUnlockPoint.isStatuePoint(point("{\"gadgetId\":70300001}")));
        assertFalse(ExecUnlockPoint.isStatuePoint(null));
    }
}
