package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CancelCoopTaskRspOuterClass;

public class PacketCancelCoopTaskRsp extends BasePacket {

    public PacketCancelCoopTaskRsp(int chapterId, int retcode) {
        super(PacketOpcodes.CancelCoopTaskRsp);

        CancelCoopTaskRspOuterClass.CancelCoopTaskRsp proto =
                CancelCoopTaskRspOuterClass.CancelCoopTaskRsp.newBuilder()
                        .setChapterId(chapterId).setRetcode(retcode).build();

        this.setData(proto);
    }
}
