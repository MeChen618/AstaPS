/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.game.avatar.AvatarExtraLevelOpcodes;
import emu.grasscutter.net.packet.BasePacket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PacketAvatarExtraLevelUpgradeRsp
extends BasePacket {
    public PacketAvatarExtraLevelUpgradeRsp(long l, int n, int n2) {
        this(l, n, n2, 0);
    }

    public PacketAvatarExtraLevelUpgradeRsp(long l, int n, int n2, int n3) {
        super(AvatarExtraLevelOpcodes.resolveResponseOpcode(0));
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CodedOutputStream output = CodedOutputStream.newInstance(bytes);
            output.writeInt32(4, n3);
            output.writeUInt32(7, n2);
            output.writeUInt32(10, n);
            output.writeUInt64(15, l);
            output.flush();
            this.setData(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode extra-level response", exception);
        }
    }
}
