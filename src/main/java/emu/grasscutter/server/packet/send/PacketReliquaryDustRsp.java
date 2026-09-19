package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.utils.ProtoWire;

import java.io.ByteArrayOutputStream;

/** S2C 21898 LCFOIJBCGOB — ReliquaryDustRsp (retcode only). */
public class PacketReliquaryDustRsp extends BasePacket {
    public static final int OPCODE = 21898;

    public PacketReliquaryDustRsp(int retcode) {
        super(OPCODE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProtoWire.writeUint32Force(out, 6, retcode); // field retcode = 6
        this.setData(out.toByteArray());
    }
}
