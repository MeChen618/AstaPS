package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.command.CommandMap;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** The public command grammar must not reintroduce duplicate routes. */
public final class CommandRedundancyTest {
    @Test
    void removedTopLevelCommandsStayUnregistered() {
        var map = new CommandMap(false);
        map.registerCommand("kill", new KillCommand());
        map.registerCommand("restore", new RestoreCommand());

        for (String removed : new String[] {"killall", "er", "e", "energy"}) {
            assertNull(map.getHandler(removed), removed);
        }
        assertNull(map.getHandler("killCharacter"));
        assertNotNull(map.getHandler("suicide"));
        assertNotNull(map.getHandler("restore"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("restore"));
    }

    @Test
    void removedCommandNamesAreAbsentWhileRetainedRoutesWork() {
        var map = new CommandMap(false);
        map.registerCommand("barrier", new BarrierCommand());
        map.registerCommand("dungeon", new EnterDungeonCommand());
        map.registerCommand("avatar", new AvatarCommand());
        map.registerCommand("kill", new KillCommand());
        map.registerCommand("trackmat", new TrackMatCommand());
        map.registerCommand("waypoints", new WaypointsCommand());

        for (String removed : new String[] {
                "pb", "enter_dungeon", "enterdungeon", "levelbreak",
                "killCharacter", "mattrack", "unlockwp",
                "extralevel", "el", "constellation", "talent",
                "setfetterlevel", "setfetterlvl", "setfriendship",
                "max", "maxavatar", "maxchar"
        }) {
            assertNull(map.getHandler(removed), removed);
            assertFalse(map.getCommandLine().getSubcommands().containsKey(removed), removed);
        }
        for (String retained : new String[] {
                "barrier", "br", "dungeon", "avatar",
                "kill", "suicide", "trackmat", "trackmaterial", "waypoints", "wp"
        }) {
            assertNotNull(map.getHandler(retained), retained);
            assertTrue(map.getCommandLine().getSubcommands().containsKey(retained), retained);
        }
    }

    @Test
    void avatarGroupReplacesRemovedTopLevelCharacterCommands() {
        var cli = new AvatarCommand().createCommandLine(null, null);
        assertTrue(cli.getSubcommands().containsKey("constellation"));
        assertTrue(cli.getSubcommands().containsKey("talent"));
        assertTrue(cli.getSubcommands().containsKey("friendship"));
        assertTrue(cli.getSubcommands().containsKey("extralevel"));
        assertTrue(cli.getSubcommands().containsKey("max"));
    }

    @Test
    void setStatsKeepsSingleSetPathAndDistinctLockCommands() {
        CommandLine cli = new SetStatsCommand().createCommandLine(null, null);
        for (String retained : new String[] {"lock", "unlock"}) {
            assertTrue(cli.getSubcommands().containsKey(retained), retained);
        }
        for (String removed : new String[] {"set", "freeze", "unfreeze"}) {
            assertFalse(cli.getSubcommands().containsKey(removed), removed);
        }
        assertNotNull(cli.getCommandSpec().positionalParameters());
    }

    @Test
    void achievementBulkActionsUseAllAsTargetRatherThanCompoundName() {
        CommandLine cli = new AchievementCommand().createCommandLine(null, null);
        for (String operation : new String[] {"grant", "revoke"}) {
            assertTrue(cli.getSubcommands().containsKey(operation));
            assertTrue(cli.getSubcommands().get(operation).getUsageMessage().contains("<achievementId|all>"));
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> new AchievementCommand().createCommandLine(null, null).parseArgs(operation, "12345"));
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> new AchievementCommand().createCommandLine(null, null).parseArgs(operation, "all"));
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> new AchievementCommand().createCommandLine(null, null).parseArgs(operation, "ALL"));
            org.junit.jupiter.api.Assertions.assertThrows(
                    CommandLine.ParameterException.class,
                    () -> new AchievementCommand().createCommandLine(null, null).parseArgs(operation, "invalid"));
            org.junit.jupiter.api.Assertions.assertThrows(
                    CommandLine.ParameterException.class,
                    () -> new AchievementCommand().createCommandLine(null, null).parseArgs(operation));
        }
        assertFalse(cli.getSubcommands().containsKey("grantall"));
        assertFalse(cli.getSubcommands().containsKey("revokeall"));
        org.junit.jupiter.api.Assertions.assertThrows(
                CommandLine.ParameterException.class, () -> cli.parseArgs("grantall"));
        org.junit.jupiter.api.Assertions.assertThrows(
                CommandLine.ParameterException.class, () -> cli.parseArgs("revokeall"));
    }

    @Test
    void sceneTagsKeepOneNamePerOperation() {
        CommandLine cli = new SetSceneTagCommand().createCommandLine(null, null);
        for (String retained : new String[] {"add", "remove", "reset", "unlock", "list"}) {
            assertTrue(cli.getSubcommands().containsKey(retained), retained);
        }
        for (String removed : new String[] {"set", "del", "restore", "unlockall"}) {
            assertFalse(cli.getSubcommands().containsKey(removed), removed);
        }
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> cli.parseArgs("unlock", "all"));
        org.junit.jupiter.api.Assertions.assertThrows(
                CommandLine.ParameterException.class, () -> cli.parseArgs("unlock"));
        org.junit.jupiter.api.Assertions.assertThrows(
                CommandLine.ParameterException.class, () -> cli.parseArgs("unlockall"));
    }

    @Test
    void globalUnlockHasAllSubcommandWithLegacyPermissions() {
        var map = new CommandMap(false);
        var handler = new UnlockAllCommand();
        map.registerCommand("unlock", handler);
        assertNotNull(map.getHandler("unlock"));
        assertNull(map.getHandler("unlockall"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("unlock"));
        var cli = handler.createCommandLine(null, null);
        assertTrue(cli.getSubcommands().containsKey("all"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> cli.parseArgs("all"));
        org.junit.jupiter.api.Assertions.assertThrows(
                CommandLine.ParameterException.class, () -> cli.parseArgs("unlockall"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "player.unlockall",
                UnlockAllCommand.class.getAnnotation(emu.grasscutter.command.Command.class).permission());
        org.junit.jupiter.api.Assertions.assertEquals(
                "player.unlockall.others",
                UnlockAllCommand.class.getAnnotation(emu.grasscutter.command.Command.class).permissionTargeted());
    }
}
