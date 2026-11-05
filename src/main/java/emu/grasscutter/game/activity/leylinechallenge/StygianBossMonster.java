package emu.grasscutter.game.activity.leylinechallenge;

import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;

/** Only Stygian encounter bosses use a forced top health bar. */
public final class StygianBossMonster extends EntityMonster {
    public StygianBossMonster(Scene scene, MonsterData data, Position pos, Position rot, int level) {
        super(scene, data, pos, rot, level);
    }
    @Override public SceneEntityInfo toProto() { return withBossBar(super.toProto()); }
    public static SceneEntityInfo withBossBar(SceneEntityInfo info) {
        return info.toBuilder().setMonster(info.getMonster().toBuilder().setIsElite(true)).build();
    }
}
