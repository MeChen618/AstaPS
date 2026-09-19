package emu.grasscutter.game.player;

import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import emu.grasscutter.server.packet.send.PacketVehiclePhlogistonPointsNotify;

/**
 * Natlan's exploration fuel.
 *
 * <p>There is one tank and it never empties. The party's server global value is the whole system:
 * Saurians no longer carry a second tank of their own, and every request to spend fuel is answered
 * by re-asserting a full one.
 *
 * <p>The client keeps its own copy of the gauge and drains it locally as you glide or swim, so
 * pinning the value server-side is not enough on its own - the notify has to go back out on every
 * attempt to spend, which is what puts the needle back at full.
 */
public final class Phlogiston {
    public static final String TEAM_KEY = "SGV_PlayerTeam_Phlogiston";
    public static final float TEAM_MAX = 100f;
    public static final float VEHICLE_MAX = 50f;

    private Phlogiston() {}

    /**
     * Refills the party's tank and tells the client about it.
     *
     * <p>Takes no amount: nothing spends fuel any more, and the callers that used to compute a cost
     * call this instead so the gauge is corrected at exactly the moments the client expects news
     * about it.
     */
    public static void refill(Player player) {
        if (player == null) return;

        player.setPhlogistonValue(TEAM_MAX);

        var teamManager = player.getTeamManager();
        if (teamManager == null || teamManager.getEntity() == null) return;

        player.sendPacket(
                new PacketServerGlobalValueChangeNotify(
                        teamManager.getEntity().getId(), TEAM_KEY, TEAM_MAX));
    }

    /**
     * Refills a Saurian's gauge along with the party's.
     *
     * <p>A ridden Saurian draws its own gauge from a separate packet, so it has to be topped up
     * too or the mount runs dry while the party tank reads full.
     */
    public static void refill(EntityVehicle vehicle) {
        if (vehicle == null) return;

        vehicle.setCurPhlogiston(VEHICLE_MAX);

        var owner = vehicle.getOwner();
        if (owner == null) return;

        owner.sendPacket(new PacketVehiclePhlogistonPointsNotify(vehicle));
        refill(owner);
    }

    /** Refills whichever tank the ability was spending from. */
    public static void refill(Player player, Object owner) {
        if (owner instanceof EntityVehicle vehicle) {
            refill(vehicle);
            return;
        }
        refill(player);
    }
}
