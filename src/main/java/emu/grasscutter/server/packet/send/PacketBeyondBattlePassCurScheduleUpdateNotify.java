/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.battlepass.BattlePassCompatHelper;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.battlepass.BeyondBattlePassWireEncoder;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;

public class PacketBeyondBattlePassCurScheduleUpdateNotify
extends BasePacket {
    public PacketBeyondBattlePassCurScheduleUpdateNotify(Player player) {
        super(7804);
        BattlePassManager battlePassManager = player != null ? player.getBattlePassManager() : null;
        this.setData(BeyondBattlePassWireEncoder.encodeCurScheduleUpdateNotify(battlePassManager));
        BattlePassCompatHelper.logBuilt(player, "BeyondCurScheduleUpdate");
    }
}
