package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.systems.ArtifactTransmuterSystem;
import emu.grasscutter.game.systems.ArtifactTransmuterSystem.PlayerState;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.utils.ProtoWire;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * FLIJGBNHOBC (26781) — 以 sniffer all.proto 为准（修复包 README 的 2/5 编号是错的）：
 *
 *   OPLHCEOLCFB = 2   // 本周期已萃取数量（或产出增量）；客户端 remaining = ConstMax - 此值
 *   ACDENEEFIEJ = 8   // schedule id = 700（必须）
 *   CMBJDHDHLCC = 12  // 萃取进度
 *   PECGJPBDOAI = 13  // 已定义套装列表 PDENDJIGPKH
 *   ILJNAEPANNC = 15  // 周期结束 unix
 *
 * Nested PDENDJIGPKH: FAODDMOJFNN=9 setId, NOLPABPANIH=11 count
 *
 * 私服注意：
 * - 客户端 ConstValue 萃取上限默认 1，若下发 field2=已萃次数会立刻锁 UI；故默认不下发 field2。
 * - 套装可定义次数上限在 ConstValue PURCHASE_RELIQUARY_PARAM（默认 2）；field13 会扣减剩余次数。
 */
public class PacketReliquaryOfferDataNotify extends BasePacket {

    public PacketReliquaryOfferDataNotify(PlayerState state) {
        this(state, false);
    }

    /**
     * @param announceExtracted 为 true 时下发本周期已萃取数量（需客户端 ConstMax>=10 才不会锁死）
     */
    public PacketReliquaryOfferDataNotify(PlayerState state, boolean announceExtracted) {
        super(PacketOpcodes.ReliquaryOfferDataNotify);
        if (state == null) {
            state = new PlayerState();
        }
        ArtifactTransmuterSystem.ensureCycle(state);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        if (announceExtracted && state.extractedThisCycle > 0) {
            ProtoWire.writeUint32Force(out, 2, state.extractedThisCycle);
        }

        ProtoWire.writeUint32Force(out, 8, ArtifactTransmuterSystem.SCHEDULE_ID);

        if (state.progress > 0) {
            ProtoWire.writeUint32Force(out, 12, state.progress);
        }

        // 已定义套装列表会让客户端按 ConstValue(默认每套2次) 扣减「剩余可定义」。
        // 私服若要放宽次数，默认不同步该列表，避免 UI 卡在 0/2；服务端仍可本地记账。
        boolean syncDefinedSuites = false;
        if (syncDefinedSuites && state.definedSuites != null && !state.definedSuites.isEmpty()) {
            for (Map.Entry<Integer, Integer> e : state.definedSuites.entrySet()) {
                ByteArrayOutputStream nest = new ByteArrayOutputStream();
                ProtoWire.writeUint32Force(nest, 9, e.getKey());
                ProtoWire.writeUint32Force(nest, 11, e.getValue());
                ProtoWire.writeBytes(out, 13, nest.toByteArray());
            }
        }

        ProtoWire.writeUint32Force(out, 15, ArtifactTransmuterSystem.cycleEndTime(state));
        this.setData(out.toByteArray());
    }
}
