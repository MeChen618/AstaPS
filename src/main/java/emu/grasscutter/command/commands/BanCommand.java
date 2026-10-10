package emu.grasscutter.command.commands;

import static emu.grasscutter.config.Configuration.HTTP_ENCRYPTION;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.BannedIp;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.game.GameSession;
import java.util.List;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "ban",
        targetRequirement = Command.TargetRequirement.NONE,
        inlineTarget = false)
public final class BanCommand implements CommandHandler {
    private static final int DEFAULT_BAN_END = 2051190000;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.setExpandAtFiles(false);
        commandLine.addSubcommand("player", new BanPlayer(sender));
        commandLine.addSubcommand("ip", new BanIp(sender));
        return commandLine;
    }

    @CommandLine.Command(name = "ban")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            BanCommand.this.sendUsageMessage(sender);
        }
    }

    record PlayerBanArguments(int uid, int endTime, String reason) {}

    /**
     * The player UID is mandatory and positional; unlike other GM commands, ban player
     * does not consume an arbitrary @UID elsewhere in the argument list.
     * A nonnumeric first trailing word starts the reason (with default end time).
     */
    static PlayerBanArguments parsePlayerArguments(String selector, List<String> trailingWords) {
        if (selector == null || !selector.matches("@[0-9]+")) {
            throw new IllegalArgumentException("Player UID must use @<digits> syntax.");
        }

        final int uid;
        try {
            uid = Integer.parseInt(selector.substring(1));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid player UID: " + selector);
        }
        if (uid <= 0) throw new IllegalArgumentException("Invalid player UID: " + selector);

        int endTime = DEFAULT_BAN_END;
        int reasonStart = 0;
        if (!trailingWords.isEmpty() && trailingWords.get(0).matches("-?[0-9]+")) {
            try {
                endTime = Integer.parseInt(trailingWords.get(0));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Invalid ban end time: " + trailingWords.get(0));
            }
            reasonStart = 1;
        }

        String reason = reasonStart == trailingWords.size()
                ? "Reason not specified."
                : String.join(" ", trailingWords.subList(reasonStart, trailingWords.size()));
        return new PlayerBanArguments(uid, endTime, reason);
    }

    @CommandLine.Command(
            name = "player",
            customSynopsis = "ban player @UID [endTime] [reason...]")
    private static final class BanPlayer implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "@UID")
        private String targetUid;

        @Parameters(index = "1..*", arity = "0..*", paramLabel = "[endTime] [reason...]")
        private List<String> remaining = List.of();

        private BanPlayer(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            final PlayerBanArguments args;
            try {
                args = parsePlayerArguments(targetUid, remaining);
            } catch (IllegalArgumentException invalid) {
                CommandOutput.sendMessage(sender, invalid.getMessage());
                return;
            }

            Player targetPlayer = Grasscutter.getGameServer().getPlayerByUid(args.uid(), true);
            if (targetPlayer == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.execution.player_exist_error");
                return;
            }
            if (!hasPermission(sender, targetPlayer, "server.ban", "server.ban.others")) return;

            Account account = targetPlayer.getAccount();
            if (account == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.ban.failure");
                return;
            }

            account.setBanReason(args.reason());
            account.setBanEndTime(args.endTime());
            account.setBanStartTime((int) (System.currentTimeMillis() / 1000));
            account.setBanned(true);
            account.save();

            GameSession session = targetPlayer.getSession();
            if (session != null) session.close();
            CommandOutput.sendTranslatedMessage(sender, "commands.ban.success");
        }
    }

    @CommandLine.Command(name = "ip")
    private static final class BanIp implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<key>")
        private String key;

        @Parameters(index = "1", paramLabel = "<ip>")
        private String ip;

        @Parameters(index = "2..*", arity = "0..*", paramLabel = "[reason]")
        private String[] reasonWords = new String[0];

        private BanIp(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (!hasPermission(sender, sender, "server.banip", "server.banip")) return;
            if (!Objects.equals(key, HTTP_ENCRYPTION.keystorePassword)) {
                CommandOutput.sendMessage(sender, "Wrong key.");
                return;
            }
            String reason = reasonWords.length == 0 ? "No reason given" : String.join(" ", reasonWords);
            new BannedIp(ip, reason).save();
            CommandOutput.sendMessage(sender, "Banned IP " + ip + ". Reason: " + reason);
        }
    }

    private static boolean hasPermission(
            Player sender, Player targetPlayer, String permission, String permissionTargeted) {
        if (sender == null) return true;
        var account = sender.getAccount();
        String required = targetPlayer != null && targetPlayer != sender ? permissionTargeted : permission;
        if (account != null && account.hasPermission(required)) return true;
        CommandOutput.sendTranslatedMessage(sender, "commands.generic.permission_error");
        return false;
    }
}
