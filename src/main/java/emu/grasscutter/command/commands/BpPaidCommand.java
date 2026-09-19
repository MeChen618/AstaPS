/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.command.Command
 *  emu.grasscutter.command.CommandHandler
 *  emu.grasscutter.game.battlepass.BattlePassManager
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.server.packet.send.PacketBattlePassAllDataNotify
 *  emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify
 *  emu.grasscutter.server.packet.send.PacketBeyondBattlePassAllDataNotify
 *  emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify
 */
package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.battlepass.BattlePassCompatHelper;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.packet.send.PacketBattlePassAllDataNotify;
import emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassAllDataNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify;
import java.util.List;

@Command(label="bppaid", usage={"<true|false|on|off|1|0>"}, permission="player.setprop", permissionTargeted="player.setprop.others")
public final class BpPaidCommand
implements CommandHandler {
    public void execute(Player player, Player player2, List<String> list) {
        boolean bl;
        Player player3;
        Player player4 = player3 = player2 != null ? player2 : player;
        if (player3 == null) {
            CommandHandler.sendMessage((Player)player, (String)"No player.");
            return;
        }
        BattlePassManager battlePassManager = player3.getBattlePassManager();
        if (battlePassManager == null) {
            CommandHandler.sendMessage((Player)player, (String)"No battle pass manager.");
            return;
        }
        if (list == null || list.isEmpty()) {
            CommandHandler.sendMessage((Player)player, (String)("Pearl BP paid=" + battlePassManager.isPaid() + " (usage: bppaid true|false)"));
            return;
        }
        String string = list.get(0).trim().toLowerCase();
        boolean bl2 = bl = string.equals("true") || string.equals("on") || string.equals("1") || string.equals("yes") || string.equals("paid");
        if (!(bl || string.equals("false") || string.equals("off") || string.equals("0") || string.equals("no") || string.equals("free"))) {
            CommandHandler.sendMessage((Player)player, (String)"Usage: bppaid true|false");
            return;
        }
        if (!BattlePassCompatHelper.setPaidFlag(battlePassManager, bl)) {
            CommandHandler.sendMessage((Player)player, (String)"setPaidFlag failed");
            return;
        }
        battlePassManager.save();
        player3.sendPacket((BasePacket)new PacketBattlePassAllDataNotify(player3));
        player3.sendPacket((BasePacket)new PacketBattlePassCurScheduleUpdateNotify(player3));
        player3.sendPacket((BasePacket)new PacketBeyondBattlePassAllDataNotify(player3));
        player3.sendPacket((BasePacket)new PacketBeyondBattlePassCurScheduleUpdateNotify(player3));
        CommandHandler.sendMessage((Player)player, (String)("Pearl BP paid set to " + bl + " (isPaid=" + battlePassManager.isPaid() + ")"));
    }
}

