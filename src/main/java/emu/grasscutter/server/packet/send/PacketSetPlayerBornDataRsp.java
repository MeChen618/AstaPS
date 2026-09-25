/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.SetPlayerBornDataRspOuterClass;

public class PacketSetPlayerBornDataRsp
extends BasePacket {
    public PacketSetPlayerBornDataRsp(int n) {
        // No 7.1 CmdId is known for this response (the private port does not send it either).
        super(PacketOpcodes.SetPlayerBornDataRsp);
        this.setData(SetPlayerBornDataRspOuterClass.SetPlayerBornDataRsp.newBuilder().setRetcode(n).build());
    }
}
