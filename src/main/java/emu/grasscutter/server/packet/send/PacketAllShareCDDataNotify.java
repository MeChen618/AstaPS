package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/** S2C AllShareCDDataNotify (1225) — manual encode; generated OuterClass not in this tree. */
public class PacketAllShareCDDataNotify extends BasePacket {

    public PacketAllShareCDDataNotify(int shareCdId, int chargeIndex, long cdEndTimeMs, boolean isAll) {
        super(PacketOpcodes.AllShareCDDataNotify);
        this.setData(encode(Map.of(shareCdId, new long[] {chargeIndex, cdEndTimeMs}), isAll));
    }

    public PacketAllShareCDDataNotify(Map<Integer, long[]> shareCdEnds, boolean isAll) {
        super(PacketOpcodes.AllShareCDDataNotify);
        this.setData(encode(shareCdEnds, isAll));
    }

    /**
     * @param shareCdEnds map shareCdId → {chargeIndex, cdEndTimeMs}
     */
    private static byte[] encode(Map<Integer, long[]> shareCdEnds, boolean isAll) {
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(128);
            CodedOutputStream out = CodedOutputStream.newInstance(bout);

            // bool is_all = 7;
            if (isAll) {
                out.writeBool(7, true);
            }

            // map<uint32, ShareCDInfo> share_cd_info_map = 15;
            for (Map.Entry<Integer, long[]> e : shareCdEnds.entrySet()) {
                int shareCdId = e.getKey();
                long[] pair = e.getValue();
                int chargeIndex = (int) pair[0];
                long cdEndTimeMs = pair[1];

                byte[] value = encodeShareCdInfo(shareCdId, chargeIndex, cdEndTimeMs);
                byte[] entry = encodeMapEntry(shareCdId, value);
                out.writeTag(15, 2); // length-delimited
                out.writeUInt32NoTag(entry.length);
                out.writeRawBytes(entry);
            }

            out.flush();
            return bout.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("AllShareCDDataNotify encode failed", ex);
        }
    }

    private static byte[] encodeMapEntry(int key, byte[] value) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(32 + value.length);
        CodedOutputStream out = CodedOutputStream.newInstance(bout);
        out.writeUInt32(1, key);
        out.writeTag(2, 2);
        out.writeUInt32NoTag(value.length);
        out.writeRawBytes(value);
        out.flush();
        return bout.toByteArray();
    }

    private static byte[] encodeShareCdInfo(int shareCdId, int chargeIndex, long cdEndTimeMs)
            throws IOException {
        byte[] shareCd = encodeShareCd(chargeIndex, cdEndTimeMs);
        ByteArrayOutputStream bout = new ByteArrayOutputStream(24 + shareCd.length);
        CodedOutputStream out = CodedOutputStream.newInstance(bout);
        // repeated _ShareCD _share_cd_list = 1;
        out.writeTag(1, 2);
        out.writeUInt32NoTag(shareCd.length);
        out.writeRawBytes(shareCd);
        // uint32 share_cd_id = 3;
        out.writeUInt32(3, shareCdId);
        out.flush();
        return bout.toByteArray();
    }

    private static byte[] encodeShareCd(int chargeIndex, long cdEndTimeMs) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(24);
        CodedOutputStream out = CodedOutputStream.newInstance(bout);
        // uint32 HABMJKBLIMA = 12;  (charge index)
        if (chargeIndex != 0) {
            out.writeUInt32(12, chargeIndex);
        } else {
            // still write 0 so client sees an explicit slot
            out.writeUInt32(12, 0);
        }
        // uint64 _cd_end_time = 14;
        out.writeUInt64(14, cdEndTimeMs);
        out.flush();
        return bout.toByteArray();
    }
}
