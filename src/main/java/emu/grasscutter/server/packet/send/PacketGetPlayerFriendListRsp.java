package emu.grasscutter.server.packet.send;

import static emu.grasscutter.config.Configuration.GAME_INFO;

import emu.grasscutter.GameConstants;
import emu.grasscutter.config.ConfigContainer.ConsoleAccount;
import emu.grasscutter.game.chat.PasswordFriendHandler;
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
        // All three are added, none replaces another: Chiori, password change and DPS.
        proto.addFriendList(buildBotFriend(GameConstants.SERVER_CONSOLE_UID, GAME_INFO.serverAccount));
        proto.addFriendList(buildPasswordFriend());
        proto.addFriendList(buildBotFriend(GameConstants.SERVER_DPS_UID, resolveDpsAccount()));

        for (Friendship friendship : player.getFriendsList().getFriends().values()) {
            proto.addFriendList(friendship.toProto());
        }

        this.setData(proto);
    }

    private static FriendBrief buildPasswordFriend() {
        return FriendBrief.newBuilder()
                .setUid(PasswordFriendHandler.UID)
                .setNickname(PasswordFriendHandler.NICKNAME)
                .setLevel(PasswordFriendHandler.LEVEL)
                .setProfilePicture(
                        ProfilePicture.newBuilder().setAvatarId(PasswordFriendHandler.AVATAR_ID))
                .setWorldLevel(PasswordFriendHandler.WORLD_LEVEL)
                .setSignature(PasswordFriendHandler.SIGNATURE)
                .setLastActiveTime((int) (System.currentTimeMillis() / 1000f))
                .setNameCardId(PasswordFriendHandler.NAME_CARD_ID)
                .setOnlineState(FriendOnlineState.FriendOnlineState_FRIEND_ONLINE)
                .setIsMpModeAvailable(false)
                .setIsGameSource(true)
                .setParam(0)
                .setPlatformType(PlatformTypeOuterClass.PlatformType.PlatformType_CLOUD_PC)
                .setIsInDuel(false)
                .setIsDuelObservable(false)
                .build();
    }

    private static ConsoleAccount resolveDpsAccount() {
        try {
            var field = GAME_INFO.getClass().getField("dpsAccount");
            Object value = field.get(GAME_INFO);
            if (value instanceof ConsoleAccount account) {
                return account;
            }
        } catch (Throwable ignored) {
        }
        ConsoleAccount fallback = new ConsoleAccount();
        fallback.nickName = "DPS";
        fallback.signature = "Send dps30 to start, dpsstop to end early";
        fallback.adventureRank = 60;
        fallback.avatarId =
                GAME_INFO.serverAccount != null ? GAME_INFO.serverAccount.avatarId : 10000007;
        fallback.nameCardId =
                GAME_INFO.serverAccount != null ? GAME_INFO.serverAccount.nameCardId : 210001;
        return fallback;
    }

    private static FriendBrief buildBotFriend(int uid, ConsoleAccount account) {
        if (account == null) {
            account = new ConsoleAccount();
        }
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
