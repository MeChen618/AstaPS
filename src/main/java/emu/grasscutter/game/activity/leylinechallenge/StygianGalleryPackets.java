package emu.grasscutter.game.activity.leylinechallenge;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Minimal real gallery state, encoded against 7.1 deob71.proto, without synthetic records. */
public final class StygianGalleryPackets {
    private StygianGalleryPackets() {}
    public static int gallery(int scene, boolean battle) {
        return 83301 + (scene - 30002) * 2 + (battle ? 1 : 0);
    }
    public static byte[] info(int gallery, int owner, int players) {
        return StygianProtocol.packet(1, 15, gallery, 6, 2, 3, owner, 2, players).getData();
    }
    public static BasePacket nested(int opcode, int tag, byte[] data) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = CodedOutputStream.newInstance(bytes);
            out.writeByteArray(tag, data); out.flush();
            var packet = new BasePacket(opcode); packet.setData(bytes.toByteArray()); return packet;
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public static BasePacket notify(int gallery, int owner, int players) {
        return nested(3264, 15, info(gallery, owner, players));
    }
    public static BasePacket list(int gallery, int owner, int players) {
        return nested(9640, 1, info(gallery, owner, players));
    }
}
