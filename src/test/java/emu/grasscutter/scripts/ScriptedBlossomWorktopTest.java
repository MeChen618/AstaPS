package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.managers.blossom.BlossomType;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.TestWorld;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class ScriptedBlossomWorktopTest {
    private static GadgetData original;

    @BeforeAll
    static void installGadget() {
        int id = BlossomType.BLUE.getGadgetId();
        original = GameData.getGadgetDataMap().put(id, JsonUtils.decode(
                "{id:" + id + ",type:'Worktop'}", GadgetData.class));
    }

    @AfterAll
    static void restoreGadget() {
        int id = BlossomType.BLUE.getGadgetId();
        if (original == null) GameData.getGadgetDataMap().remove(id);
        else GameData.getGadgetDataMap().put(id, original);
    }

    @Test
    void luaGadgetIsInteractiveBeforeScenePublishesItsEntity() {
        var player = new TestWorld.TestPlayer();
        var world = TestWorld.create(player);
        var scene = new TestWorld.TestScene(world, 3, SceneType.SCENE_WORLD);
        player.setScene(scene);
        var source = new SceneGadget();
        source.gadget_id = BlossomType.BLUE.getGadgetId();
        source.config_id = 176008;
        source.pos = new Position(1, 2, 3);
        source.rot = new Position();

        // Group zero bypasses camp eligibility; the same Lua creation path is exercised.
        var entity = scene.getScriptManager().createGadget(0, 0, source);

        assertNotNull(entity);
        assertInstanceOf(GadgetWorktop.class, entity.getContent());
        assertEquals(204, entity.getState());
        assertTrue(((GadgetWorktop) entity.getContent()).getWorktopOptions().contains(187));
        var published = entity.toProto().getGadget();
        assertTrue(published.getIsEnableInteract());
        assertTrue(published.getWorktop().getOptionListList().contains(187));
    }

    @Test
    void aNativeWorktopIsNotDuplicatedWhenItsLuaGroupLoads() {
        var player = new TestWorld.TestPlayer();
        var world = TestWorld.create(player);
        var scene = new TestWorld.TestScene(world, 3, SceneType.SCENE_WORLD);
        player.setScene(scene);
        var source = new SceneGadget();
        source.gadget_id = BlossomType.BLUE.getGadgetId();
        source.config_id = 176008;
        source.pos = new Position(1, 2, 3);
        source.rot = new Position();

        var nativeGadget = scene.getScriptManager().createGadget(0, 0, source);
        assertNotNull(nativeGadget);
        scene.getEntities().put(nativeGadget.getId(), nativeGadget);

        assertNull(scene.getScriptManager().createGadget(0, 0, source));
        assertSame(nativeGadget, scene.getEntityByConfigId(176008, 0));
        assertEquals(1, scene.getEntities().size());
        assertTrue(((GadgetWorktop) nativeGadget.getContent())
                .getWorktopOptions().contains(187));
    }
}
