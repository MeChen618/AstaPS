package emu.grasscutter.command.commands;

import static emu.grasscutter.config.Configuration.HTTP_ENCRYPTION;

import emu.grasscutter.command.*;
import emu.grasscutter.game.BannedIp;
import emu.grasscutter.game.player.Player;
import java.util.List;
import java.util.Objects;

@Command(
        label = "banip",
        usage = {"<password> <ip> [<reason>]"},
        permission = "server.banip",
        targetRequirement = Command.TargetRequirement.NONE)
public final class BanIpCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.size() < 2) {
            this.sendUsageMessage(sender);
            return;
        }

        // A second secret on top of the permission: this command is reachable from in-game chat,
        // and an account that gets it wrong can lock the whole server's address range out.
        var password = args.remove(0);
        if (!Objects.equals(password, HTTP_ENCRYPTION.keystorePassword)) {
            CommandHandler.sendMessage(sender, "Wrong key.");
            return;
        }

        var ip = args.remove(0);
        var reason = args.isEmpty() ? "No reason given" : String.join(" ", args);

        new BannedIp(ip, reason).save();

        CommandHandler.sendMessage(sender, "Banned IP " + ip + ". Reason: " + reason);
    }
}
