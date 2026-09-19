/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.GetBattlePassProductRsp;

public class PacketGetBattlePassProductRsp
extends BasePacket {
    public PacketGetBattlePassProductRsp(String string, String string2, int n, int n2) {
        super(27358);
        this.setData(
                GetBattlePassProductRsp.newBuilder()
                        .setProductId(string != null ? string : "10201")
                        .setRetcode(0)
                        .setBattlePassProductPlayType(n)
                        .setCurScheduleId(n2)
                        .setPriceTier(string2 != null ? string2 : "Tier_ugcbp_5")
                        .build());
    }
}
