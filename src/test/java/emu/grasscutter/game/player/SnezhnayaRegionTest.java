package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.world.WorldAreaData;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

final class SnezhnayaRegionTest {
    private Map<Integer, WorldAreaData> original;

    @BeforeEach
    void isolateAreas() {
        original = new HashMap<>(GameData.getWorldAreaDataMap());
        GameData.getWorldAreaDataMap().clear();
    }

    @AfterEach
    void restoreAreas() {
        GameData.getWorldAreaDataMap().clear();
        GameData.getWorldAreaDataMap().putAll(original);
    }

    @Test
    void abilityGroupsIdentifyBothReleasesAndFutureAreas() {
        for (int area : new int[] {55, 59, 60, 61, 75}) {
            addArea(area, 3, "DynamicAbility_IceCreature_V7_0_Team");
            assertTrue(SnezhnayaFrigid.isSnezhnayaArea(3, area), "area " + area);
        }
        addArea(56, 3, "Unrelated_Team");
        addArea(62, 5, "DynamicAbility_IceCreature_V7_0_Team");
        assertFalse(SnezhnayaFrigid.isSnezhnayaArea(3, 56));
        assertFalse(SnezhnayaFrigid.isSnezhnayaArea(3, 62));
        assertFalse(SnezhnayaFrigid.isSnezhnayaArea(5, 60));
        assertFalse(SnezhnayaFrigid.isSnezhnayaArea(3, 0));
    }

    @Test
    void legacyResourcesWithoutAbilityGroupsIncludeSevenTwo() {
        for (int area : new int[] {55, 59, 60, 61}) {
            assertTrue(SnezhnayaFrigid.isSnezhnayaArea(3, area));
        }
        assertFalse(SnezhnayaFrigid.isSnezhnayaArea(3, 54));
        assertFalse(SnezhnayaFrigid.isSnezhnayaArea(3, 62));
    }

    private void addArea(int area, int scene, String group) {
        var row =
                new Gson()
                        .fromJson(
                                "{\"areaID1\":"
                                        + area
                                        + ",\"sceneID\":"
                                        + scene
                                        + ",\"teamAbilityGroupList\":[\""
                                        + group
                                        + "\"]}",
                                WorldAreaData.class);
        GameData.getWorldAreaDataMap().put(row.getId(), row);
    }
}
