/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.command.Command
 *  emu.grasscutter.command.CommandHandler
 *  emu.grasscutter.game.avatar.Avatar
 *  emu.grasscutter.game.entity.EntityAvatar
 *  emu.grasscutter.game.player.Player
 */
package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.avatar.AvatarExtraLevelHelper;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import java.util.List;

@Command(label="extralevel", aliases={"el", "levelbreak"}, usage={"[avatarId]"}, permission="player.give", permissionTargeted="player.give.others")
public final class ExtraLevelCommand
implements CommandHandler {
    public void execute(Player player, Player player2, List<String> list) {
        int n;
        Avatar avatar;
        block9: {
            if (player2 == null) {
                CommandHandler.sendMessage((Player)player, (String)"No target player.");
                return;
            }
            avatar = null;
            if (list != null && !list.isEmpty()) {
                try {
                    n = Integer.parseInt(list.get(0));
                    avatar = player2.getAvatars().getAvatarById(n);
                    if (avatar == null) {
                        CommandHandler.sendMessage((Player)player, (String)("Avatar not found: " + n));
                        return;
                    }
                    break block9;
                }
                catch (NumberFormatException numberFormatException) {
                    CommandHandler.sendMessage((Player)player, (String)"Usage: /extralevel [avatarId]");
                    return;
                }
            }
            EntityAvatar entityAvatar = player2.getTeamManager().getCurrentAvatarEntity();
            if (entityAvatar != null) {
                avatar = entityAvatar.getAvatar();
            }
        }
        if (avatar == null) {
            CommandHandler.sendMessage((Player)player, (String)"No current avatar.");
            return;
        }
        n = avatar.getLevel();
        boolean bl = AvatarExtraLevelHelper.upgradeAvatar(player2, avatar);
        if (bl) {
            CommandHandler.sendMessage((Player)player, (String)("Extra level OK: avatar " + avatar.getAvatarId() + " " + n + " -> " + avatar.getLevel() + " (cost 104300)"));
        } else {
            CommandHandler.sendMessage((Player)player, (String)("Extra level failed: need promote=6 and level 90 or 95, plus enough 104300. Now level=" + avatar.getLevel() + " promote=" + avatar.getPromoteLevel()));
        }
    }
}

