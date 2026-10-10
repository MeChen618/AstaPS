package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class BarrierCommandTest {
    @Test
    void requiresExplicitStateWithoutAnOnlinePlayerForParsing() {
        var command = new BarrierCommand();
        assertEquals(Command.TargetRequirement.NONE,
                BarrierCommand.class.getAnnotation(Command.class).targetRequirement());
        assertThrows(CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null).parseArgs());
        for (String state : new String[] {"on", "off", "1", "0", "ON", "OFF"}) {
            assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs(state));
        }
        assertThrows(CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null).parseArgs("toggle"));
        assertThrows(CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null).parseArgs("on", "off"));
    }

    @Test
    void retainsPermissionsAndAliases() {
        var annotation = BarrierCommand.class.getAnnotation(Command.class);
        assertEquals("player.setprop", annotation.permission());
        assertEquals("player.setprop.others", annotation.permissionTargeted());
        assertArrayEquals(new String[] {"br"}, annotation.aliases());
    }
}
