package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;

/** Preserve the original keyed unbanip command alongside unban ip. */
@Command(
        label = "unbanip",
        permission = "server.banip",
        targetRequirement = Command.TargetRequirement.NONE)
public final class UnBanIpCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return UnbanCommand.createUnbanIpCommandLine(sender);
    }
}
