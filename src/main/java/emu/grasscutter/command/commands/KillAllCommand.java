package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;

/** Preserve the original keyed killall command alongside kill all. */
@Command(
        label = "killall",
        permission = "server.killall",
        permissionTargeted = "server.killall.others",
        targetRequirement = Command.TargetRequirement.PLAYER)
public final class KillAllCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return KillCommand.createKillAllCommandLine(sender, targetPlayer);
    }
}
