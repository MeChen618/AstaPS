package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandMap;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class PropCommandTest {
    private static CommandLine command() {
        return new SetPropCommand().createCommandLine(null, null);
    }

    @Test
    void propGroupReplacesLegacyEntrypointWithoutChangingPermissions() {
        var map = new CommandMap(false);
        var handler = new SetPropCommand();
        map.registerCommand("prop", handler);

        assertSame(handler, map.getHandler("prop"));
        assertNull(map.getHandler("setprop"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("prop"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("setprop"));

        var annotation = SetPropCommand.class.getAnnotation(Command.class);
        assertEquals("prop", annotation.label());
        assertArrayEquals(new String[0], annotation.aliases());
        assertEquals("player.setprop", annotation.permission());
        assertEquals("player.setprop.others", annotation.permissionTargeted());
    }

    @Test
    void onlyNestedSetAcceptsPlayerPropertyChanges() {
        var commandLine = command();
        assertEquals("prop", commandLine.getCommandName());
        assertTrue(commandLine.getSubcommands().containsKey("set"));
        assertTrue(commandLine.getSubcommands().get("set").getUsageMessage()
                .contains("prop set <prop> <value>"));

        assertDoesNotThrow(() -> command().parseArgs("set", "worldlevel", "8"));
        assertDoesNotThrow(() -> command().parseArgs("set", "godmode", "on"));
        assertDoesNotThrow(() -> command().parseArgs("set", "godmode", "toggle"));
        assertDoesNotThrow(() -> command().parseArgs("set", "unlockmap", "all"));
        assertDoesNotThrow(() -> command().parseArgs("set", "bp", "50"));

        assertThrows(CommandLine.ParameterException.class,
                () -> command().parseArgs("worldlevel", "8"));
        assertThrows(CommandLine.ParameterException.class,
                () -> command().parseArgs("set", "worldlevel"));
        assertThrows(CommandLine.ParameterException.class,
                () -> command().parseArgs("set", "worldlevel", "8", "extra"));
    }
}
