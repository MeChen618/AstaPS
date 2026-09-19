package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.utils.ProtoWire;

import java.io.ByteArrayOutputStream;

/** SetReliquaryStarStateRsp. */
public class PacketSetReliquaryStarStateRsp extends BasePacket {
    public PacketSetReliquaryStarStateRsp(long guid, boolean starred) {
        super(PacketOpcodes.SetReliquaryStarStateRsp);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProtoWire.writeUint32Force(out, 15, 0); // retcode
        if (starred) {
            ProtoWire.writeUint32Force(out, 12, 1);
        }
        ProtoWire.writeTag(out, 13, 0);
        ProtoWire.writeVarint(out, guid);
        this.setData(out.toByteArray());
    }
}
