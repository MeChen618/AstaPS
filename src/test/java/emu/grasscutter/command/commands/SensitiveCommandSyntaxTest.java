package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class SensitiveCommandSyntaxTest {
    @Test
    void bothKillAllRoutesRequireAnExplicitKey() {
        assertThrows(
                CommandLine.ParameterException.class,
                () -> new KillCommand().createCommandLine(null, null).parseArgs("all"));
        assertThrows(
                CommandLine.ParameterException.class,
                () -> new KillAllCommand().createCommandLine(null, null).parseArgs());
        assertDoesNotThrow(
                () -> new KillCommand().createCommandLine(null, null).parseArgs("all", "key", "3"));
        assertDoesNotThrow(
                () -> new KillAllCommand().createCommandLine(null, null).parseArgs("key", "3"));
    }

    @Test
    void ipBanRoutesRequireKeyAndAddress() {
        assertThrows(
                CommandLine.ParameterException.class,
                () -> new BanCommand().createCommandLine(null, null).parseArgs("ip", "127.0.0.1"));
        assertThrows(
                CommandLine.ParameterException.class,
                () -> new BanIpCommand().createCommandLine(null, null).parseArgs("key"));
        assertDoesNotThrow(
                () -> new BanCommand().createCommandLine(null, null).parseArgs("ip", "key", "127.0.0.1"));
        assertDoesNotThrow(
                () -> new BanIpCommand().createCommandLine(null, null).parseArgs("key", "127.0.0.1"));
    }

    @Test
    void ipUnbanRoutesRequireKeyAndAddress() {
        assertThrows(
                CommandLine.ParameterException.class,
                () -> new UnbanCommand().createCommandLine(null, null).parseArgs("ip", "127.0.0.1"));
        assertThrows(
                CommandLine.ParameterException.class,
                () -> new UnBanIpCommand().createCommandLine(null, null).parseArgs("key"));
        assertDoesNotThrow(
                () -> new UnbanCommand().createCommandLine(null, null).parseArgs("ip", "key", "127.0.0.1"));
        assertDoesNotThrow(
                () -> new UnBanIpCommand().createCommandLine(null, null).parseArgs("key", "127.0.0.1"));
    }
}
