package emu.grasscutter.game.chat;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketPrivateChatNotify;

/**
 * 好友「改密」：私聊直接发新密码即可改密（UID=98）。
 */
public final class PasswordFriendHandler {
    public static final int UID = 98;
    public static final String NICKNAME = "改密";
    public static final String SIGNATURE = "直接发送新密码即可改密";
    public static final int AVATAR_ID = 10000007;
    public static final int NAME_CARD_ID = 210001;
    public static final int LEVEL = 60;
    public static final int WORLD_LEVEL = 8;

    private PasswordFriendHandler() {}

    public static boolean isPasswordFriend(int uid) {
        return uid == UID;
    }

    /**
     * @return true 表示已消费该私聊（不应再当普通聊天/命令处理）
     */
    public static boolean handlePrivateMessage(Player player, String message) {
        if (player == null) return true;
        String raw = message == null ? "" : message.trim();

        // 回显玩家消息
        player.sendPacket(
                new PacketPrivateChatNotify(
                        player.getUid(), UID, raw.isEmpty() ? " " : raw));

        if (raw.isEmpty()) {
            reply(player, "请直接发送新密码。例如：abc123");
            return true;
        }
        if (raw.startsWith("/") || raw.startsWith("!")) {
            reply(player, "请直接发送新密码，不要加 / 。例如：abc123");
            return true;
        }
        if (raw.contains("&&")) {
            reply(player, "密码里不要包含 &&");
            return true;
        }
        if (raw.length() > 32) {
            reply(player, "密码太长，请控制在 32 位以内");
            return true;
        }

        try {
            Account account = player.getAccount();
            if (account == null) {
                reply(player, "改密失败：账号不存在");
                return true;
            }
            account.setPassword(raw);
            account.save();
            Grasscutter.getLogger()
                    .info(
                            "PasswordFriend DM: uid={} user={} password updated",
                            player.getUid(),
                            account.getUsername());
            reply(
                    player,
                    "密码已修改成功！\n新登录："
                            + account.getUsername()
                            + "&&"
                            + raw
                            + "\n下次请用这个登录。");
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("PasswordFriend DM fail uid=" + player.getUid(), t);
            reply(player, "改密失败：" + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
        }
        return true;
    }

    private static void reply(Player player, String text) {
        try {
            player.sendPacket(new PacketPrivateChatNotify(UID, player.getUid(), text));
        } catch (Throwable ignored) {
        }
    }
}
