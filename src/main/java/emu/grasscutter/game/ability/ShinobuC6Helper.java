package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarTalentData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 久岐忍六命「割舍软弱之心」。
 *
 * <p>官方走 DoActionByEventMixin(HPDown)，本服不执行该 mixin。做法对齐 {@link HutaoC6Helper}：
 * 在服务端伤害结算里拦截——致死伤害改为留 1 血；生命值低于 25% 时临时加精通。
 * 两段效果各自约 60 秒 CD，与芭芭拉/七七的复活 CD 无关。
 */
public final class ShinobuC6Helper {
    public static final int AVATAR_ID = 10000065;
    public static final int TALENT_C6 = 656;

    private static final float DEFAULT_HP_RATIO = 0.25f;
    private static final float DEFAULT_EM = 150f;
    private static final float DEFAULT_DURATION_SEC = 15f;
    private static final long DEFAULT_CD_MS = 60_000L;

    private static final Int2LongOpenHashMap LETHAL_CD_UNTIL = new Int2LongOpenHashMap();
    private static final Int2LongOpenHashMap EM_CD_UNTIL = new Int2LongOpenHashMap();
    private static final ConcurrentHashMap<Integer, Integer> EM_TASK_IDS = new ConcurrentHashMap<>();

    static {
        LETHAL_CD_UNTIL.defaultReturnValue(0L);
        EM_CD_UNTIL.defaultReturnValue(0L);
    }

    private ShinobuC6Helper() {}

    public static void clearEntityState(int entityId) {
        LETHAL_CD_UNTIL.remove(entityId);
        EM_CD_UNTIL.remove(entityId);
        Integer taskId = EM_TASK_IDS.remove(entityId);
        if (taskId != null) {
            try {
                Grasscutter.getGameServer().getScheduler().cancelTask(taskId);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void clearPlayerState(emu.grasscutter.game.player.Player player) {
        if (player == null || player.getTeamManager() == null) return;
        for (EntityAvatar ea : player.getTeamManager().getActiveTeam()) {
            if (ea != null) clearEntityState(ea.getId());
        }
    }

    public static float filterDamage(EntityAvatar entity, float curHp, float proposedDamage) {
        if (entity == null || proposedDamage <= 0f) {
            return proposedDamage;
        }
        Avatar avatar = entity.getAvatar();
        if (avatar == null || avatar.getAvatarId() != AVATAR_ID) {
            return proposedDamage;
        }
        if (!hasC6(entity)) {
            return proposedDamage;
        }

        float maxHp = entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (maxHp <= 0f) return proposedDamage;

        float newHp = curHp - proposedDamage;
        boolean lethal = newHp <= 0f || newHp < 1f;
        boolean belowRatio = newHp / maxHp < resolveHpRatio(avatar);

        float adjusted = proposedDamage;
        int entityId = entity.getId();
        long now = System.currentTimeMillis();

        long lethalCdMs = resolveCdMs(avatar);
        if (lethal && now >= LETHAL_CD_UNTIL.get(entityId)) {
            adjusted = Math.max(0f, curHp - 1f);
            LETHAL_CD_UNTIL.put(entityId, now + lethalCdMs);
            Grasscutter.getLogger()
                    .info(
                            "[ShinobuC6] lethal intercept entity={} cur={} dmg={} -> 1hp cdMs={}",
                            entityId,
                            curHp,
                            proposedDamage,
                            lethalCdMs);
        }

        float hpAfter = curHp - adjusted;
        if ((belowRatio || hpAfter / maxHp < resolveHpRatio(avatar)) && now >= EM_CD_UNTIL.get(entityId)) {
            triggerEmBuff(entity);
        }

        return adjusted;
    }

    private static long resolveCdMs(Avatar avatar) {
        float[] params = readTalentParams(avatar);
        if (params != null && params.length > 3 && params[3] > 0f) {
            return Math.round(params[3] * 1000f);
        }
        return DEFAULT_CD_MS;
    }

    public static float filterDirectHpLoss(EntityAvatar entity, float curHp, float newHp) {
        if (entity == null || newHp >= curHp) {
            return newHp;
        }
        float damage = curHp - newHp;
        float adjustedDamage = filterDamage(entity, curHp, damage);
        return curHp - adjustedDamage;
    }

    private static void triggerEmBuff(EntityAvatar entity) {
        float em = DEFAULT_EM;
        float durationSec = DEFAULT_DURATION_SEC;
        long cdMs = DEFAULT_CD_MS;
        // Talent paramList: [hpRatio, durationSec, em, cdSec]
        float[] params = readTalentParams(entity.getAvatar());
        if (params != null) {
            if (params.length > 1 && params[1] > 0f) durationSec = params[1];
            if (params.length > 2 && params[2] > 0f) em = params[2];
            if (params.length > 3 && params[3] > 0f) cdMs = Math.round(params[3] * 1000f);
        }
        int entityId = entity.getId();
        EM_CD_UNTIL.put(entityId, System.currentTimeMillis() + cdMs);
        entity.addFightProperty(FightProperty.FIGHT_PROP_ELEMENT_MASTERY, em);
        syncEm(entity);
        scheduleEmClear(entity, durationSec, em);
        Grasscutter.getLogger()
                .info("[ShinobuC6] EM +{} for {}s entity={} cdMs={}", em, durationSec, entityId, cdMs);
    }

    private static void scheduleEmClear(EntityAvatar entity, float durationSec, float em) {
        int entityId = entity.getId();
        Integer prev = EM_TASK_IDS.remove(entityId);
        if (prev != null) {
            try {
                Grasscutter.getGameServer().getScheduler().cancelTask(prev);
            } catch (Throwable ignored) {
            }
        }
        int ticks = Math.max(1, Math.round(durationSec));
        int taskId =
                Grasscutter.getGameServer()
                        .getScheduler()
                        .scheduleDelayedTask(
                                () -> {
                                    EM_TASK_IDS.remove(entityId);
                                    try {
                                        if (entity.getScene() == null) return;
                                        entity.addFightProperty(FightProperty.FIGHT_PROP_ELEMENT_MASTERY, -em);
                                        syncEm(entity);
                                    } catch (Throwable ignored) {
                                    }
                                },
                                ticks);
        EM_TASK_IDS.put(entityId, taskId);
    }

    private static void syncEm(EntityAvatar entity) {
        try {
            entity.getScene()
                    .broadcastPacket(
                            new PacketEntityFightPropUpdateNotify(
                                    (GameEntity) entity, FightProperty.FIGHT_PROP_ELEMENT_MASTERY));
            entity.getPlayer().sendPacket(new PacketAvatarFightPropNotify(entity.getAvatar()));
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasC6(EntityAvatar entity) {
        return PartyReviveHelper.hasC6(
                entity != null ? entity.getAvatar() : null, AVATAR_ID, TALENT_C6, "Shinobu_Constellation");
    }

    private static float resolveHpRatio(Avatar avatar) {
        float[] params = readTalentParams(avatar);
        if (params != null && params.length > 0 && params[0] > 0f && params[0] <= 1f) {
            return params[0];
        }
        return DEFAULT_HP_RATIO;
    }

    private static float[] readTalentParams(Avatar avatar) {
        AvatarTalentData td = GameData.getAvatarTalentDataMap().get(TALENT_C6);
        return td != null ? td.getParamList() : null;
    }
}
