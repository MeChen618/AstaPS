package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class RestoreLegacyRoutesTest {
    @Test
    void healRetainsNoArgumentAndAllGrammar() {
        var handler = new HealCommand();
        assertDoesNotThrow(() -> handler.createCommandLine(null, null).parseArgs());
        assertDoesNotThrow(() -> handler.createCommandLine(null, null).parseArgs("all"));
        assertThrows(
                CommandLine.ParameterException.class,
                () -> handler.createCommandLine(null, null).parseArgs("all", "extra"));
    }

    @Test
    void energyAliasesHaveNoPositionalArguments() {
        var handler = new erCommand();
        assertDoesNotThrow(() -> handler.createCommandLine(null, null).parseArgs());
        assertThrows(
                CommandLine.ParameterException.class,
                () -> handler.createCommandLine(null, null).parseArgs("extra"));
    }
}
