package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.CommandMap;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.SnezhnayaFrigid;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.world.TestWorld;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.ServerGlobalValueChangeNotifyOuterClass.ServerGlobalValueChangeNotify;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class KresnikCommandTest {
    private static final AtomicInteger NEXT_UID = new AtomicInteger(990100);

    @Test
    void setsLiveEnergyAndSupportsQueryWithoutChangingIt() {
        var player = new RecordingPlayer();
        var handler = new KresnikCommand();
        assertEquals(0, handler.createCommandLine(player, player).execute("123.5"));
        assertEquals(123.5f, SnezhnayaFrigid.stateOf(player).getEnergy());
        assertEquals(0f, SnezhnayaFrigid.stateOf(player).getFrigid());
        assertEquals(0, handler.createCommandLine(player, player).execute());
        assertEquals(123.5f, SnezhnayaFrigid.stateOf(player).getEnergy());
        assertTrue(player.messages.getLast().contains("123.5"));
        assertEquals(0, handler.createCommandLine(player, player).execute("0"));
        assertEquals(0f, SnezhnayaFrigid.stateOf(player).getEnergy());
        assertEquals(0, handler.createCommandLine(player, player).execute("500"));
        assertEquals(500f, SnezhnayaFrigid.stateOf(player).getEnergy());
    }

    @Test
    void invalidAndNonfiniteInputsDoNotMutateTheBalance() {
        var player = new RecordingPlayer();
        assertTrue(SnezhnayaFrigid.setEnergy(player, 77));
        for (String value : List.of("-1", "501", "NaN", "Infinity", "-Infinity", "bad")) {
            var command = new KresnikCommand().createCommandLine(player, player);
            assertThrows(CommandLine.ParameterException.class, () -> command.parseArgs(value));
            assertEquals(77f, SnezhnayaFrigid.stateOf(player).getEnergy());
        }
        for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, -1f, 501f}) {
            assertFalse(SnezhnayaFrigid.setEnergy(player, value));
            assertEquals(77f, SnezhnayaFrigid.stateOf(player).getEnergy());
        }
    }

    @Test
    void aliasAndExistingPropertyPermissionsAreRegistered() {
        var registry = new CommandMap(false);
        var handler = new KresnikCommand();
        registry.registerCommand("kresnik", handler);
        assertSame(handler, registry.getHandler("kresnik"));
        assertSame(handler, registry.getHandler("kshnik"));
        var annotation = KresnikCommand.class.getAnnotation(emu.grasscutter.command.Command.class);
        assertEquals("player.setprop", annotation.permission());
        assertEquals("player.setprop.others", annotation.permissionTargeted());
    }

    @Test
    void settingEnergySendsTheNewTeamGlobalValue() throws Exception {
        var originalCombat = GameData.getConfigGlobalCombat();
        try {
            var player = new RecordingPlayer();
            TestWorld.create(player);
            var team = new EntityTeam(player);
            player.getTeamManager().setEntity(team);
            assertEquals(0, new KresnikCommand().createCommandLine(player, player).execute("250"));
            assertEquals(1, player.packets.size());
            var notify =
                    ServerGlobalValueChangeNotify.parseFrom(player.packets.getFirst().getData());
            assertEquals(team.getId(), notify.getEntityId());
            assertEquals(Utils.abilityHash(SnezhnayaFrigid.ENERGY_GV), notify.getKeyHash());
            assertEquals(250f, notify.getValue());
        } finally {
            GameData.setConfigGlobalCombat(originalCombat);
        }
    }

    private static final class RecordingPlayer extends Player {
        final List<String> messages = new ArrayList<>();
        final List<BasePacket> packets = new ArrayList<>();
        private final TeamManager team = new TeamManager(this);

        RecordingPlayer() {
            setUid(NEXT_UID.getAndIncrement());
        }

        @Override
        public void dropMessage(Object message) {
            messages.add(String.valueOf(message));
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }

        @Override
        public TeamManager getTeamManager() {
            return team;
        }
    }
}
