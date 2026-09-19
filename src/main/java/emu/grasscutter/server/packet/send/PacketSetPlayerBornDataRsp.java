/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.SetPlayerBornDataRspOuterClass;

public class PacketSetPlayerBornDataRsp
extends BasePacket {
    public PacketSetPlayerBornDataRsp(int n) {
        super(4259);
        this.setData(SetPlayerBornDataRspOuterClass.SetPlayerBornDataRsp.newBuilder().setRetcode(n).build());
    }
}
