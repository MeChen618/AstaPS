package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;

/** TakeInvestigationRewardRsp: id=1, retcode=14 */
public class PacketTakeInvestigationRewardRsp extends BasePacket {
    public PacketTakeInvestigationRewardRsp(int chapterId, int retcode) {
        super(PacketOpcodes.TakeInvestigationRewardRsp);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(16);
            CodedOutputStream out = CodedOutputStream.newInstance(baos);
            out.writeUInt32(1, chapterId);
            out.writeInt32(14, retcode);
            out.flush();
            this.setData(baos.toByteArray());
        } catch (Exception e) {
            this.setData(new byte[0]);
        }
    }
}
