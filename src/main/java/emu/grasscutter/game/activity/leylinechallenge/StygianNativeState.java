package emu.grasscutter.game.activity.leylinechallenge;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** 7.1 IHCAHEHNKCO (1439), distinct from the battle settlement message 28119. */
public final class StygianNativeState {
    private StygianNativeState() {}

    /** One OONOGOEHGIB (field 14) round record for the round page HUD. */
    public record RoundRecord(int costTime, boolean finished, int roundIndex, int uid,
            List<Integer> avatarIds) {}

    public static BasePacket packet(int owner, int difficulty, int round, boolean battle,
            long stageBeginMillis, int gallery, List<Player> players, List<RoundRecord> records) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = CodedOutputStream.newInstance(bytes);
            out.writeUInt32(3, battle ? 2 : 1);
            out.writeFixed64(4, stageBeginMillis);
            out.writeUInt32(5, difficulty);
            out.writeUInt32(10, round);
            out.writeUInt32(12, owner);
            out.writeByteArray(6, StygianGalleryPackets.info(gallery, owner, players.size()));
            for (var player : players) {
                var pb = new ByteArrayOutputStream(); var po = CodedOutputStream.newInstance(pb);
                po.writeUInt32(6, player.getUid());
                po.writeString(12, player.getNickname()); po.flush();
                out.writeByteArray(15, pb.toByteArray());
            }
            // OONOGOEHGIB (field 14) round records: the round page HUD's progression data.
            for (var rec : records) {
                var rb = new ByteArrayOutputStream(); var ro = CodedOutputStream.newInstance(rb);
                ro.writeUInt32(1, rec.costTime());
                ro.writeBool(9, rec.finished());
                ro.writeUInt32(14, rec.roundIndex());
                var tb = new ByteArrayOutputStream(); var to = CodedOutputStream.newInstance(tb);
                to.writeUInt32(13, rec.uid());
                for (int avatarId : rec.avatarIds()) {
                    var ab = new ByteArrayOutputStream(); var ao = CodedOutputStream.newInstance(ab);
                    ao.writeUInt32(1, avatarId); ao.flush();
                    to.writeByteArray(15, ab.toByteArray());
                }
                to.flush();
                ro.writeByteArray(15, tb.toByteArray());
                ro.flush();
                out.writeByteArray(14, rb.toByteArray());
            }
            out.flush(); var packet = new BasePacket(1439); packet.setData(bytes.toByteArray());
            return packet;
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
