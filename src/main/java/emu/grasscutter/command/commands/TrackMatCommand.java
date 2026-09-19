package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.SpecialtyMaterialTrackHelper;
import java.util.List;

@Command(
        label = "trackmat",
        aliases = {"trackmaterial", "mattrack"},
        usage = {"<itemId|materialName>", "clear"},
        permission = "player.teleport",
        permissionTargeted = "player.teleport.others",
        targetRequirement = Command.TargetRequirement.PLAYER)
public final class TrackMatCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args == null || args.isEmpty()) {
            CommandHandler.sendMessage(
                    sender,
                    "Usage: /trackmat <itemId|name>  or  /trackmat clear\nExample: /trackmat 101253");
            return;
        }
        if ("clear".equalsIgnoreCase(args.get(0))) {
            int n = SpecialtyMaterialTrackHelper.clear(targetPlayer);
            CommandHandler.sendMessage(sender, "Cleared " + n + " material map markers.");
            return;
        }
        int itemId = SpecialtyMaterialTrackHelper.resolveItemId(String.join(" ", args));
        if (itemId <= 0) {
            CommandHandler.sendMessage(sender, "Unrecognised material: " + String.join(" ", args));
            return;
        }
        int n = SpecialtyMaterialTrackHelper.track(targetPlayer, itemId);
        if (n <= 0) {
            CommandHandler.sendMessage(sender, "No specialty point data found for itemId=" + itemId + ".");
            return;
        }
        CommandHandler.sendMessage(
                sender,
                "Marked material "
                        + itemId
                        + ", "
                        + n
                        + " gather points, on the world map. Use /trackmat clear to remove them.");
    }
}
