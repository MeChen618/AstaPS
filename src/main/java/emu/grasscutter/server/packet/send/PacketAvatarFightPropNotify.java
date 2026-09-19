package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.ability.ArlecchinoBurstBoL;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarFightPropNotifyOuterClass.AvatarFightPropNotify;
import java.util.HashMap;

public class PacketAvatarFightPropNotify extends BasePacket {

    private static final int PROP_HP_DEBTS = FightProperty.FIGHT_PROP_CUR_HP_DEBTS.getId();
    private static final int ARLECCHINO_AVATAR_ID = 10000096;

    public PacketAvatarFightPropNotify(Avatar avatar) {
        super(PacketOpcodes.AvatarFightPropNotify);

        // A full Notify omits HP_DEBTS by default: including it after damage makes the client show the
        // damage number without the HP actually dropping.
        // Exception: it must be included while Arlecchino's Q is locked, otherwise the full Notify after
        // SkillSucc wipes the Bond of Life bar immediately
        // (the client cinematic already zeroed it locally, so a missing field in a full Notify confirms 0).
        var props = new HashMap<>(avatar.getFightProperties());
        if (!shouldKeepHpDebts(avatar)) {
            props.remove(PROP_HP_DEBTS);
        }

        AvatarFightPropNotify proto =
                AvatarFightPropNotify.newBuilder()
                        .setAvatarGuid(avatar.getGuid())
                        .putAllFightPropMap(props)
                        .build();

        this.setData(proto);
    }

    private static boolean shouldKeepHpDebts(Avatar avatar) {
        if (avatar == null || avatar.getAvatarId() != ARLECCHINO_AVATAR_ID) {
            return false;
        }
        try {
            var player = avatar.getPlayer();
            if (player == null || player.getTeamManager() == null) {
                return false;
            }
            for (EntityAvatar ent : player.getTeamManager().getActiveTeam()) {
                if (ent != null
                        && ent.getAvatar() == avatar
                        && ArlecchinoBurstBoL.isConsumeBlocked(ent.getId())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
