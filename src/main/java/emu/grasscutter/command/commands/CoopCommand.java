package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "coop",
        permission = "server.coop",
        permissionTargeted = "server.coop.others",
        inlineTarget = false)
public final class CoopCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender, targetPlayer));
        commandLine.setExpandAtFiles(false);
        commandLine.registerConverter(
                CommandMap.TargetSelector.class,
                value -> {
                    try {
                        return CommandMap.parseExplicitTargetSelector(value);
                    } catch (IllegalArgumentException invalid) {
                        throw new CommandLine.TypeConversionException(invalid.getMessage());
                    }
                });
        return commandLine;
    }

    @CommandLine.Command(name = "coop")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[<hostSelector>]")
        private CommandMap.TargetSelector hostSelector;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            Player host;
            if (hostSelector == null) {
                if (sender == null) {
                    CommandOutput.sendMessage(null, "A host selector is required from the console.");
                    return;
                }
                host = sender;
            } else {
                final Player candidate;
                try {
                    candidate = CommandMap.findPlayer(hostSelector);
                } catch (IllegalArgumentException mismatch) {
                    CommandOutput.sendMessage(sender, mismatch.getMessage());
                    return;
                }
                host = candidate == null
                        ? null
                        : Grasscutter.getGameServer().getPlayerByUid(candidate.getUid());
                if (host == null) {
                    CommandOutput.sendTranslatedMessage(sender, "commands.execution.player_offline_error");
                    return;
                }
            }

            if (targetPlayer == null) {
                CommandOutput.sendMessage(sender, "Select a guest player before using coop.");
                return;
            }
            if (host == targetPlayer) {
                CommandOutput.sendMessage(sender, "A player cannot join their own world.");
                return;
            }
            if (targetPlayer.isInMultiplayer()) {
                targetPlayer.getServer().getMultiplayerSystem().leaveCoop(targetPlayer);
            }
            host.getServer().getMultiplayerSystem().applyEnterMp(targetPlayer, host.getUid());
            targetPlayer
                    .getServer()
                    .getMultiplayerSystem()
                    .applyEnterMpReply(host, targetPlayer.getUid(), true);
            CommandOutput.sendTranslatedMessage(
                    sender, "commands.coop.success", targetPlayer.getNickname(), host.getNickname());
        }
    }
}
