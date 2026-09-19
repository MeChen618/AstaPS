/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.command.Command
 *  emu.grasscutter.command.CommandHandler
 *  emu.grasscutter.game.battlepass.BattlePassManager
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify
 *  emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify
 */
package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify;
import java.util.List;

@Command(label="bpbuy", usage={"<levels>"}, permission="player.setprop", permissionTargeted="player.setprop.others")
public final class BpBuyCommand
implements CommandHandler {
    public void execute(Player player, Player player2, List<String> list) {
        int n;
        Player player3;
        Player player4 = player3 = player2 != null ? player2 : player;
        if (player3 == null) {
            CommandHandler.sendMessage((Player)player, (String)"No player.");
            return;
        }
        if (list == null || list.isEmpty()) {
            CommandHandler.sendMessage((Player)player, (String)"Usage: bpbuy <levels>");
            return;
        }
        try {
            n = Integer.parseInt(list.get(0));
        }
        catch (Exception exception) {
            CommandHandler.sendMessage((Player)player, (String)"Invalid levels.");
            return;
        }
        if (n <= 0) {
            CommandHandler.sendMessage((Player)player, (String)"levels must be > 0");
            return;
        }
        BattlePassManager battlePassManager = player3.getBattlePassManager();
        if (battlePassManager == null) {
            CommandHandler.sendMessage((Player)player, (String)"No battle pass manager.");
            return;
        }
        int n2 = 50 - battlePassManager.getLevel();
        int n3 = Math.min(n, Math.max(0, n2));
        if (n3 <= 0) {
            CommandHandler.sendMessage((Player)player, (String)"Already at max BP level.");
            return;
        }
        int n4 = 150 * n3;
        if (player3.getPrimogems() < n4) {
            CommandHandler.sendMessage((Player)player, (String)("Need " + n4 + " primogems, have " + player3.getPrimogems()));
            return;
        }
        player3.setPrimogems(player3.getPrimogems() - n4);
        battlePassManager.setLevel(battlePassManager.getLevel() + n3);
        battlePassManager.save();
        player3.sendPacket((BasePacket)new PacketBattlePassCurScheduleUpdateNotify(player3));
        player3.sendPacket((BasePacket)new PacketBeyondBattlePassCurScheduleUpdateNotify(player3));
        CommandHandler.sendMessage((Player)player, (String)("Bought " + n3 + " BP levels for " + n4 + " primogems. Now level " + battlePassManager.getLevel()));
    }
}

