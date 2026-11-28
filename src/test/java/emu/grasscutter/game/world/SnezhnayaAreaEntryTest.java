package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.world.WorldAreaData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.SnezhnayaFrigid;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.EnterWorldAreaReqOuterClass.EnterWorldAreaReq;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerEnterWorldAreaReq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(ServerResourceFixture.class)
final class SnezhnayaAreaEntryTest {
    @Test
    @SuppressWarnings("unchecked")
    void missingNewOutlinesUseAreaReportsAndSceneEntryClearsStaleReports() throws Exception {
        var areas = new HashMap<>(GameData.getWorldAreaDataMap());
        var field = WorldAreaIndex.class.getDeclaredField("CACHE");
        field.setAccessible(true);
        var cache = (Map<Integer, WorldAreaIndex>) field.get(null);
        var oldIndex = cache.put(3, WorldAreaIndex.parse(JsonParser.parseString("{}")));
        var combat = GameData.getConfigGlobalCombat();
        var player =
                new Player() {
                    private final emu.grasscutter.game.player.TeamManager team =
                            new emu.grasscutter.game.player.TeamManager(this);

                    @Override
                    public emu.grasscutter.game.player.TeamManager getTeamManager() {
                        return team;
                    }
                };
        player.setUid(991860);
        try {
            GameData.getWorldAreaDataMap().clear();
            for (int area : new int[] {60, 61}) {
                var row =
                        new Gson()
                                .fromJson(
                                        "{\"areaID1\":"
                                                + area
                                                + ",\"sceneID\":3,"
                                                + "\"teamAbilityGroupList\":[\"DynamicAbility_IceCreature_V7_0_Team\"]}",
                                        WorldAreaData.class);
                GameData.getWorldAreaDataMap().put(row.getId(), row);
            }
            var world = TestWorld.create(player);
            player.setScene(new TestWorld.TestScene(world, 3, SceneType.SCENE_WORLD));
            player.getPosition().set(new Position(-500, 200, 1000));
            var session =
                    new GameSession(null) {
                        @Override
                        public void send(BasePacket packet) {}
                    };
            session.setPlayer(player);
            player.setSession(session);
            assertFalse(SnezhnayaFrigid.isIn(player));
            for (int area : new int[] {60, 61, (12 << 16) | 60}) {
                new HandlerEnterWorldAreaReq()
                        .handle(
                                session,
                                PacketHead.getDefaultInstance().toByteArray(),
                                EnterWorldAreaReq.newBuilder()
                                        .setAreaId(area)
                                        .setAreaType(1)
                                        .build()
                                        .toByteArray());
                assertTrue(SnezhnayaFrigid.isIn(player));
                assertTrue(SnezhnayaFrigid.isGranted(player));
                assertTrue(
                        player.getTeamManager()
                                .getTeamAbilityEmbryos()
                                .containsAll(
                                        java.util.List.of(
                                                "DynamicAbility_IceCreature_7_0",
                                                "DynamicAbility_SnezBookAvailable",
                                                "SceneObj_Area_Zd_SnezhnayaFrigid_TeamAbility_Basic")));
            }
            SnezhnayaFrigid.onAreaEntered(player, 7);
            assertFalse(SnezhnayaFrigid.isIn(player));
            assertFalse(SnezhnayaFrigid.isGranted(player));
            SnezhnayaFrigid.onAreaEntered(player, 60);
            SnezhnayaFrigid.syncForSceneEntry(player);
            assertFalse(SnezhnayaFrigid.isIn(player));
            assertFalse(SnezhnayaFrigid.isGranted(player));
        } finally {
            SnezhnayaFrigid.syncForSceneEntry(player);
            GameData.setConfigGlobalCombat(combat);
            GameData.getWorldAreaDataMap().clear();
            GameData.getWorldAreaDataMap().putAll(areas);
            if (oldIndex == null) cache.remove(3);
            else cache.put(3, oldIndex);
        }
    }
}
