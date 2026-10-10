package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class AccountCreateArgumentsTest {
    @Test
    void acceptsPasswordlessAccountsAndOptionalReservedUid() {
        var noArgs = AccountCommand.parseCreateArguments(null, null);
        assertNull(noArgs.password());
        assertEquals(0, noArgs.uid());

        var uidOnly = AccountCommand.parseCreateArguments("@10001", null);
        assertNull(uidOnly.password());
        assertEquals(10001, uidOnly.uid());
    }

    @Test
    void acceptsPasswordWithOrWithoutUid() {
        var passwordOnly = AccountCommand.parseCreateArguments("secret", null);
        assertEquals("secret", passwordOnly.password());
        assertEquals(0, passwordOnly.uid());

        var both = AccountCommand.parseCreateArguments("secret", 10002);
        assertEquals("secret", both.password());
        assertEquals(10002, both.uid());
    }

    @Test
    void rejectsDuplicateUid() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AccountCommand.parseCreateArguments("@10001", 10002));
    }

    @Test
    void picocliAcceptsAllDocumentedCreateForms() {
        var command = new AccountCommand();
        assertDoesNotThrow(() -> command.createCommandLine(null, null).parseArgs("create", "alice"));
        assertDoesNotThrow(
                () -> command.createCommandLine(null, null).parseArgs("create", "alice", "secret"));
        assertDoesNotThrow(
                () -> command.createCommandLine(null, null).parseArgs("create", "alice", "@10001"));
        assertDoesNotThrow(
                () ->
                        command
                                .createCommandLine(null, null)
                                .parseArgs("create", "alice", "secret", "@10001"));
    }

    @Test
    void usageShowsSimpleSyntaxWhileKeepingUidOnlyParsing() {
        var create = new AccountCommand().createCommandLine(null, null).getSubcommands().get("create");
        var usage = create.getUsageMessage();
        assertTrue(usage.contains("account create <username> [password] [@UID]"));
        assertFalse(usage.contains("password|@UID"));
    }

    @Test
    void resetPasswordRequiresUsernameAndNewPassword() {
        var handler = new AccountCommand();
        assertDoesNotThrow(
                () -> handler.createCommandLine(null, null).parseArgs("resetpass", "alice", "newpass"));
        assertThrows(
                CommandLine.ParameterException.class,
                () -> handler.createCommandLine(null, null).parseArgs("resetpass", "alice"));
    }

    @Test
    void picocliRejectsMalformedTrailingUid() {
        var command = new AccountCommand();
        assertThrows(
                CommandLine.ParameterException.class,
                () ->
                        command
                                .createCommandLine(null, null)
                                .parseArgs("create", "alice", "secret", "10001"));
    }
}
