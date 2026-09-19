/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.battlepass.BattlePassMission;
import emu.grasscutter.game.battlepass.BeyondBattlePassWireEncoder;
import emu.grasscutter.net.packet.BasePacket;
import java.util.Collection;

public class PacketBeyondBattlePassMissionUpdateNotify
extends BasePacket {
    public PacketBeyondBattlePassMissionUpdateNotify(BattlePassMission battlePassMission) {
        super(3436);
        this.setData(BeyondBattlePassWireEncoder.encodeMissionUpdateNotify(battlePassMission));
    }

    public PacketBeyondBattlePassMissionUpdateNotify(Collection<BattlePassMission> collection) {
        super(3436);
        this.setData(BeyondBattlePassWireEncoder.encodeMissionUpdateNotify(collection));
    }
}
