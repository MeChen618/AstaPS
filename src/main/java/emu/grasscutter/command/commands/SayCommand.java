package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.command.Command.TargetRequirement;
import emu.grasscutter.game.player.Player;
import java.util.Arrays;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "say",
        permission = "server.sendmessage",
        targetRequirement = TargetRequirement.NONE,
        inlineTarget = false)
public final class SayCommand implements CommandHandler {
    record MessageArguments(String message, CommandMap.TargetSelector recipient) {}

    /** A final player selector targets one player. Other words remain message text. */
    static MessageArguments parseMessageArguments(List<String> words) {
        if (words == null || words.isEmpty()) {
            throw new IllegalArgumentException("Specify a message.");
        }
        String last = words.getLast();
        int at = last.indexOf('@');
        boolean explicitSelector = at == 0 || at == last.length() - 1
                || (at > 0 && last.substring(at + 1).matches("[0-9]+"));
        if (at >= 0 && explicitSelector) {
            if (words.size() == 1) {
                throw new IllegalArgumentException("Specify a message before the player selector.");
            }
            var selector = CommandMap.parseExplicitTargetSelector(last);
            return new MessageArguments(
                    String.join(" ", words.subList(0, words.size() - 1)), selector);
        }
        return new MessageArguments(String.join(" ", words), null);
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var cli = new CommandLine(new Args(sender));
        cli.setExpandAtFiles(false);
        return cli;
    }

    @CommandLine.Command(
            name = "say",
            customSynopsis = "say <message...> [selector]",
            description = "Omit the final selector to broadcast. Target one player with @UID, username@, or username@UID.")
    private static final class Args implements Runnable {
        private final Player sender;

        @Parameters(index = "0..*", arity = "1..*", paramLabel = "<message...> [selector]")
        private String[] words;

        private Args(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            final MessageArguments args;
            try {
                args = parseMessageArguments(Arrays.asList(words));
            } catch (IllegalArgumentException invalid) {
                CommandOutput.sendMessage(sender, invalid.getMessage());
                return;
            }

            if (args.recipient() == null) {
                for (Player player : Grasscutter.getGameServer().getPlayers().values()) {
                    CommandOutput.sendMessage(player, args.message());
                }
            } else {
                final Player selected;
                try {
                    selected = CommandMap.findPlayer(args.recipient());
                } catch (IllegalArgumentException invalid) {
                    CommandOutput.sendMessage(sender, invalid.getMessage());
                    return;
                }
                if (selected == null) {
                    CommandOutput.sendTranslatedMessage(sender, "commands.execution.player_exist_error");
                    return;
                }
                Player recipient = Grasscutter.getGameServer().getPlayerByUid(selected.getUid());
                if (recipient == null || !recipient.isOnline()
                        || recipient.getSession() == null || !recipient.getSession().isActive()) {
                    CommandOutput.sendTranslatedMessage(sender, "commands.execution.need_target_online");
                    return;
                }
                if (!Grasscutter.getPermissionHandler().checkPermission(
                        sender, recipient, "server.sendmessage", "server.sendmessage.others")) {
                    return;
                }
                CommandOutput.sendMessage(recipient, args.message());
            }
            CommandOutput.sendTranslatedMessage(sender, "commands.say.success");
        }
    }
}
