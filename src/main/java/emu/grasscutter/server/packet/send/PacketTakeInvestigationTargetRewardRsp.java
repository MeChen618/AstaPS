package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;

/** TakeInvestigationTargetRewardRsp: retcode=6, quest_id=10 */
public class PacketTakeInvestigationTargetRewardRsp extends BasePacket {
    public PacketTakeInvestigationTargetRewardRsp(int questId, int retcode) {
        super(PacketOpcodes.TakeInvestigationTargetRewardRsp);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(16);
            CodedOutputStream out = CodedOutputStream.newInstance(baos);
            out.writeInt32(6, retcode);
            out.writeUInt32(10, questId);
            out.flush();
            this.setData(baos.toByteArray());
        } catch (Exception e) {
            this.setData(new byte[0]);
        }
    }
}
