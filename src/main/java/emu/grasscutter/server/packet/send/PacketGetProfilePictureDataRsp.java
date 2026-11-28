package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.BeyondProfilePictureTable;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetProfilePictureDataRspOuterClass.GetProfilePictureDataRsp;

import java.util.Collection;

/** Current portrait and frame choices, using the 7.2 native response layout. */
public final class PacketGetProfilePictureDataRsp extends BasePacket {
    public PacketGetProfilePictureDataRsp(Player player) {
        this(player, 0);
    }

    public PacketGetProfilePictureDataRsp(Player player, int clientSequence) {
        this(
                BeyondProfilePictureTable.all().stream().map(e -> e.id).toList(),
                BeyondProfilePictureTable.allFrameIds(),
                clientSequence);
    }

    public PacketGetProfilePictureDataRsp(
            Collection<Integer> pictures, Collection<Integer> frames, int clientSequence) {
        super(PacketOpcodes.GetProfilePictureDataRsp, clientSequence);
        setData(
                GetProfilePictureDataRsp.newBuilder()
                        .addAllSpecialProfilePictureList(pictures)
                        .addAllUnlockedProfileFrameList(frames));
    }

    /** The separate Beyond portrait payload has not been migrated to 7.2. */
    public static final int BEYOND_PROFILE_PICTURE_DATA_NOTIFY = 0;
}
