package emu.grasscutter.game.ability;

import emu.grasscutter.net.packet.BasePacket;
import java.io.ByteArrayOutputStream;

/**
 * 爱可菲即兴烹饪协议手工编码。
 *
 * <p>早期可工作路径：充能条由客户端绘制，配合 SkillCookReq，且<strong>不发</strong> DataNotify。
 * DataNotify 字段写错会让客户端进入 CannotCreateFood，元素充能环消失。
 *
 * <p>解锁/允许烹饪通知只发 remain@1 + max@2（不发 refresh 字段）；达上限时 remain=0。
 */
final class EscoffierSkillCookProto {
    private EscoffierSkillCookProto() {}

    /** 发菜成功回包：retcode@1 + ItemParam@2。 */
    static BasePacket buildCookRsp(int itemId, int count) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(24);
        writeUInt32Field(out, 1, 0);
        writeNestedItemParam(out, 2, itemId, count);
        return simplePacket(EscoffierSkillCookOpcodes.COOK_RSP, out.toByteArray());
    }

    /** 发菜失败回包。 */
    static BasePacket buildCookRspError(int retcode) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8);
        writeUInt32Field(out, 1, retcode);
        return simplePacket(EscoffierSkillCookOpcodes.COOK_RSP, out.toByteArray());
    }

    /** 最小额度包：remain_count@1、max_count@2。 */
    static BasePacket buildCookDataNotify(int usedCount, int maxWeekly, long nextResetEpochSec) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16);
        int remaining = Math.max(0, maxWeekly - usedCount);
        writeUInt32Field(out, 1, remaining);
        writeUInt32Field(out, 2, maxWeekly);
        // 故意省略 field3：错误类型的刷新时间戳会毒化 CanCook / 充能 UI。
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
