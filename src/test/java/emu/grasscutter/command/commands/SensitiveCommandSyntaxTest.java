package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandMap;
import java.util.List;
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
    void playerBanUsesMandatoryLeadingAtUid() {
        var ban = new BanCommand();
        assertFalse(BanCommand.class.getAnnotation(Command.class).inlineTarget());
        var cli = ban.createCommandLine(null, null);
        assertFalse(cli.getSubcommands().containsKey("player"));
        assertTrue(cli.getSubcommands().containsKey("ip"));
        assertTrue(cli.getUsageMessage().contains("ban @UID [endTime] [reason...]"));

        // The root positional is optional to Picocli so the "ip" subcommand
        // remains available. Runtime validation still requires an explicit @UID.
        assertThrows(
                IllegalArgumentException.class,
                () -> BanCommand.parsePlayerArguments(null, List.of()));
        assertDoesNotThrow(
                () -> ban.createCommandLine(null, null).parseArgs("@10001"));
        assertDoesNotThrow(
                () -> ban.createCommandLine(null, null).parseArgs("@10001", "1800000000", "Cheating"));
        assertDoesNotThrow(
                () -> ban.createCommandLine(null, null).parseArgs("@10001", "Cheating"));
    }

    @Test
    void playerBanParsesOptionalEndTimeAndReasonWithoutChangingUidPosition() {
        var defaultBan = BanCommand.parsePlayerArguments("@10001", List.of());
        assertEquals(10001, defaultBan.uid());
        assertEquals("Reason not specified.", defaultBan.reason());

        var specifiedBan =
                BanCommand.parsePlayerArguments("@10002", List.of("1800000000", "Repeated", "abuse"));
        assertEquals(10002, specifiedBan.uid());
        assertEquals(1800000000, specifiedBan.endTime());
        assertEquals("Repeated abuse", specifiedBan.reason());

        var reasonOnly = BanCommand.parsePlayerArguments("@10003", List.of("Cheating"));
        assertEquals(defaultBan.endTime(), reasonOnly.endTime());
        assertEquals("Cheating", reasonOnly.reason());
    }

    @Test
    void playerBanRejectsMissingAndMisplacedUidOrOverflowEndTime() {
        for (String selector : List.of("10001", "1800000000", "@alice", "@0", "@-1", "@9999999999999")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BanCommand.parsePlayerArguments(selector, List.of("@10001")));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> BanCommand.parsePlayerArguments("@10001", List.of("999999999999999999")));
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
