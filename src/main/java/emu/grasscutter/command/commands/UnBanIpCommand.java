package emu.grasscutter.command.commands;

import static emu.grasscutter.config.Configuration.HTTP_ENCRYPTION;

import emu.grasscutter.command.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import java.util.List;
import java.util.Objects;

@Command(
        label = "unbanip",
        usage = {"<password> <ip>"},
        permission = "server.banip",
        targetRequirement = Command.TargetRequirement.NONE)
public final class UnBanIpCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.size() < 2) {
            this.sendUsageMessage(sender);
            return;
        }

        var password = args.get(0);
        if (!Objects.equals(password, HTTP_ENCRYPTION.keystorePassword)) {
            CommandHandler.sendMessage(sender, "Wrong key.");
            return;
        }

        var ip = args.get(1);
        if (!DatabaseHelper.removeBannedIp(ip)) {
            CommandHandler.sendMessage(sender, "No ban recorded for " + ip + ".");
            return;
        }

        // Lifting the IP ban alone would leave every account it took down still banned.
        var unbanned = DatabaseHelper.unbanAccountsBannedByIp(ip);
        CommandHandler.sendMessage(
                sender,
                unbanned > 0
                        ? "Unbanned IP " + ip + ", along with " + unbanned + " account(s)."
                        : "Unbanned IP " + ip + ".");
    }
}
