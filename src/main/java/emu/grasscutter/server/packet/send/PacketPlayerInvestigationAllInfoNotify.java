package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;
import java.util.Collection;

/**
 * Adventurer Handbook 见闻 full sync ({@code PlayerInvestigationAllInfoNotify}).
 *
 * <p>Uses 7.0 all-in-one.proto field numbers (no generated OuterClass in this tree).
 */
public class PacketPlayerInvestigationAllInfoNotify extends BasePacket {

    public record InvestigationInfo(int id, int progress, int totalProgress, int state) {}

    public record TargetInfo(
            int investigationId, int questId, int progress, int totalProgress, int state) {}

    public PacketPlayerInvestigationAllInfoNotify(
            Collection<InvestigationInfo> investigations, Collection<TargetInfo> targets) {
        super(PacketOpcodes.PlayerInvestigationAllInfoNotify);
        this.setData(buildAllInfo(investigations, targets));
    }

    /** Also push chapter list alone (PlayerInvestigationNotify). */
    public static BasePacket asChapterNotify(Collection<InvestigationInfo> investigations) {
        BasePacket pkt = new BasePacket(PacketOpcodes.PlayerInvestigationNotify);
        pkt.setData(buildChapterNotify(investigations));
        return pkt;
    }

    /** Also push targets alone (PlayerInvestigationTargetNotify). */
    public static BasePacket asTargetNotify(Collection<TargetInfo> targets) {
        BasePacket pkt = new BasePacket(PacketOpcodes.PlayerInvestigationTargetNotify);
        pkt.setData(buildTargetNotify(targets));
        return pkt;
    }

    private static byte[] buildAllInfo(
            Collection<InvestigationInfo> investigations, Collection<TargetInfo> targets) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
            CodedOutputStream out = CodedOutputStream.newInstance(baos);
            // 7.0: repeated Investigation investigation_list = 12
            if (investigations != null) {
                for (InvestigationInfo inv : investigations) {
                    out.writeByteArray(12, buildInvestigationBytes(inv));
                }
            }
            // Dump lists targets as bytes=5; 6.6 used repeated on 2. Write both.
            if (targets != null && !targets.isEmpty()) {
                ByteArrayOutputStream blob = new ByteArrayOutputStream(targets.size() * 24);
                CodedOutputStream blobOut = CodedOutputStream.newInstance(blob);
                for (TargetInfo t : targets) {
                    // Concatenate target messages (no outer tags) into bytes field 5
                    byte[] msg = buildTargetBytes(t);
                    blobOut.write(msg, 0, msg.length);
                }
                blobOut.flush();
                out.writeByteArray(5, blob.toByteArray());
                // Compatibility: also emit as repeated messages on field 2 (6.6 layout)
                for (TargetInfo t : targets) {
                    out.writeByteArray(2, buildTargetBytes(t));
                }
            }
            out.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static byte[] buildChapterNotify(Collection<InvestigationInfo> investigations) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
            CodedOutputStream out = CodedOutputStream.newInstance(baos);
            // 7.0: repeated Investigation investigation_list = 7
            if (investigations != null) {
                for (InvestigationInfo inv : investigations) {
                    out.writeByteArray(7, buildInvestigationBytes(inv));
                }
            }
            out.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static byte[] buildTargetNotify(Collection<TargetInfo> targets) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
            CodedOutputStream out = CodedOutputStream.newInstance(baos);
            // 7.0: repeated InvestigationTarget investigation_target_list = 2
            if (targets != null) {
                for (TargetInfo t : targets) {
                    out.writeByteArray(2, buildTargetBytes(t));
                }
            }
            out.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    /** Investigation { total_progress=2; id=6; progress=9; state=15 } */
    private static byte[] buildInvestigationBytes(InvestigationInfo inv) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(24);
        CodedOutputStream out = CodedOutputStream.newInstance(baos);
        out.writeUInt32(2, inv.totalProgress());
        out.writeUInt32(6, inv.id());
        out.writeUInt32(9, inv.progress());
        out.writeEnum(15, inv.state());
        out.flush();
        return baos.toByteArray();
    }

    /** InvestigationTarget { quest_id=5; progress=7; investigation_id=9; total_progress=13; state=15 } */
    private static byte[] buildTargetBytes(TargetInfo t) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(32);
        CodedOutputStream out = CodedOutputStream.newInstance(baos);
        out.writeUInt32(5, t.questId());
        out.writeUInt32(7, t.progress());
        out.writeUInt32(9, t.investigationId());
        out.writeUInt32(13, t.totalProgress());
        out.writeEnum(15, t.state());
        out.flush();
        return baos.toByteArray();
    }
}
