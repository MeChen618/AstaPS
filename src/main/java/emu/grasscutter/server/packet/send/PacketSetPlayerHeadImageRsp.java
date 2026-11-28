package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.ProfilePictureHelper;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetPlayerHeadImageRspOuterClass.SetPlayerHeadImageRsp;

public class PacketSetPlayerHeadImageRsp extends BasePacket {

    public PacketSetPlayerHeadImageRsp(Player player) {
        this(player, 0, 0);
    }

    public PacketSetPlayerHeadImageRsp(Player player, int retcode, int sequence) {
        super(PacketOpcodes.SetPlayerHeadImageRsp, sequence);

        SetPlayerHeadImageRsp proto =
                SetPlayerHeadImageRsp.newBuilder()
                        .setRetcode(retcode)
                        .setProfilePicture(
                                ProfilePictureHelper.toProto(player.getHeadImage()).toBuilder()
                                        .setProfileFrameId(player.getProfileFrameId()))
                        .build();

        this.setData(proto);
    }
}
