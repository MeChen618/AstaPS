package emu.grasscutter.game.ability;

import emu.grasscutter.net.packet.BasePacket;
import java.io.ByteArrayOutputStream;

/**
 * Hand-encoded protocol for Escoffier's improvised cooking.
 *
 * <p>The path known to work: the client draws the charge bar and sends SkillCookReq, with <strong>no</strong>
 * DataNotify from the server.
 * Getting a DataNotify field wrong puts the client into CannotCreateFood and the charge ring disappears.
 *
 * <p>The unlock/allow-cooking notify sends only remain@1 and max@2, never a refresh field; remain is 0 at
 * the cap.
 */
final class EscoffierSkillCookProto {
    private EscoffierSkillCookProto() {}

    /** Successful dish response: retcode@1 plus ItemParam@2. */
    static BasePacket buildCookRsp(int itemId, int count) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(24);
        writeUInt32Field(out, 1, 0);
        writeNestedItemParam(out, 2, itemId, count);
        return simplePacket(EscoffierSkillCookOpcodes.COOK_RSP, out.toByteArray());
    }

    /** Failed dish response. */
    static BasePacket buildCookRspError(int retcode) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8);
        writeUInt32Field(out, 1, retcode);
        return simplePacket(EscoffierSkillCookOpcodes.COOK_RSP, out.toByteArray());
    }

    /** Minimal quota packet: remain_count@1, max_count@2. */
    static BasePacket buildCookDataNotify(int usedCount, int maxWeekly, long nextResetEpochSec) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16);
        int remaining = Math.max(0, maxWeekly - usedCount);
        writeUInt32Field(out, 1, remaining);
        writeUInt32Field(out, 2, maxWeekly);
        // field3 is deliberately omitted: a refresh timestamp of the wrong type poisons CanCook and the
        // charge UI.
        return simplePacket(EscoffierSkillCookOpcodes.COOK_DATA_NOTIFY, out.toByteArray());
    }

    private static void writeNestedItemParam(
            ByteArrayOutputStream out, int fieldNumber, int itemId, int count) {
        ByteArrayOutputStream inner = new ByteArrayOutputStream(16);
        writeUInt32Field(inner, 1, itemId);
        writeUInt32Field(inner, 2, count);
        writeLenField(out, fieldNumber, inner.toByteArray());
    }

    private static BasePacket simplePacket(int opcode, byte[] data) {
        BasePacket packet = new BasePacket(opcode);
        packet.setData(data);
        return packet;
    }

    private static void writeUInt32Field(ByteArrayOutputStream out, int fieldNumber, int value) {
        writeVarint(out, (fieldNumber << 3) | 0);
        writeVarint(out, value);
    }

    private static void writeLenField(ByteArrayOutputStream out, int fieldNumber, byte[] data) {
        writeVarint(out, (fieldNumber << 3) | 2);
        writeVarint(out, data.length);
        out.writeBytes(data);
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
