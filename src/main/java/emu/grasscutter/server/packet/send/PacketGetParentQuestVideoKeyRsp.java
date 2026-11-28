package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetParentQuestVideoKeyRspOuterClass.GetParentQuestVideoKeyRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

public final class PacketGetParentQuestVideoKeyRsp extends BasePacket {
    public PacketGetParentQuestVideoKeyRsp(int parentQuestId) {
        super(PacketOpcodes.GetParentQuestVideoKeyRsp);

        var proto = GetParentQuestVideoKeyRsp.newBuilder().setParentQuestId(parentQuestId);
        var key = GameData.getParentQuestVideoKeys().get(parentQuestId);
        if (key == null) {
            proto.setRetcode(Retcode.RET_FAIL_VALUE);
        } else {
            proto.setVideoKey1(key.key1()).setVideoKey2(key.key2());
        }
        setData(proto);
    }
}
