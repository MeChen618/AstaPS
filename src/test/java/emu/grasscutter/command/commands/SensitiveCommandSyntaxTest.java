package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class SensitiveCommandSyntaxTest {
    @Test
    void unifiedBanTargetsReplaceLegacyRoutes() {
        var map = new CommandMap(false);
        map.registerCommand("ban", new BanCommand());
        map.registerCommand("unban", new UnbanCommand());
        assertNull(map.getHandler("banip"));
        assertNull(map.getHandler("unbanip"));
        var cli = new BanCommand().createCommandLine(null, null);
        assertTrue(cli.getSubcommands().isEmpty());
        assertFalse(BanCommand.class.getAnnotation(Command.class).inlineTarget());
        assertTrue(cli.getUsageMessage().contains("<playerSelector|IPv4>"));
        assertTrue(cli.getUsageMessage().contains("playerSelector: @UID"));
        assertThrows(CommandLine.ParameterException.class, () -> cli.parseArgs());
    }

    @Test
    void killAllRequiresExplicitKeyWithoutLegacyOrCharacterRoutes() {
        var cli = new KillCommand().createCommandLine(null, null);
        assertTrue(cli.getSubcommands().containsKey("all"));
        assertFalse(cli.getSubcommands().containsKey("character"));
        assertFalse(cli.getSubcommands().containsKey("avatar"));
        assertThrows(CommandLine.ParameterException.class, () -> cli.parseArgs("all"));
        assertDoesNotThrow(() -> cli.parseArgs("all", "key", "3"));

        var map = new CommandMap(false);
        map.registerCommand("kill", new KillCommand());
        assertNull(map.getHandler("killall"));
        assertFalse(map.getCommandLine().getSubcommands().containsKey("killcharacter"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("suicide"));
    }

    @Test
    void explicitBanSelectorsAndOptionalReason() {
        for (String target : List.of("@10001", "rino@", "rino@10001", "20261010@", "123.123.123.123")) {
            assertDoesNotThrow(
                    () -> new BanCommand().createCommandLine(null, null).parseArgs(target));
            assertDoesNotThrow(() -> BanCommand.parseBanArguments(target, List.of()));
        }
        assertEquals(BanCommand.TargetType.PLAYER, BanCommand.parseTarget("@10001").type());
        assertEquals("@10001", BanCommand.parseTarget("@10001").value());
        assertEquals(BanCommand.TargetType.IPV4, BanCommand.parseTarget("123.123.123.123").type());
        assertEquals(BanCommand.TargetType.PLAYER, BanCommand.parseTarget("rino@").type());
        assertEquals(BanCommand.TargetType.PLAYER, BanCommand.parseTarget("20261010@10001").type());
        assertEquals("rino@10001", BanCommand.parseTarget("rino@10001").value());
        assertThrows(IllegalArgumentException.class, () -> BanCommand.parseTarget("@legacy"));

        var timed = BanCommand.parseBanArguments("account_name@",
                List.of("1800000000", "Repeated", "abuse"));
        assertEquals(1800000000, timed.endTime());
        assertEquals("Repeated abuse", timed.reason());
        assertEquals("Cheating", BanCommand.parseBanArguments("@10001",
                List.of("Cheating")).reason());
        assertEquals("Reason not specified.", BanCommand.parseBanArguments(
                "123.123.123.123", List.of()).reason());
        assertEquals("Spam", BanCommand.parseBanArguments(
                "123.123.123.123", List.of("Spam")).reason());
    }

    @Test
    void rejectsInvalidSelectorsAndIpExpiration() {
        for (String invalid : List.of("@", "@0", "@-1", "@99999999999999",
                "rino@0", "rino@other", "123.123.123.999", "001.2.3.4",
                "rino", "account_name", "legacy.name", "10001")) {
            assertThrows(IllegalArgumentException.class, () -> BanCommand.parseTarget(invalid));
        }
        assertThrows(IllegalArgumentException.class,
                () -> BanCommand.parseBanArguments(null, List.of()));
        var bareName = assertThrows(IllegalArgumentException.class,
                () -> BanCommand.parseBanArguments("rino", List.of("Cheating")));
        assertTrue(bareName.getMessage().contains("playerSelector"));
        assertThrows(IllegalArgumentException.class,
                () -> BanCommand.parseBanArguments("@10001", List.of("999999999999999999")));
        assertThrows(IllegalArgumentException.class,
                () -> BanCommand.parseBanArguments("123.123.123.123", List.of("1800000000")));
    }

    @Test
    void coopAndMailUseIdenticalExplicitPlayerSelectors() {
        for (String selector : List.of("@10001", "rino@", "20261010@", "rino@10001")) {
            assertDoesNotThrow(() -> new CoopCommand()
                    .createCommandLine(null, null).parseArgs(selector), selector);
            assertDoesNotThrow(() -> new MailCommand()
                    .createCommandLine(null, null)
                    .parseArgs("send", selector, "--title", "Hello", "--body", "Test"), selector);
        }
        assertDoesNotThrow(() -> new MailCommand().createCommandLine(null, null)
                .parseArgs("send", "all", "--title", "Hello", "--body", "Test"));
        for (String invalid : List.of("@0", "rino@other", "@rino", "10001", "rino")) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> new CoopCommand().createCommandLine(null, null).parseArgs(invalid));
            assertThrows(CommandLine.ParameterException.class,
                    () -> new MailCommand().createCommandLine(null, null)
                            .parseArgs("send", invalid, "--title", "Hello", "--body", "Test"));
        }
    }

    @Test
    void unbanIpStillRequiresKey() {
        assertThrows(CommandLine.ParameterException.class,
                () -> new UnbanCommand().createCommandLine(null, null).parseArgs("ip", "127.0.0.1"));
        assertDoesNotThrow(
                () -> new UnbanCommand().createCommandLine(null, null).parseArgs("ip", "key", "127.0.0.1"));
    }
}
