package emu.grasscutter.game.activity.leylinechallenge;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Native 7.1 wire fields; see deob71.proto. No 7.0 command IDs are reused. */
public final class StygianProtocol {
    private StygianProtocol() {}

    public static Map<Integer, Integer> fields(byte[] payload) throws IOException {
        var result = new HashMap<Integer, Integer>();
        var in = CodedInputStream.newInstance(payload);
        for (int tag; (tag = in.readTag()) != 0;) {
            if ((tag & 7) == 0) result.put(tag >>> 3, in.readUInt32());
            else if (!in.skipField(tag)) throw new IOException("Unexpected end group");
        }
        return result;
    }

    public static BasePacket packet(int opcode, int... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException();
        try {
            var bytes = new ByteArrayOutputStream();
            var out = CodedOutputStream.newInstance(bytes);
            for (int i = 0; i < pairs.length; i += 2)
                if (pairs[i + 1] != 0) out.writeUInt32(pairs[i], pairs[i + 1]);
            out.flush();
            var packet = new BasePacket(opcode);
            packet.setData(bytes.toByteArray());
            return packet;
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public static BasePacket start(int type, int dungeon, int code) {
        return packet(1006, 3, code, 4, type, 8, dungeon);
    }
    public static BasePacket info(int dungeon, int host, int now) {
        return packet(28008, 3, 1, 6, host, 9, 60, 10, now, 12, dungeon);
    }
    public static BasePacket success(int dungeon, int host, int end) {
        return packet(29718, 1, 1, 6, end, 10, host, 13, dungeon);
    }
    public static BasePacket stop(int host, int reason) {
        return packet(20667, 1, reason, 4, 1, 6, host);
    }
    public static BasePacket confirm(int type, boolean agree, boolean guest, int code) {
        return guest ? packet(27953, 4, agree ? 1 : 0, 7, code, 11, type)
                     : packet(2735, 5, type, 9, agree ? 1 : 0, 13, code);
    }
}
