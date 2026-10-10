package emu.grasscutter.net.proto;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import java.io.IOException;

/**
 * Wire parser for the 7.1 ascension request. The client sends {@code uint64 guid = 3}, while the
 * port's AvatarPromoteReq schema, generated from protocol/7.1/protocol.desc, declares guid at field
 * 12 - so parseFrom() reads 0 out of a real request and the guid has to come off the wire instead.
 */
public final class AvatarPromoteReqParser {
    private static final int CLIENT_GUID_FIELD = 3;

    private AvatarPromoteReqParser() {}

    /** The avatar guid carried by an ascension request, or 0 when the payload carries none. */
    public static long parseGuid(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return 0;
        }
        try {
            long guid = readVarintField(payload, CLIENT_GUID_FIELD);
            if (guid > 0) {
                return guid;
            }
        } catch (IOException ignored) {
            // Fall through to the schema the descriptor defines.
        }
        try {
            return AvatarPromoteReqOuterClass.AvatarPromoteReq.parseFrom(payload).getGuid();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static long readVarintField(byte[] payload, int field) throws IOException {
        CodedInputStream in = CodedInputStream.newInstance(payload);
        while (!in.isAtEnd()) {
            int tag = in.readTag();
            if (tag == 0) {
                break;
            }
            if ((tag >>> 3) == field && (tag & 7) == WireFormat.WIRETYPE_VARINT) {
                return in.readUInt64();
            }
            if (!in.skipField(tag)) {
                break;
            }
        }
        return 0;
    }
}
