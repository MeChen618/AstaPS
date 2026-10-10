package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.CommandMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class AccountCreateArgumentsTest {
    @Test
    void newAccountNamesCarryOptionalReservedUid() {
        var automatic = AccountCommand.parseNewAccount("alice");
        assertEquals("alice", automatic.username());
        assertEquals(0, automatic.uid());

        var reserved = AccountCommand.parseNewAccount("bob@10001");
        assertEquals("bob", reserved.username());
        assertEquals(10001, reserved.uid());
        assertEquals("20261010", AccountCommand.parseNewAccount("20261010@10002").username());
    }

    @Test
    void malformedNewAccountNamesAreRejected() {
        for (String invalid : List.of(
                "", " ", "@10001", "bob@", "bob@0", "bob@-1", "bob@not-a-uid",
                "bob@10001@10002", "bob@999999999999999999", "bob.name",
                "bob @10001")) {
            assertThrows(IllegalArgumentException.class,
                    () -> AccountCommand.parseNewAccount(invalid), invalid);
        }
        assertThrows(IllegalArgumentException.class,
                () -> AccountCommand.parseNewAccount(null));
    }

    @Test
    void createAndCloneAcceptCompactNewAccountSyntax() {
        var cmd = new AccountCommand();
        for (String name : List.of("alice", "alice@10001")) {
            assertDoesNotThrow(
                    () -> cmd.createCommandLine(null, null).parseArgs("create", name), name);
            assertDoesNotThrow(
                    () -> cmd.createCommandLine(null, null).parseArgs("create", name, "secret"), name);
        }

        for (String source : List.of("@10001", "alice@", "alice@10001")) {
            for (String target : List.of("bob", "bob@10002")) {
                assertDoesNotThrow(
                        () -> cmd.createCommandLine(null, null).parseArgs("clone", source, target),
                        source + " -> " + target);
            }
        }
    }

    @Test
    void parserRejectsMalformedNewNamesInBothCreationRoutes() {
        var cmd = new AccountCommand();
        for (String invalid : List.of("bob@", "bob@0", "@10002", "bob@xyz")) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> cmd.createCommandLine(null, null).parseArgs("create", invalid),
                    invalid);
            assertThrows(CommandLine.ParameterException.class,
                    () -> cmd.createCommandLine(null, null).parseArgs("clone", "alice@", invalid),
                    invalid);
        }
    }

    @Test
    void deleteAndResetPasswordUseOnlyExplicitExistingAccountSelectors() {
        var cmd = new AccountCommand();
        for (String existing : List.of("@10001", "alice@", "alice@10001")) {
            assertDoesNotThrow(
                    () -> cmd.createCommandLine(null, null).parseArgs("delete", existing));
            assertDoesNotThrow(
                    () -> cmd.createCommandLine(null, null)
                            .parseArgs("resetpassword", existing, "newpass"));
            assertDoesNotThrow(
                    () -> cmd.createCommandLine(null, null)
                            .parseArgs("passwd", existing, "newpass"));
        }

        for (String invalid : List.of("alice", "10001", "@0", "alice@other")) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> cmd.createCommandLine(null, null).parseArgs("delete", invalid));
            assertThrows(CommandLine.ParameterException.class,
                    () -> cmd.createCommandLine(null, null)
                            .parseArgs("resetpassword", invalid, "newpass"));
            assertThrows(CommandLine.ParameterException.class,
                    () -> cmd.createCommandLine(null, null)
                            .parseArgs("clone", invalid, "bob"));
        }
    }

    @Test
    void usageAndAliasesShowOnlyCanonicalForms() {
        var cli = new AccountCommand().createCommandLine(null, null);
        assertTrue(cli.getSubcommands().get("create").getUsageMessage()
                .contains("account create <newName[@newUID]> [password]"));
        assertTrue(cli.getSubcommands().get("clone").getUsageMessage()
                .contains("account clone <sourceSelector> <newName[@newUID]>"));
        assertTrue(cli.getSubcommands().get("delete").getUsageMessage()
                .contains("<accountSelector>"));
        assertTrue(cli.getSubcommands().get("resetpassword").getUsageMessage()
                .contains("<accountSelector>"));
        assertSame(cli.getSubcommands().get("resetpassword"), cli.getSubcommands().get("passwd"));
        assertFalse(cli.getSubcommands().containsKey("resetpass"));
        assertDoesNotThrow(() -> cli.parseArgs("passwd", "alice@", "newpass"));
        assertThrows(CommandLine.ParameterException.class,
                () -> cli.parseArgs("resetpass", "alice@", "newpass"));
        assertEquals("alice", CommandMap.parseExplicitTargetSelector("alice@").username());
    }

    @Test
    void oldSeparatedUidArgumentsAndExcessPositionsAreRejected() {
        var cmd = new AccountCommand();
        assertThrows(CommandLine.ParameterException.class,
                () -> cmd.createCommandLine(null, null)
                        .parseArgs("create", "alice", "secret", "@10001"));
        assertThrows(CommandLine.ParameterException.class,
                () -> cmd.createCommandLine(null, null)
                        .parseArgs("clone", "alice@", "bob", "@10002"));
        assertThrows(CommandLine.ParameterException.class,
                () -> cmd.createCommandLine(null, null).parseArgs("delete"));
        assertThrows(CommandLine.ParameterException.class,
                () -> cmd.createCommandLine(null, null)
                        .parseArgs("resetpassword", "alice@"));
    }
}
