package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;

/** Legacy energy/er/e aliases alongside restore energy. */
@Command(
        label = "er",
        aliases = {"e", "energy"},
        permission = "player.setprop",
        permissionTargeted = "player.setprop.others")
public final class erCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(
                (Runnable)
                        () -> {
                            RestoreCommand.restoreEnergy(targetPlayer);
                            CommandOutput.sendMessage(sender, "Restored elemental energy successfully.");
                        });
    }
}
