package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;

/** Preserve the original keyed banip command alongside ban ip. */
@Command(
        label = "banip",
        permission = "server.banip",
        targetRequirement = Command.TargetRequirement.NONE)
public final class BanIpCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return BanCommand.createBanIpCommandLine(sender);
    }
}
