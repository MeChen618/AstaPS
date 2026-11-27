package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.utils.FileUtils;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/** Resolves named coordinates from the scene globals or the corresponding authored Lua table. */
public final class SceneDummyPoints {
    private final Path sceneRoot;
    private final Map<Integer, Map<String, Position>> luaPoints = new ConcurrentHashMap<>();

    public SceneDummyPoints() {
        this(FileUtils.getScriptPath("Scene"));
    }

    SceneDummyPoints(Path sceneRoot) {
        this.sceneRoot = sceneRoot;
    }

    @Nullable
    public Position resolve(int sceneId, String name) {
        if (sceneId <= 0 || name == null || name.isBlank()) return null;
        var point = fromSceneData(sceneId, name);
        if (point != null) return point;

        var luaPoint = luaPoints.computeIfAbsent(sceneId, this::loadScene).get(name);
        return luaPoint == null ? null : new Position(luaPoint);
    }

    private Map<String, Position> loadScene(int sceneId) {
        var path = sceneRoot.resolve(Integer.toString(sceneId)).resolve("scene" + sceneId + "_dummy_points.lua");
        return Files.isRegularFile(path) ? load(path) : Map.of();
    }

    @Nullable
    public static Position fromSceneData(int sceneId, String name) {
        var globals = GameData.getScriptSceneDataMap().get("flat.luas.scenes.full_globals.lua.json");
        if (globals == null || globals.getScriptObjectList() == null) return null;
        var script =
                globals.getScriptObjectList().get(sceneId + "/scene" + sceneId + "_dummy_points.lua");
        if (script == null || script.getDummyPoints() == null) return null;
        var xyz = script.getDummyPoints().get(name + ".pos");
        if (xyz == null
                || xyz.size() != 3
                || xyz.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
            return null;
        }
        return new Position(xyz.get(0), xyz.get(1), xyz.get(2));
    }

    public static Map<String, Position> load(Path path) {
        try (var reader = Files.newBufferedReader(path)) {
            return read(reader);
        } catch (IOException | RuntimeException failure) {
            Grasscutter.getLogger()
                    .warn(
                            "Cannot read scene dummy points from {}.",
                            path,
                            failure);
            return Map.of();
        }
    }

    public static Map<String, Position> read(Reader reader) {
        // The dummy-point resource only declares tables. No libraries, ScriptLib, I/O or require
        // are installed, and it cannot mutate the shared game-script globals.
        var globals = new Globals();
        LuaC.install(globals);
        globals.load(reader, "scene-dummy-points").call();
        var points = globals.get("dummy_points");
        if (!points.istable()) return Map.of();
        var result = new HashMap<String, Position>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            var entry = points.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (key.type() != LuaValue.TSTRING || !entry.arg(2).istable()) continue;
            var pos = entry.arg(2).get("pos");
            if (!pos.istable()) continue;
            var x = pos.get("x");
            var y = pos.get("y");
            var z = pos.get("z");
            if (x.type() != LuaValue.TNUMBER
                    || y.type() != LuaValue.TNUMBER
                    || z.type() != LuaValue.TNUMBER) continue;
            float px = x.tofloat(), py = y.tofloat(), pz = z.tofloat();
            if (Float.isFinite(px) && Float.isFinite(py) && Float.isFinite(pz))
                result.put(key.tojstring(), new Position(px, py, pz));
        }
        return Map.copyOf(result);
    }
}
