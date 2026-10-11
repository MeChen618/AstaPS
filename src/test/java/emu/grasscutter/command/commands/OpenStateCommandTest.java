package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandMap;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class OpenStateCommandTest {
    @Test
    void openStateUsesItsOwnCommandAndInheritedPermissionNodes() {
        var handler = new OpenStateCommand();
        var map = new CommandMap(false);
        map.registerCommand("openstate", handler);

        assertSame(handler, map.getHandler("openstate"));
        assertEquals("openstate", handler.getLabel());
        var metadata = OpenStateCommand.class.getAnnotation(Command.class);
        assertEquals("player.setprop", metadata.permission());
        assertEquals("player.setprop.others", metadata.permissionTargeted());
    }

    @Test
    void openStateHasReadAndWriteFormsWithoutSeparateSetUnsetAliases() {
        var cli = new OpenStateCommand().createCommandLine(null, null);
        assertEquals("openstate", cli.getCommandName());
        assertTrue(cli.getSubcommands().isEmpty());
        assertDoesNotThrow(() -> cli.parseArgs("45"));
        assertDoesNotThrow(() -> new OpenStateCommand().createCommandLine(null, null)
                .parseArgs("45", "1"));
        assertDoesNotThrow(() -> new OpenStateCommand().createCommandLine(null, null)
                .parseArgs("45", "0"));

        for (String[] args : new String[][] {
                {}, {"set", "45", "1"}, {"unset", "45"},
                {"45", "on"}, {"45", "1", "extra"}
        }) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> new OpenStateCommand().createCommandLine(null, null).parseArgs(args));
        }
    }
}
