package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.utils.ProtoWire;
import java.io.ByteArrayOutputStream;

/**
 * 7.0 {@code _ShowLoadingScreenNotify} (CmdId 21177). Used for the Spiral Abyss mid-half black
 * screen while the official tip UI ({@code UI_TOWER_INSTAGE_LOADING_TIP}) is driven by MidLevel.
 *
 * <pre>
 *   uint32 _template_loading_id = 1;
 *   float  OCPLMKIMGDI          = 9;
 *   float  duration             = 12;
 *   float  OCPDAMPEMIA          = 14;
 * </pre>
 */
public class PacketShowLoadingScreenNotify extends BasePacket {

    public PacketShowLoadingScreenNotify(float durationSeconds) {
        this(0, durationSeconds);
    }

    public PacketShowLoadingScreenNotify(int templateLoadingId, float durationSeconds) {
        super(PacketOpcodes.ShowLoadingScreenNotify);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (templateLoadingId > 0) {
            ProtoWire.writeUint32Force(out, 1, templateLoadingId);
        }
        writeFloat(out, 9, 0f);
        writeFloat(out, 12, durationSeconds);
        writeFloat(out, 14, 0f);
        setData(out.toByteArray());
    }

    private static void writeFloat(ByteArrayOutputStream out, int fieldNumber, float value) {
        ProtoWire.writeFixed32(out, fieldNumber, Float.floatToIntBits(value));
    }
}
