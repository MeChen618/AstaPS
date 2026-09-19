package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * MoveTeamRsp — retcode + echo backup_avatar_team_order_list when known.
 *
 * Field numbers guessed from AvatarTeamAllDataNotify (retcode=1 style, order list
 * often shares the same semantic field). We emit:
 *   field 1 varint retcode=0
 *   field 2 packed repeated uint32 order (and also unpacked as fallback field 15)
 */
public class PacketMoveTeamRsp extends BasePacket {
    public PacketMoveTeamRsp() {
        this(null);
    }

    public PacketMoveTeamRsp(List<Integer> order) {
        super(PacketOpcodes._MoveTeamRsp);
        ByteArrayOutputStream out = new ByteArrayOutputStream(64);
        // retcode = 0 (field 1)
        out.write(0x08);
        out.write(0x00);
        if (order != null && !order.isEmpty()) {
            // field 2, wire type 2 (length-delimited packed)
            writePackedRepeated(out, 2, order);
            // also field 15 unpacked varints (AvatarTeamAllDataNotify uses 15 for order)
            for (Integer id : order) {
                writeTag(out, 15, 0);
                writeVarint(out, id.intValue());
            }
        }
        setData(out.toByteArray());
    }

    public PacketMoveTeamRsp(int retcode) {
        super(PacketOpcodes._MoveTeamRsp);
        ByteArrayOutputStream out = new ByteArrayOutputStream(8);
        out.write(0x08);
        writeVarint(out, retcode);
        setData(out.toByteArray());
    }

    private static void writePackedRepeated(ByteArrayOutputStream out, int field, List<Integer> vals) {
        ByteArrayOutputStream packed = new ByteArrayOutputStream(vals.size() * 2);
        for (Integer v : vals) {
            writeVarint(packed, v.intValue());
        }
        byte[] body = packed.toByteArray();
        writeTag(out, field, 2);
        writeVarint(out, body.length);
        out.write(body, 0, body.length);
    }

    private static void writeTag(ByteArrayOutputStream out, int field, int wireType) {
        writeVarint(out, (field << 3) | wireType);
    }

    private static void writeVarint(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }
}
