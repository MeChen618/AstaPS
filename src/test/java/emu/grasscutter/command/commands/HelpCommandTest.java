package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class HelpCommandTest {
    @Test
    void acceptsRootAndNestedHelpPaths() {
        var handler = new HelpCommand();
        assertDoesNotThrow(() -> handler.createCommandLine(null, null).parseArgs());
        assertDoesNotThrow(() -> handler.createCommandLine(null, null)
                .parseArgs("teleport", "pos"));
        assertDoesNotThrow(() -> handler.createCommandLine(null, null)
                .parseArgs("mail", "system", "send"));
    }

    @Test
    void resolvesSubcommandUsageAndRejectsUnknownSubcommands() {
        var teleport = new TeleportCommand().createCommandLine(null, null);
        CommandLine pos = HelpCommand.findSubcommand(teleport, List.of("pos"));
        assertNotNull(pos);
        assertTrue(pos.getUsageMessage().contains("<x>"));
        assertNull(HelpCommand.findSubcommand(teleport, List.of("unknown")));
        assertNull(HelpCommand.findSubcommand(teleport, List.of("pos", "unknown")));
        assertSame(teleport, HelpCommand.findSubcommand(teleport, List.of()));
    }

    @Test
    void displaysCanonicalDungeonRoute() {
        var dungeon = new EnterDungeonCommand();
        assertEquals("dungeon", dungeon.getLabel());
        assertEquals("commands.enter_dungeon.description", dungeon.getDescriptionKey());
        assertNotNull(HelpCommand.findSubcommand(dungeon.createCommandLine(null, null), List.of()));
    }
}
