package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class AnnounceCommandTest {
    @Test
    void parsesAllRoutes() {
        var command = new AnnounceCommand();
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("Hello", "world"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("tpl", "42"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("refresh"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("revoke", "42"));
        assertThrows(CommandLine.ParameterException.class, () -> command.createCommandLine(null, null).parseArgs());
        assertThrows(CommandLine.ParameterException.class, () -> command.createCommandLine(null, null).parseArgs("tpl"));
        assertThrows(CommandLine.ParameterException.class, () -> command.createCommandLine(null, null).parseArgs("revoke", "not-an-id"));
    }

    @Test
    void validatesContentAndIds() {
        assertFalse(AnnounceCommand.validContent("  \t "));
        assertTrue(AnnounceCommand.validContent("hello world"));
        assertFalse(AnnounceCommand.validId(0));
        assertFalse(AnnounceCommand.validId(-1));
        assertTrue(AnnounceCommand.validId(1));
        var annotation = AnnounceCommand.class.getAnnotation(Command.class);
        assertEquals("server.announce", annotation.permission());
        assertArrayEquals(new String[] {"a"}, annotation.aliases());
    }
}
