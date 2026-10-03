package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.WindSeedType1NotifyOuterClass.WindSeedType1Notify;
import emu.grasscutter.utils.FileUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PacketWindSeedClientNotify extends BasePacket {
    public static final int CMD = PacketOpcodes.WindSeedType1Notify;
    public static final int FIELD = WindSeedType1Notify.PAYLOAD_FIELD_NUMBER;

    static int cmdId() {
        int configured = Configuration.GAME.watermark.cmdId;
        return configured > 0 ? configured : PacketOpcodes.WindSeedType1Notify;
    }

    private static final int DEFAULT_PAYLOAD_FIELD = WindSeedType1Notify.PAYLOAD_FIELD_NUMBER;

    public static boolean disabled() {
        return Configuration.GAME.watermark.cmdId < 0;
    }

    public static byte[] encode(byte[] luac) {
        return encode(luac, Configuration.GAME.watermark.payloadField);
    }

    static byte[] encode(byte[] luac, int field) {
        if (field <= 0) field = DEFAULT_PAYLOAD_FIELD;

        var bos = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(bos);
        try {
            out.writeByteArray(field, luac);
            out.flush();
        } catch (IOException e) {
            Grasscutter.getLogger().error("Failed to encode the wind seed payload.", e);
        }
        return bos.toByteArray();
    }

    public PacketWindSeedClientNotify(String givenPath) {
        super(cmdId());
        final Path path = Paths.get(givenPath, new String[0]);
        byte[] data;
        try {
            data = Files.readAllBytes(path);
        } catch (Exception e) {
            data = FileUtils.readResource("/lua/UID.luac");
        }

        this.setData(encode(data));
    }

    public PacketWindSeedClientNotify(byte[] data) {
        super(cmdId());
        this.setData(encode(data));
    }

    public PacketWindSeedClientNotify(byte[] data, int cmdId, int payloadField) {
        super(cmdId);
        this.setData(encode(data, payloadField));
    }
}
