package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScriptSceneData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(ServerResourceFixture.class)
final class SceneDummyPointsTest {
    private static final String FLAT_SCENE_DATA = "flat.luas.scenes.full_globals.lua.json";

    @Test
    void readsLuaOnlyNamesFromTheirOwnSceneAndPrefersFlattenedPositions(@TempDir Path sceneRoot)
            throws Exception {
        var sceneDirectory = Files.createDirectory(sceneRoot.resolve("9"));
        Files.writeString(
                sceneDirectory.resolve("scene9_dummy_points.lua"),
                """
                dummy_points = {
                    ["DUMMY_POS 900000001"] = { pos = { x = -12, y = 20, z = 6 } },
                    SharedPoint = { pos = { x = 40, y = 50, z = 60 } }
                }
                """);

        var sceneScript = new ScriptSceneData.ScriptObject();
        sceneScript.setDummyPoints(Map.of("SharedPoint.pos", List.of(1f, 2f, 3f)));
        var sceneData = new ScriptSceneData();
        sceneData.setScriptObjectList(Map.of("9/scene9_dummy_points.lua", sceneScript));

        var sceneFiles = GameData.getScriptSceneDataMap();
        var previous = sceneFiles.put(FLAT_SCENE_DATA, sceneData);
        try {
            var points = new SceneDummyPoints(sceneRoot);
            var luaOnly = points.resolve(9, "DUMMY_POS 900000001");
            assertNotNull(luaOnly);
            assertEquals(-12f, luaOnly.getX());
            assertEquals(20f, luaOnly.getY());
            assertEquals(6f, luaOnly.getZ());

            var shared = points.resolve(9, "SharedPoint");
            assertNotNull(shared);
            assertEquals(1f, shared.getX());
            assertEquals(2f, shared.getY());
            assertEquals(3f, shared.getZ());

            assertNull(points.resolve(6, "DUMMY_POS 900000001"));
            assertNull(points.resolve(9, "AbsentPoint"));
        } finally {
            if (previous == null) {
                sceneFiles.remove(FLAT_SCENE_DATA);
            } else {
                sceneFiles.put(FLAT_SCENE_DATA, previous);
            }
        }
    }
}
