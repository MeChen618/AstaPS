package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.command.CommandMap;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class SensitiveCommandSyntaxTest {
    @Test
    void banIpIsAvailableOnlyAsSubcommand() {
        var map = new CommandMap(false);
        map.registerCommand("ban", new BanCommand());
        map.registerCommand("unban", new UnbanCommand());
        assertNull(map.getHandler("banip"));
        assertNull(map.getHandler("unbanip"));
    }

    @Test
    void killAllRequiresExplicitKeyWithoutLegacyOrCharacterRoutes() {
        var commandLine = new KillCommand().createCommandLine(null, null);
        assertTrue(commandLine.getSubcommands().containsKey("all"));
        assertFalse(commandLine.getSubcommands().containsKey("character"));
        assertFalse(commandLine.getSubcommands().containsKey("avatar"));
        assertThrows(
                CommandLine.ParameterException.class,
                () -> commandLine.parseArgs("all"));
        assertDoesNotThrow(() -> commandLine.parseArgs("all", "key", "3"));

        var map = new CommandMap(false);
        map.registerCommand("kill", new KillCommand());
        assertNull(map.getHandler("killall"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("killcharacter"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("suicide"));
    }

    @Test
    void ipBanRoutesRequireKeyAndAddress() {
        assertThrows(
                CommandLine.ParameterException.class,
                () -> new BanCommand().createCommandLine(null, null).parseArgs("ip", "127.0.0.1"));
        assertDoesNotThrow(
                () -> new BanCommand().createCommandLine(null, null).parseArgs("ip", "key", "127.0.0.1"));
    }

    @Test
    void ipUnbanRoutesRequireKeyAndAddress() {
        assertThrows(
                CommandLine.ParameterException.class,
                () -> new UnbanCommand().createCommandLine(null, null).parseArgs("ip", "127.0.0.1"));
        assertDoesNotThrow(
                () -> new UnbanCommand().createCommandLine(null, null).parseArgs("ip", "key", "127.0.0.1"));
    }
}
