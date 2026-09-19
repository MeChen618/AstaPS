/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.game.avatar.Avatar
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.net.proto.ProudSkillExtraLevelNotifyOuterClass$ProudSkillExtraLevelNotify
 */
package emu.grasscutter.game.avatar;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.ProudSkillExtraLevelNotifyOuterClass;

final class SkirkTeamBonusNotify {
    private static final int OPCODE = 4108;

    private SkirkTeamBonusNotify() {
    }

    static void send(Player player, Avatar avatar, int n, int n2) {
        if (player == null || avatar == null) {
            return;
        }
        ProudSkillExtraLevelNotifyOuterClass.ProudSkillExtraLevelNotify proudSkillExtraLevelNotify = ProudSkillExtraLevelNotifyOuterClass.ProudSkillExtraLevelNotify.newBuilder().setAvatarGuid(avatar.getGuid()).setTalentType(3).setTalentIndex(n).setExtraLevel(n2).build();
        BasePacket basePacket = new BasePacket(4108);
        basePacket.setData(proudSkillExtraLevelNotify.toByteArray());
        player.sendPacket(basePacket);
    }
}

