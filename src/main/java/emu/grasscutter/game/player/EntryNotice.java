package emu.grasscutter.game.player;

import emu.grasscutter.BuildConfig;
import emu.grasscutter.server.packet.send.PacketAntiAddictNotify;
import java.util.Objects;

/**
 * The message box shown once the player is in the world: a welcome the first time, and afterwards
 * a notice when the server has been rebuilt since the player was last in. The live announcements
 * are sent at the same moment.
 *
 * <p>The client only renders an AntiAddictNotify once it is fully in the world; sent during the
 * login handshake it is swallowed. It used to hang off TowerAllDataReq, whose 7.1 CmdId is not
 * known, so it never went out; PostEnterSceneReq arrives after every scene entry, and this sends
 * at most once per login whichever request gets here first.
 */
public final class EntryNotice {
    private EntryNotice() {}

    public static void sendOnce(Player player) {
        if (player == null || player.isEntryNoticeChecked()) return;
        player.setEntryNoticeChecked(true);

        if (player.isPendingWelcomeNotice()) {
            player.sendPacket(new PacketAntiAddictNotify(1, "Welcome to AstaPS"));
            player.setPendingWelcomeNotice(false);
        } else if (!Objects.equals(player.getLastSeenBuildHash(), BuildConfig.GIT_HASH)) {
            player.sendPacket(
                    new PacketAntiAddictNotify(
                            1, "The server has been updated and restarted since you were last online."));
        }

        player.setLastSeenBuildHash(BuildConfig.GIT_HASH);

        // The announcement banner is otherwise only broadcast every few minutes.
        var server = player.getServer();
        if (server != null && server.getAnnouncementSystem() != null) {
            server.getAnnouncementSystem().sendActive(player);
        }
    }
}
