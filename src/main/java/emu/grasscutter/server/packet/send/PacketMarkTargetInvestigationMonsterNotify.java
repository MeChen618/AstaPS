/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.net.proto.InvestigationMonsterOuterClass$InvestigationMonster
 *  emu.grasscutter.net.proto._InvestigationMonsterConfigOuterClass$_InvestigationMonsterConfig
 *  emu.grasscutter.net.proto._InvestigationMonsterDetailOuterClass$_InvestigationMonsterDetail
 */
package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.InvestigationMonsterOuterClass;
import emu.grasscutter.net.proto._InvestigationMonsterConfigOuterClass;
import emu.grasscutter.net.proto._InvestigationMonsterDetailOuterClass;
import java.io.ByteArrayOutputStream;

public class PacketMarkTargetInvestigationMonsterNotify
extends BasePacket {
    public PacketMarkTargetInvestigationMonsterNotify(InvestigationMonsterOuterClass.InvestigationMonster investigationMonster) {
        super(1609);
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(64);
            PacketMarkTargetInvestigationMonsterNotify.writeVarint(byteArrayOutputStream, 24);
            PacketMarkTargetInvestigationMonsterNotify.writeVarint(byteArrayOutputStream, investigationMonster.getId());
            _InvestigationMonsterConfigOuterClass._InvestigationMonsterConfig monsterConfig = null;
            if (investigationMonster.getInvestigationMonsterDetailListCount() > 0) {
                _InvestigationMonsterDetailOuterClass._InvestigationMonsterDetail detail =
                        investigationMonster.getInvestigationMonsterDetailList(0);
                if (detail != null && detail.hasMonsterConfig()) {
                    monsterConfig = detail.getMonsterConfig();
                }
            }
            if (monsterConfig != null) {
                byte[] configBytes = monsterConfig.toByteArray();
                PacketMarkTargetInvestigationMonsterNotify.writeVarint(byteArrayOutputStream, 82);
                PacketMarkTargetInvestigationMonsterNotify.writeVarint(byteArrayOutputStream, configBytes.length);
                byteArrayOutputStream.write(configBytes);
                PacketMarkTargetInvestigationMonsterNotify.writeVarint(byteArrayOutputStream, 106);
                PacketMarkTargetInvestigationMonsterNotify.writeVarint(byteArrayOutputStream, configBytes.length);
                byteArrayOutputStream.write(configBytes);
            }
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
