package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;

/** AddSeenMonsterNotify (opcode 9934): monster_id_list = repeated field 4. */
public class PacketAddSeenMonsterNotify extends BasePacket {
    public PacketAddSeenMonsterNotify(int monsterId) {
        super(PacketOpcodes.AddSeenMonsterNotify);
        this.setData(build(monsterId));
    }

    private static byte[] build(int monsterId) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(16);
            CodedOutputStream cos = CodedOutputStream.newInstance(baos);
            if (monsterId > 0) {
                cos.writeUInt32(4, monsterId);
            }
            cos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
