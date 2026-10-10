package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import java.util.Map;
import picocli.CommandLine;

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

    static String formatPlayer(String nickname, int uid, boolean styled) {
        return styled
                ? nickname + " <color=green>(" + uid + ")</color>"
                : nickname + " (" + uid + ")";
    }

    @CommandLine.Command(name = "list")
    private static final class ListPlayers implements Runnable {
        private final Player sender;

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
                            .map(player -> formatPlayer(player.getNickname(), player.getUid(), sender != null))
                            .reduce((left, right) -> left + ", " + right)
                            .orElse("");
            CommandOutput.sendMessage(sender, players);
        }
    }
}
