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
        assertTrue(cli.getUsageMessage().contains("<@UID|accountName|IPv4>"));
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
        assertTrue(map.getCommandLine().getSubcommands().containsKey("killcharacter"));
        assertTrue(map.getCommandLine().getSubcommands().containsKey("suicide"));
    }

    @Test
    void threeKindsOfBanTargetAndOptionalReason() {
        for (String target : List.of("@10001", "account_name", "123.123.123.123")) {
            assertDoesNotThrow(
                    () -> new BanCommand().createCommandLine(null, null).parseArgs(target));
        }
        assertEquals(BanCommand.TargetType.UID, BanCommand.parseTarget("@10001").type());
        assertEquals(10001, BanCommand.parseTarget("@10001").uid());
        assertEquals(BanCommand.TargetType.ACCOUNT, BanCommand.parseTarget("account_name").type());
        assertEquals(BanCommand.TargetType.IPV4, BanCommand.parseTarget("123.123.123.123").type());
        assertEquals(BanCommand.TargetType.ACCOUNT, BanCommand.parseTarget("legacy.name").type());
        assertEquals(BanCommand.TargetType.ACCOUNT, BanCommand.parseTarget("@legacy").type());

        var timed = BanCommand.parseBanArguments("account_name",
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
                "123.123.123.999", "001.2.3.4")) {
            assertThrows(IllegalArgumentException.class, () -> BanCommand.parseTarget(invalid));
        }
        assertThrows(IllegalArgumentException.class,
                () -> BanCommand.parseBanArguments(null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> BanCommand.parseBanArguments("@10001", List.of("999999999999999999")));
        assertThrows(IllegalArgumentException.class,
                () -> BanCommand.parseBanArguments("123.123.123.123", List.of("1800000000")));
    }

    @Test
    void unbanIpStillRequiresKey() {
        assertThrows(CommandLine.ParameterException.class,
                () -> new UnbanCommand().createCommandLine(null, null).parseArgs("ip", "127.0.0.1"));
        assertDoesNotThrow(
                () -> new UnbanCommand().createCommandLine(null, null).parseArgs("ip", "key", "127.0.0.1"));
    }
}
