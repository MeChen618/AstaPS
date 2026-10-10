package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import at.favre.lib.crypto.bcrypt.BCrypt;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Unmatched;

@Command(
        label = "account",
        targetRequirement = Command.TargetRequirement.NONE,
        inlineTarget = false)
public final class AccountCommand implements CommandHandler {
    private record UidArg(int value) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        if (sender != null) {
            var rejected = new CommandLine(new ConsoleOnly(sender));
            rejected.setUnmatchedArgumentsAllowed(true);
            rejected.setExpandAtFiles(false);
            return rejected;
        }

        var commandLine = new CommandLine(new AccountRoot(sender));
        commandLine.setExpandAtFiles(false);
        commandLine.registerConverter(UidArg.class, value -> parseUid(sender, value));

        commandLine.addSubcommand("create", new Create(sender));
        commandLine.addSubcommand("clone", new Clone(sender));
        commandLine.addSubcommand("delete", new Delete(sender));
        commandLine.addSubcommand("resetpass", new ResetPass(sender));
        return commandLine;
    }

    private static UidArg parseUid(Player sender, String value) {
        if (value == null || value.length() < 2 || value.charAt(0) != '@') {
            throw new CommandLine.TypeConversionException("UID must use @<digits> syntax.");
        }
        try {
            int uid = Integer.parseInt(value.substring(1));
            if (uid <= 0) throw new NumberFormatException();
            return new UidArg(uid);
        } catch (NumberFormatException ignored) {
            throw new CommandLine.TypeConversionException(
                    translate(sender, "commands.account.invalid"));
        }
    }

    @picocli.CommandLine.Command(name = "account")
    private static final class ConsoleOnly implements Runnable {
        private final Player sender;

        @Unmatched private String[] ignored;

        private ConsoleOnly(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            CommandOutput.sendTranslatedMessage(sender, "commands.generic.console_execute_error");
        }
    }

    @picocli.CommandLine.Command(name = "account")
    private final class AccountRoot implements Runnable {
        private final Player sender;

        private AccountRoot(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            AccountCommand.this.sendUsageMessage(sender);
        }
    }

    record CreateArguments(String password, int uid) {}

    /**
     * A lone @UID specifies a reserved UID, not a password. When a password is supplied,
     * the optional @UID follows it.
     */
    static CreateArguments parseCreateArguments(String passwordOrUid, Integer trailingUid) {
        if (passwordOrUid != null && passwordOrUid.startsWith("@")) {
            if (trailingUid != null) {
                throw new IllegalArgumentException("Specify the new account UID only once.");
            }
            return new CreateArguments(null, parseUid(null, passwordOrUid).value());
        }
        return new CreateArguments(passwordOrUid, trailingUid == null ? 0 : trailingUid);
    }

    @picocli.CommandLine.Command(name = "create")
    private final class Create implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<username>")
        private String username;

        @Parameters(index = "1", arity = "0..1", paramLabel = "<password|@UID>")
        private String passwordOrUid;

        @Parameters(index = "2", arity = "0..1", paramLabel = "<@UID>")
        private UidArg trailingUid;

        private Create(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            try {
                var arguments =
                        parseCreateArguments(
                                passwordOrUid, trailingUid == null ? null : trailingUid.value());
                createAccount(sender, username, arguments.password(), arguments.uid());
            } catch (IllegalArgumentException invalid) {
                CommandOutput.sendMessage(sender, invalid.getMessage());
            }
        }
    }

    @picocli.CommandLine.Command(name = "clone")
    private static final class Clone implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<source-account>")
        private String sourceUsername;

        @Parameters(index = "1", paramLabel = "<target-account>")
        private String targetUsername;

        @Parameters(index = "2", arity = "0..1", paramLabel = "[@UID]")
        private UidArg uid;

        private Clone(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            try {
                var result =
                        PlayerCloneService.cloneOffline(
                                sourceUsername, targetUsername, uid == null ? 0 : uid.value());
                CommandOutput.sendMessage(
                        sender,
                        "Cloned %s (UID %d) to %s (UID %d): %d persisted documents copied."
                                .formatted(
                                        sourceUsername,
                                        result.sourceUid(),
                                        targetUsername,
                                        result.targetUid(),
                                        result.clonedDocuments()));
                CommandOutput.sendMessage(
                        sender,
                        "Friendships and public music-game beatmaps were intentionally not cloned.");
            } catch (IllegalArgumentException | IllegalStateException failure) {
                CommandOutput.sendMessage(sender, "Clone failed: " + failure.getMessage());
            }
        }
    }

    @picocli.CommandLine.Command(name = "delete")
    private final class Delete implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<username>")
        private String username;

        private Delete(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Account toDelete = DatabaseHelper.getAccountByName(username);
            if (toDelete == null) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.account.no_account"));
                return;
            }

            AccountDeletionService.delete(toDelete);
            CommandOutput.sendMessage(sender, translate(sender, "commands.account.delete"));
        }
    }

    @picocli.CommandLine.Command(name = "resetpass")
    private final class ResetPass implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<username>")
        private String username;

        @Parameters(index = "1", paramLabel = "<password>")
        private String password;

        private ResetPass(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Account toUpdate = DatabaseHelper.getAccountByName(username);
            if (toUpdate == null) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.account.no_account"));
                return;
            }

            String passwordHash = hashPassword(sender, password);
            if (passwordHash == null) return;

            // Persist both password and token revocation before reporting success.
            AccountPasswordResetService.resetPassword(toUpdate, passwordHash);
            kickAccount(toUpdate);
            CommandOutput.sendMessage(sender, "Password Updated. Existing login tokens revoked.");
        }
    }

    private void createAccount(Player sender, String username, String password, int uid) {
        String passwordHash = password == null ? null : hashPassword(sender, password);
        if (password != null && passwordHash == null) return;

        Account account = DatabaseHelper.createAccountWithUid(username, uid);
        if (account == null) {
            CommandOutput.sendMessage(sender, translate(sender, "commands.account.exists"));
            return;
        }

        if (passwordHash != null) account.setPassword(passwordHash);
        // New console-created accounts use the same default permissions as autoCreate.
        account.save();
        CommandOutput.sendMessage(
                sender, translate(sender, "commands.account.create", account.getReservedPlayerUid()));
    }

    private String hashPassword(Player sender, String password) {
        try {
            return BCrypt.withDefaults().hashToString(12, password.toCharArray());
        } catch (IllegalArgumentException invalidPassword) {
            CommandOutput.sendMessage(sender, "Invalid password.");
            return null;
        }
    }

    private void kickAccount(Account account) {
        Player player = Grasscutter.getGameServer().getPlayerByAccountId(account.getId());
        if (player != null) player.getSession().close();
    }
}
