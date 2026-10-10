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
    void restoreEnergyRemainsTheSupportedRoute() {
        var handler = new RestoreCommand();
        var commandLine = handler.createCommandLine(null, null);
        assertDoesNotThrow(() -> commandLine.parseArgs("energy"));
        assertThrows(
                CommandLine.ParameterException.class,
                () -> commandLine.parseArgs("energy", "extra"));
    }
}
