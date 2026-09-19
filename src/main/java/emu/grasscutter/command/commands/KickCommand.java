package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.player.Player;
import java.util.List;
import java.util.Objects;

@Command(
        label = "kick",
        aliases = {"restart"},
        permissionTargeted = "server.kick")
public final class KickCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args == null
                || args.isEmpty()
                || !Objects.equals(args.get(0), Configuration.HTTP_ENCRYPTION.keystorePassword)) {
            Player recipient = sender != null ? sender : targetPlayer;
            if (recipient != null) {
                CommandHandler.sendMessage(recipient, "Wrong key");
            }
            return;
        }
        args.remove(0);
        if (sender != null) {
            CommandHandler.sendTranslatedMessage(
                    sender,
                    "commands.kick.player_kick_player",
                    sender.getUid(),
                    sender.getAccount().getUsername(),
                    targetPlayer.getUid(),
                    targetPlayer.getAccount().getUsername());
        } else {
            CommandHandler.sendTranslatedMessage(
                    sender,
                    "commands.kick.server_kick_player",
                    targetPlayer.getUid(),
                    targetPlayer.getAccount().getUsername());
        }

        targetPlayer.getSession().close();
    }
}
