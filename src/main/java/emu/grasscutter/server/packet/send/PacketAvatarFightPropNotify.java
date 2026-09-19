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

        // 全量 Notify 默认不带 HP_DEBTS：受伤后带上会导致客户端显示伤害但不掉血。
        // 例外：阿蕾 Q 锁定期间必须带上，否则 SkillSucc 后的全量 Notify 会立刻把契条刷没
        //（客户端 cinematic 已本地清零，全量缺字段 = 确认 0）。
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
