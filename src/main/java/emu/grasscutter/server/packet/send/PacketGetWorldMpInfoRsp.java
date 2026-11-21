package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetWorldMpInfoRspOuterClass.GetWorldMpInfoRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

public class PacketGetWorldMpInfoRsp extends BasePacket {

    public PacketGetWorldMpInfoRsp(World world) {
        super(PacketOpcodes.GetWorldMpInfoRsp);

        var proto = GetWorldMpInfoRsp.newBuilder();
        if (world == null) {
            proto.setRetcode(Retcode.RET_SVR_ERROR_VALUE);
        } else {
            proto.setIsInMpMode(world.isMultiplayer());
        }

        this.setData(proto);
    }
}
