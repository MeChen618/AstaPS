package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.SpecialtyMaterialTrackHelper;
import java.util.List;

@Command(
        label = "trackmat",
        aliases = {"trackmaterial", "mattrack", "采集追踪"},
        usage = {"<itemId|材料名>", "clear"},
        permission = "player.teleport",
        permissionTargeted = "player.teleport.others",
        targetRequirement = Command.TargetRequirement.PLAYER)
public final class TrackMatCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args == null || args.isEmpty()) {
            CommandHandler.sendMessage(
                    sender, "用法: /trackmat <物品ID|名称>  或  /trackmat clear\n例: /trackmat 101253  或  /trackmat 枯叶紫英");
            return;
        }
        if ("clear".equalsIgnoreCase(args.get(0)) || "清除".equals(args.get(0))) {
            int n = SpecialtyMaterialTrackHelper.clear(targetPlayer);
            CommandHandler.sendMessage(sender, "已清除材料地图标记 " + n + " 个，请打开大世界地图查看。");
            return;
        }
        int itemId = SpecialtyMaterialTrackHelper.resolveItemId(String.join(" ", args));
        if (itemId <= 0) {
            CommandHandler.sendMessage(sender, "无法识别材料: " + String.join(" ", args));
            return;
        }
        int n = SpecialtyMaterialTrackHelper.track(targetPlayer, itemId);
        if (n <= 0) {
            CommandHandler.sendMessage(sender, "没有找到 itemId=" + itemId + " 的特产点位数据。");
            return;
        }
        CommandHandler.sendMessage(
                sender,
                "已在大世界地图标记材料 "
                        + itemId
                        + " 共 "
                        + n
                        + " 处（采集类标点）。请打开地图查看；用 /trackmat clear 清除。");
    }
}
