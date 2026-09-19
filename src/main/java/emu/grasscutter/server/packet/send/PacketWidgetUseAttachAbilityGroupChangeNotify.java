package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.*;
import java.io.ByteArrayOutputStream;

/** Notifies the client to attach/detach a widget ability group (e.g. Endora / Mini Seelie). */
public class PacketWidgetUseAttachAbilityGroupChangeNotify extends BasePacket {

    public PacketWidgetUseAttachAbilityGroupChangeNotify(int materialId, boolean isAttach) {
        super(PacketOpcodes.WidgetUseAttachAbilityGroupChangeNotify, true);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CodedOutputStream cos = CodedOutputStream.newInstance(baos);
            // Field numbers from WidgetUseAttachAbilityGroupChangeNotify.proto / 7.0 dump
            cos.writeBool(13, isAttach);
            cos.writeUInt32(14, materialId);
            cos.flush();
            this.setData(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode WidgetUseAttachAbilityGroupChangeNotify", e);
        }
    }
}
