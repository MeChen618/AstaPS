package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

/** Query or override an open state separately from player properties. */
@Command(
        label = "openstate",
        permission = "player.setprop",
        permissionTargeted = "player.setprop.others")
public final class OpenStateCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "openstate")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<openStateId>")
        private int id;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[0|1]")
        private Integer value;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (!GameData.getOpenStateDataMap().containsKey(id)) {
                CommandOutput.sendMessage(sender, "Unknown open state: " + id);
                return;
            }
            if (value == null) {
                CommandOutput.sendMessage(sender,
                        "Open state " + id + " = "
                                + targetPlayer.getProgressManager().getOpenState(id));
                return;
            }
            if (value != 0 && value != 1) {
                CommandOutput.sendMessage(sender, "Open state value must be 0 or 1.");
                return;
            }

            targetPlayer.getProgressManager().forceSetOpenState(id, value);
            targetPlayer.save();
            CommandOutput.sendMessage(sender, "Open state " + id + " = " + value);
        }
    }
}
