package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class ConstellationCommandTest {
    @Test
    void setAndResetKeepDistinctRoutes() {
        var command = new ConstellationCommand();
        var cli = command.createCommandLine(null, null);
        assertDoesNotThrow(() -> cli.parseArgs("set", "0"));
        assertDoesNotThrow(() -> cli.parseArgs("set", "6", "all"));
        assertDoesNotThrow(() -> cli.parseArgs("reset"));
        assertDoesNotThrow(() -> cli.parseArgs("reset", "ALL"));
        assertThrows(CommandLine.ParameterException.class, () -> cli.parseArgs("set"));
        assertThrows(CommandLine.ParameterException.class, () -> cli.parseArgs("set", "not-a-level"));
        assertThrows(CommandLine.ParameterException.class, () -> cli.parseArgs("reset", "unexpected"));
        assertThrows(CommandLine.ParameterException.class, () -> cli.parseArgs("set", "6", "all", "extra"));
    }

    @Test
    void levelRangeIsInclusiveZeroThroughSix() {
        assertFalse(ConstellationCommand.validLevel(-1));
        for (int level = 0; level <= 6; level++) {
            assertTrue(ConstellationCommand.validLevel(level));
        }
        assertFalse(ConstellationCommand.validLevel(7));
    }

    @Test
    void commandRetainsOnlineTargetRequirement() {
        assertEquals(Command.TargetRequirement.ONLINE,
                ConstellationCommand.class.getAnnotation(Command.class).targetRequirement());
    }
}
