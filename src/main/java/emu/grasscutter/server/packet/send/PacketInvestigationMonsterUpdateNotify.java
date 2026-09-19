/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.net.proto.InvestigationMonsterOuterClass$InvestigationMonster
 */
package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.InvestigationMonsterOuterClass;
import java.io.ByteArrayOutputStream;

public class PacketInvestigationMonsterUpdateNotify
extends BasePacket {
    public PacketInvestigationMonsterUpdateNotify(InvestigationMonsterOuterClass.InvestigationMonster investigationMonster) {
        super(7840);
        try {
            byte[] byArray = investigationMonster.toByteArray();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(8 + byArray.length);
            byteArrayOutputStream.write(16);
            byteArrayOutputStream.write(0);
            PacketInvestigationMonsterUpdateNotify.writeVarint(byteArrayOutputStream, 26);
            PacketInvestigationMonsterUpdateNotify.writeVarint(byteArrayOutputStream, byArray.length);
            byteArrayOutputStream.write(byArray);
            this.setData(byteArrayOutputStream.toByteArray());
        }
        catch (Exception exception) {
            this.setData(new byte[0]);
        }
    }

    private static void writeVarint(ByteArrayOutputStream byteArrayOutputStream, int n) {
        int n2 = n;
        while ((n2 & 0xFFFFFF80) != 0) {
            byteArrayOutputStream.write(n2 & 0x7F | 0x80);
            n2 >>>= 7;
        }
        byteArrayOutputStream.write(n2);
    }
}

