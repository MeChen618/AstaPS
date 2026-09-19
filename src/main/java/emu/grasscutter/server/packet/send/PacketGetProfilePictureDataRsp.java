package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.ProfilePictureHelper;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetProfilePictureDataRspOuterClass.GetProfilePictureDataRsp;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Profile-picture unlock list.
 *
 * layout 0: proto builder uint32@4 + retcode@11
 * layout 20: notify-style uint32@4 only
 */
public class PacketGetProfilePictureDataRsp extends BasePacket {

    public PacketGetProfilePictureDataRsp(Player player) {
        this(player, PacketOpcodes.GetProfilePictureDataRsp, 0, 0);
    }

    public PacketGetProfilePictureDataRsp(Player player, int clientSequence) {
        this(player, PacketOpcodes.GetProfilePictureDataRsp, 0, clientSequence);
    }

    public PacketGetProfilePictureDataRsp(Player player, int opcode, int layout, int clientSequence) {
        super(opcode, clientSequence);
        try {
            List<Integer> ids = ProfilePictureHelper.allProfilePictureIds();
            byte[] payload =
                    switch (layout) {
                        case 20 -> buildNotifyListOnly(ids, 4);
                        default -> GetProfilePictureDataRsp.newBuilder()
                                .addAllSpecialProfilePictureList(ids)
                                .setRetcode(0)
                                .build()
                                .toByteArray();
                    };
            this.setData(payload);
            Grasscutter.getLogger()
                    .info(
                            "GetProfilePictureDataRsp uid={} opcode={} layout={} seq={} pictures={} bytes={}",
                            player != null ? player.getUid() : 0,
                            opcode,
                            layout,
                            clientSequence,
                            ids.size(),
                            payload.length);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("GetProfilePictureDataRsp failed: {}", t.toString());
            this.setData(GetProfilePictureDataRsp.newBuilder().setRetcode(0).build());
        }
    }

    private static byte[] buildNotifyListOnly(List<Integer> ids, int listField) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, ids.size() * 5 + 8));
        byte[] packed = packUint32List(ids);
        writeVarint(out, (listField << 3) | 2);
        writeVarint(out, packed.length);
        out.write(packed);
        return out.toByteArray();
    }

    private static byte[] packUint32List(List<Integer> ids) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(ids.size() * 5);
        for (int id : ids) {
            writeVarint(out, id);
        }
        return out.toByteArray();
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
