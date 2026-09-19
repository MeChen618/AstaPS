/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.avatar;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.props.FightProperty;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AvatarStatePersist {
    private static final Map<Integer, float[]> PENDING = new ConcurrentHashMap<Integer, float[]>();

    private AvatarStatePersist() {
    }

    public static void stash(Avatar avatar, float f, float f2, float f3) {
        if (avatar == null) {
            return;
        }
        PENDING.put(System.identityHashCode(avatar), new float[]{f, f2, f3});
    }

    public static void afterRecalc(Avatar avatar) {
        if (avatar == null) {
            return;
        }
        float[] fArray = PENDING.remove(System.identityHashCode(avatar));
        if (fArray == null) {
            return;
        }
        try {
            float f;
            float f2;
            float f3 = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            float f4 = fArray[0];
            if (f3 > 0.0f && f4 > 0.0f) {
                if (f4 <= 1.01f && f3 > 10.0f) {
                    f2 = f3;
                } else {
                    f2 = Math.min(f4, f3);
                    if (f2 < 1.0f && f4 >= 1.0f) {
                        f2 = 1.0f;
                    }
                }
                avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, f2);
                avatar.setCurrentHp(f2);
            }
            if ((f2 = fArray[1]) > 0.0f) {
                avatar.setCurrentEnergy(f2);
            }
            if ((f = fArray[2]) > 0.0f) {
                avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, f);
                avatar.setNyxValue(f);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }
}
