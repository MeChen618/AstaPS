package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@Command(label = "player", targetRequirement = Command.TargetRequirement.NONE, inlineTarget = false)
public final class PlayerCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("list", new ListPlayers(sender));
        return commandLine;
    }

    @CommandLine.Command(name = "player")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            PlayerCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "list")
    private static final class ListPlayers implements Runnable {
        private final Player sender;

        @Option(names = "--uid", description = "Include player UIDs in the list")
        private boolean includeUid;

        private ListPlayers(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Map<Integer, Player> playersMap = Grasscutter.getGameServer().getPlayers();

            CommandOutput.sendMessage(sender, translate(sender, "commands.player.success", playersMap.size()));
            if (playersMap.isEmpty()) return;

            String players =
                    playersMap.values().stream()
                            .map(
                                    player -> {
                                        if (!includeUid) return player.getNickname();
                                        if (sender != null) {
                                            return player.getNickname()
                                                    + " <color=green>("
                                                    + player.getUid()
                                                    + ")</color>";
                                        }
                                        return player.getNickname() + " (" + player.getUid() + ")";
                                    })
                            .reduce((left, right) -> left + ", " + right)
                            .orElse("");
            CommandOutput.sendMessage(sender, players);
        }
    }
}
