package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.BannedIp;
import emu.grasscutter.game.player.Player;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(label = "ban", targetRequirement = Command.TargetRequirement.NONE, inlineTarget = false)
public final class BanCommand implements CommandHandler {
    private static final int DEFAULT_BAN_END = 2051190000;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var cli = new CommandLine(new BanTargetCommand(sender));
        cli.setExpandAtFiles(false);
        return cli;
    }

    enum TargetType { UID, ACCOUNT, IPV4 }
    record BanTarget(TargetType type, int uid, String value) {}
    record BanArguments(BanTarget target, int endTime, String reason) {}

    static BanTarget parseTarget(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Specify @UID, account name, or IPv4 address.");
        }
        if (value.matches("@[0-9]+")) {
            try {
                int uid = Integer.parseInt(value.substring(1));
                if (uid > 0) return new BanTarget(TargetType.UID, uid, value);
            } catch (NumberFormatException ignored) {}
            throw new IllegalArgumentException("Invalid player UID: " + value);
        }
        if (value.equals("@") || value.matches("@-?[0-9]+")) {
            throw new IllegalArgumentException("Invalid player UID: " + value);
        }
        // Old dotted usernames remain resolvable unless they look like a complete IPv4.
        if (value.matches("[0-9]+(\\.[0-9]+){3}")) {
            for (String octet : value.split("\\.")) {
                if (octet.length() > 3
                        || (octet.length() > 1 && octet.startsWith("0"))
                        || Integer.parseInt(octet) > 255) {
                    throw new IllegalArgumentException("Invalid IPv4 address: " + value);
                }
            }
            return new BanTarget(TargetType.IPV4, 0, value);
        }
        return new BanTarget(TargetType.ACCOUNT, 0, value);
    }

    static BanArguments parseBanArguments(String target, List<String> trailing) {
        var parsed = parseTarget(target);
        int endTime = DEFAULT_BAN_END;
        int offset = 0;
        if (!trailing.isEmpty() && trailing.getFirst().matches("-?[0-9]+")) {
            try {
                endTime = Integer.parseInt(trailing.getFirst());
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Invalid ban end time: " + trailing.getFirst());
            }
            offset = 1;
        }
        if (parsed.type() == TargetType.IPV4 && offset != 0) {
            throw new IllegalArgumentException(
                    "IPv4 bans do not expire; omit endTime and supply only a reason.");
        }
        String reason = trailing.size() == offset
                ? "Reason not specified."
                : String.join(" ", trailing.subList(offset, trailing.size()));
        return new BanArguments(parsed, endTime, reason);
    }

    @CommandLine.Command(name = "ban",
            customSynopsis = "ban <@UID|accountName|IPv4> [endTime] [reason...]")
    private static final class BanTargetCommand implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<@UID|accountName|IPv4>")
        private String target;

        @Parameters(index = "1..*", arity = "0..*", paramLabel = "[endTime] [reason...]")
        private List<String> trailing = List.of();

        private BanTargetCommand(Player sender) { this.sender = sender; }

        @Override
        public void run() {
            final BanArguments args;
            try {
                args = parseBanArguments(target, trailing);
            } catch (IllegalArgumentException invalid) {
                CommandOutput.sendMessage(sender, invalid.getMessage());
                return;
            }

            if (args.target().type() == TargetType.IPV4) {
                if (!hasPermission(sender, "server.banip", null)) return;
                new BannedIp(args.target().value(), args.reason()).save();
                CommandOutput.sendMessage(
                        sender, "Banned IP " + args.target().value() + ". Reason: " + args.reason());
                return;
            }

            Account account;
            if (args.target().type() == TargetType.UID) {
                Player player = Grasscutter.getGameServer().getPlayerByUid(args.target().uid(), true);
                account = player == null
                        ? DatabaseHelper.getAccountByPlayerId(args.target().uid())
                        : player.getAccount();
            } else {
                account = Grasscutter.getGameServer().getAccountByName(args.target().value());
            }

            if (account == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.execution.player_exist_error");
                return;
            }

            boolean someoneElse = sender != null
                    && !sender.getAccount().getId().equals(account.getId());
            if (!hasPermission(sender, "server.ban",
                    someoneElse ? "server.ban.others" : null)) return;

            account.setBanReason(args.reason());
            account.setBanEndTime(args.endTime());
            account.setBanStartTime((int) (System.currentTimeMillis() / 1000));
            account.setBannedByIp(null);
            account.setBanned(true);
            account.save();

            Player online = Grasscutter.getGameServer().getPlayerByAccountId(account.getId());
            if (online != null && online.getSession() != null) online.getSession().close();
            CommandOutput.sendTranslatedMessage(sender, "commands.ban.success");
        }
    }

    private static boolean hasPermission(Player sender, String permission, String additional) {
        if (sender == null) return true;
        Account account = sender.getAccount();
        if (account != null && account.hasPermission(permission)
                && (additional == null || account.hasPermission(additional))) return true;
        CommandOutput.sendTranslatedMessage(sender, "commands.generic.permission_error");
        return false;
    }
}
