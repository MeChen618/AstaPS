package emu.grasscutter.game.chat;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketPrivateChatNotify;

/**
 * The "Change Password" system friend (UID=98): whisper it a new password to change yours.
 */
public final class PasswordFriendHandler {
    public static final int UID = 98;
    public static final String NICKNAME = "Change Password";
    public static final String SIGNATURE = "Send a new password to change yours";
    public static final int AVATAR_ID = 10000007;
    public static final int NAME_CARD_ID = 210001;
    public static final int LEVEL = 60;
    public static final int WORLD_LEVEL = 8;

    private PasswordFriendHandler() {}

    public static boolean isPasswordFriend(int uid) {
        return uid == UID;
    }

    /**
     * @return true when the whisper was consumed and must not be handled as chat or a command
     */
    public static boolean handlePrivateMessage(Player player, String message) {
        if (player == null) return true;
        String raw = message == null ? "" : message.trim();

        // Echo the player's own message back.
        player.sendPacket(
                new PacketPrivateChatNotify(
                        player.getUid(), UID, raw.isEmpty() ? " " : raw));

        if (raw.isEmpty()) {
            reply(player, "Just send the new password, for example: abc123");
            return true;
        }
        if (raw.startsWith("/") || raw.startsWith("!")) {
            reply(player, "Send the new password without a leading /, for example: abc123");
            return true;
        }
        if (raw.contains("&&")) {
            reply(player, "The password must not contain &&");
            return true;
        }
        if (raw.length() > 32) {
            reply(player, "The password is too long; keep it to 32 characters or fewer");
            return true;
        }

        try {
            Account account = player.getAccount();
            if (account == null) {
                reply(player, "Password change failed: no such account");
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
                    "Password changed successfully.\nNew login: "
                            + account.getUsername()
                            + "&&"
                            + raw
                            + "\nUse this next time you log in.");
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("PasswordFriend DM fail uid=" + player.getUid(), t);
            reply(player, "Password change failed: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
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
