package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.*;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.combine.CombineManger;
import emu.grasscutter.game.player.Player;
import java.util.List;
import java.util.Objects;

@Command(
        label = "reload",
        permission = "server.reload",
        targetRequirement = Command.TargetRequirement.NONE)
public final class ReloadCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args == null
                || args.isEmpty()
                || !Objects.equals(args.get(0), Configuration.HTTP_ENCRYPTION.keystorePassword)) {
            Player recipient = sender != null ? sender : targetPlayer;
            if (recipient != null) {
                CommandHandler.sendMessage(recipient, "密钥输入错误");
            }
            return;
        }
        args.remove(0);
        CommandHandler.sendMessage(sender, translate(sender, "commands.reload.reload_start"));

        Grasscutter.loadConfig();
        Grasscutter.loadLanguage();
        Grasscutter.getGameServer().getGachaSystem().load();
        Grasscutter.getGameServer().getShopSystem().load();
        CombineManger.initialize();

        CommandHandler.sendMessage(sender, translate(sender, "commands.reload.reload_done"));
    }
}
