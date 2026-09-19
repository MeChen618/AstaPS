package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.utils.ProtoWire;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 7.0 {@code CommonPlayerTipsNotify} (CmdId 20710).
 *
 * <pre>
 *   repeated string text_map_id_list = 2;
 *   uint32 notify_type = 14;
 * </pre>
 */
public class PacketCommonPlayerTipsNotify extends BasePacket {

    public PacketCommonPlayerTipsNotify(String textMapId, int notifyType) {
        this(List.of(textMapId), notifyType);
    }

    public PacketCommonPlayerTipsNotify(List<String> textMapIds, int notifyType) {
        super(PacketOpcodes.CommonPlayerTipsNotify);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (textMapIds != null) {
            for (String id : textMapIds) {
                if (id == null || id.isEmpty()) continue;
                ProtoWire.writeBytes(out, 2, id.getBytes(StandardCharsets.UTF_8));
            }
        }
        if (notifyType != 0) {
            ProtoWire.writeUint32Force(out, 14, notifyType);
        }
        setData(out.toByteArray());
    }
}
