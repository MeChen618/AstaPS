package emu.grasscutter.server.packet.send;

import static emu.grasscutter.config.Configuration.GAME;

import emu.grasscutter.GameConstants;
import emu.grasscutter.config.ConfigContainer.ConsoleAccount;
import emu.grasscutter.game.friends.Friendship;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.FriendBriefOuterClass.FriendBrief;
import emu.grasscutter.net.proto.FriendOnlineStateOuterClass.FriendOnlineState;
import emu.grasscutter.net.proto.GetPlayerFriendListRspOuterClass.GetPlayerFriendListRsp;
import emu.grasscutter.net.proto.ProfilePictureOuterClass.ProfilePicture;

public class PacketGetPlayerFriendListRsp extends BasePacket {

    public PacketGetPlayerFriendListRsp(Player player) {
        super(PacketOpcodes.GetPlayerFriendListRsp);

        GetPlayerFriendListRsp.Builder proto = GetPlayerFriendListRsp.newBuilder();
        proto.addFriendList(buildBotFriend(GameConstants.SERVER_CONSOLE_UID, GAME.serverAccount));
        proto.addFriendList(buildBotFriend(GameConstants.SERVER_DPS_UID, GAME.dpsAccount));

        for (Friendship friendship : player.getFriendsList().getFriends().values()) {
            proto.addFriendList(friendship.toProto());
        }

        this.setData(proto);
    }

    private static FriendBrief buildBotFriend(int uid, ConsoleAccount account) {
        if (account == null) account = new ConsoleAccount();
        return FriendBrief.newBuilder()
                .setUid(uid)
                .setNickname(account.nickName)
                .setLevel(account.adventureRank)
                .setProfilePicture(ProfilePicture.newBuilder().setAvatarId(account.avatarId))
                .setWorldLevel(account.worldLevel)
                .setSignature(account.signature != null ? account.signature : "")
                .setLastActiveTime((int) (System.currentTimeMillis() / 1000f))
                .setNameCardId(account.nameCardId)
                .setOnlineState(FriendOnlineState.FriendOnlineState_FRIEND_ONLINE)
                .setIsMpModeAvailable(true)
                .setIsGameSource(true)
                .setParam(0)
                .setPlatformType(PlatformTypeOuterClass.PlatformType.PlatformType_CLOUD_PC)
                .setIsInDuel(false)
                .setIsDuelObservable(false)
                .build();
    }
}
