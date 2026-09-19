package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.ChangeHpDebtsReasonOuterClass.ChangeHpDebtsReason;
import emu.grasscutter.net.proto.ForwardTypeOuterClass.ForwardType;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.packet.send.PacketAbilityInvocationsNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

/**
 * Clorinde Bond of Life — 官方：驰猎是「发射铳枪时」赋契，不依赖命中。
 *
 * <ul>
 *   <li>开 E 不加契
 *   <li>夜巡中每次驰猎开火（GunShot / ShotGun_Attack）+35%MaxHP（契&lt;100%MaxHP）
 *   <li>大招按天赋加契
 *   <li>夜巡中：贯夜以外治疗 → 转契；贯夜 → 回血扣契
 * </ul>
 */
public final class ClorindeBoLUtil {
    public static final int CLORINDE_AVATAR_ID = 10000098;
    public static final int SKILL_NA = 10981;
    public static final int SKILL_E = 10982;
    public static final int SKILL_DODGE = 10983;
    public static final int SKILL_Q = 10985;

    private static final String ACTIVE_FLAG = "_ABILITY_Clorinde_ElementalArt_Active_Flag";
    private static final String DODGE_HEAL_FLAG = "_ABILITY_Clorinde_Dodge_HealFlag";
    private static final String HP_DEBTS_ENHANCED = "_ABILITY_Clorinde_ElementalArt_HpDebts_Enhanced";
    private static final String DODGE_ENHANCED = "_ABILITY_Clorinde_DodgeEnhanced";

    private static final float SHOT_RATIO = 0.35f;
    private static final float HEAL_TO_BOL_RATIO = 0.8f;
    /** 天赋缺省：贯夜治疗量 契&lt;100% / 契≥100%（详细属性 104% / 110%） */
    private static final float DODGE_HEAL_RATIO_HAS_DEBT = 1.04f;
    private static final float DODGE_HEAL_RATIO_FULL_DEBT = 1.10f;
    private static final String FORBID_FOOD_HEAL = "_ABILITY_Avatar_ForbidFoodHeal";
    /** 贯夜治疗去重（技能开始结算 + HealHP 可能双触发） */
    private static final long DODGE_HEAL_ICD_MS = 500L;
    /** 同一发铳枪多条 action 去重；需短于驰猎连发间隔 */
    private static final long SHOT_ICD_MS = 220L;
    private static final long VIGIL_MS = 9000L;
    /** 贯夜短窗口：仅用于治疗识别辅助，不再用来拦截驰猎赋契 */
    private static final long DODGE_WINDOW_MS = 800L;

    private static final Int2LongOpenHashMap LAST_SHOT_GRANT_MS = new Int2LongOpenHashMap();
    private static final Int2LongOpenHashMap LAST_DODGE_HEAL_MS = new Int2LongOpenHashMap();
    private static final Int2LongOpenHashMap VIGIL_UNTIL_MS = new Int2LongOpenHashMap();
    private static final Int2LongOpenHashMap DODGE_UNTIL_MS = new Int2LongOpenHashMap();
    private static final ThreadLocal<Float> HEAL_SAVED_BOL = new ThreadLocal<>();

    private ClorindeBoLUtil() {}

    public static boolean isClorinde(EntityAvatar avatar) {
        return avatar != null
                && avatar.getAvatar() != null
                && avatar.getAvatar().getAvatarId() == CLORINDE_AVATAR_ID;
    }

    public static boolean isNightVigil(EntityAvatar avatar) {
        if (avatar == null) {
            return false;
        }
        if (globalFlag(avatar, ACTIVE_FLAG) > 0.01f) {
            return true;
        }
        return VIGIL_UNTIL_MS.getOrDefault(avatar.getId(), 0L) > System.currentTimeMillis();
    }

    public static boolean isDodgeHeal(EntityAvatar avatar) {
        if (avatar == null) {
            return false;
        }
        // 只用短时窗口；不要信粘住的全局 Dodge_HealFlag（贯夜后会一直为1，导致再也加不了契）
        long until = DODGE_UNTIL_MS.getOrDefault(avatar.getId(), 0L);
        long now = System.currentTimeMillis();
        if (until > now) {
            return true;
        }
        // 窗口已过：清掉我们写过的粘性 flag，避免后续误判
        if (avatar.getGlobalAbilityValues() != null
                && globalFlag(avatar, DODGE_HEAL_FLAG) > 0.01f) {
            avatar.getGlobalAbilityValues().put(DODGE_HEAL_FLAG, Float.valueOf(0f));
        }
        return false;
    }

    private static float globalFlag(EntityAvatar avatar, String key) {
        if (avatar == null || avatar.getGlobalAbilityValues() == null) {
            return 0f;
        }
        Object v = avatar.getGlobalAbilityValues().get(key);
        if (v instanceof Float f) {
            return f;
        }
        if (v instanceof Number n) {
            return n.floatValue();
        }
        return 0f;
    }

    public static void applyFromSkillHook(EntityAvatar avatar, float ratio) {
        Grasscutter.getLogger()
                .info("[BoL][Clorinde] stock applyClorindeBoL ignored ratio=" + ratio);
    }

    public static void applyNoArgHook(EntityAvatar avatar) {
        // Dodge_HealFlag 清零时的误加契 — 忽略
    }

    /**
     * 受伤后用 UpdateNotify 补推 HP_DEBTS + 强化档。
     * 全量 AvatarFightPropNotify 不能带契（会不掉血），也不能不补推（契条/贯夜档会丢）。
     */
    public static void onDamaged(EntityAvatar avatar) {
        if (!isClorinde(avatar)) {
            return;
        }
        float bol = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (bol <= 0.5f || maxHp <= 1f) {
            return;
        }
        Player player = avatar.getPlayer();
        if (player != null && avatar.getAvatar() != null) {
            try {
                player.sendPacket(
                        new PacketAvatarFightPropUpdateNotify(
                                avatar.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
            } catch (Exception ignored) {
            }
        }
        if (avatar.getScene() != null) {
            try {
                avatar.getScene()
                        .broadcastPacket(
                                new PacketEntityFightPropUpdateNotify(
                                        avatar, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
            } catch (Exception ignored) {
            }
        }
        syncClorindeDebtTiers(avatar, bol, maxHp, true);
    }

    public static void onSkillStart(EntityAvatar avatar, int skillId) {
        if (!isClorinde(avatar)) {
            return;
        }
        if (skillId == SKILL_E) {
            if (avatar.getGlobalAbilityValues() != null) {
                avatar.getGlobalAbilityValues().put(ACTIVE_FLAG, Float.valueOf(1f));
            }
            pushGlobalFloat(avatar, ACTIVE_FLAG, 1f);
            pushGlobalFloat(avatar, DODGE_HEAL_FLAG, 0f);
            VIGIL_UNTIL_MS.put(avatar.getId(), System.currentTimeMillis() + VIGIL_MS);
            // 按当前真实契量刷新强化档（清掉上次贯夜 keepTier 残留的假满契）
            float bolNow = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
            float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            syncClorindeDebtTiers(avatar, bolNow, maxHp, true);
            Grasscutter.getLogger()
                    .info(
                            "[BoL][Clorinde] E start — no BoL grant, vigil on, bol="
                                    + bolNow
                                    + " tier="
                                    + debtTier(bolNow, maxHp));
            return;
        }
        if (skillId == SKILL_DODGE) {
            DODGE_UNTIL_MS.put(avatar.getId(), System.currentTimeMillis() + DODGE_WINDOW_MS);
            // 贯夜起手前先按当前契锁住强化档；heal 清契后也不能立刻把档位打成 0
            float bolNow = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
            float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            syncClorindeDebtTiers(avatar, bolNow, maxHp, true);
            pushGlobalFloat(avatar, DODGE_HEAL_FLAG, 0f);
            Grasscutter.getLogger()
                    .info(
                            "[BoL][Clorinde] Dodge/贯夜 window open tier="
                                    + debtTier(bolNow, maxHp)
                                    + " bol="
                                    + bolNow);
            // 不依赖客户端 Predicated 才治疗；有契就按契×比例结算还契回血
            tryDodgeHealFromSkill(avatar);
            return;
        }
        if (skillId == SKILL_Q) {
            onBurst(avatar);
            return;
        }
        // NA skill 上报不稳定且易与 GunShot 双计；赋契主路径走 gunshot-action
    }

    public static void onBurst(EntityAvatar avatar) {
        if (!isClorinde(avatar)) {
            return;
        }
        grant(avatar, burstBondRatio(avatar.getAvatar()), "burst");
    }

    /**
     * GunShot 任意 action 执行 = 铳枪已发射（官方 onAbilityStart.AddHPDebts），不要求命中。
     * 注意：不要用 isDodgeHeal 窗口拦赋契——连点贯夜会刷新窗口，导致后续驰猎永远加不上契。
     */
    public static void onAbilityAction(Ability ability, GameEntity target) {
        EntityAvatar avatar = resolveAvatar(ability, target);
        if (!isClorinde(avatar) || !isNightVigil(avatar)) {
            return;
        }
        String name = abilityName(ability);
        if (name.contains("ElementalArt_Dodge") && !name.contains("GunShot")) {
            return;
        }
        if (name.contains("ElementalArt_GunShot") || name.contains("GunShot")) {
            onSwiftHuntCast(avatar, "gunshot-action");
        }
    }

    /**
     * 驰猎动画 modifier（ShotGun_Attack*）挂上 = 开火，不要求命中。
     */
    public static void onModifierChange(Player player, AbilityInvokeEntry entry) {
        if (player == null || entry == null || entry.getAbilityData() == null) {
            return;
        }
        EntityAvatar avatar = player.getTeamManager().getCurrentAvatarEntity();
        if (!isClorinde(avatar) || !isNightVigil(avatar)) {
            return;
        }
        try {
            AbilityMetaModifierChange meta =
                    AbilityMetaModifierChange.parseFrom(entry.getAbilityData());
            if (meta == null || meta.getAction() != ModifierAction.MODIFIER_ACTION_ADDED) {
                return;
            }
            String abilityName = resolveAbilityString(meta.getParentAbilityName());
            if (abilityName == null) {
                abilityName = resolveAbilityString(meta.getParentAbilityOverride());
            }
            if (abilityName == null) {
                return;
            }
            if (abilityName.contains("ElementalArt_Dodge") && !abilityName.contains("GunShot")) {
                return;
            }
            if (abilityName.contains("ElementalArt_GunShot") || abilityName.contains("GunShot")) {
                onSwiftHuntCast(avatar, "mod-gunshot");
                return;
            }
            if (!abilityName.contains("ElementalArt_Activate")
                    && !abilityName.contains("ElementalArt")) {
                return;
            }
            String modName = resolveModifierName(abilityName, meta.getModifierLocalId());
            if (modName == null) {
                return;
            }
            String lower = modName.toLowerCase();
            if (lower.contains("shotgun_attack")
                    || lower.contains("skillobj_ani_shotgun_attack")
                    || lower.contains("elementalart_attack")) {
                onSwiftHuntCast(avatar, "mod-" + modName);
            }
        } catch (Exception e) {
            Grasscutter.getLogger().debug("[BoL][Clorinde] onModifierChange fail", e);
        }
    }

    /** 命中兜底：部分包体 GunShot action 不上报时仍赋契；与开火共用 ICD 防双加。 */
    public static boolean isSwiftHuntHit(AttackResult attackResult) {
        if (attackResult == null) {
            return true;
        }
        String anim = attackResult.getAnimEventId();
        if (anim == null || anim.isEmpty()) {
            return true;
        }
        String lower = anim.toLowerCase();
        if (lower.contains("dodge")
                || lower.contains("burst")
                || lower.contains("extraattack")
                || lower.contains("plunge")
                || lower.contains("elementalburst")) {
            return false;
        }
        return true;
    }

    public static void onCombatHit(EntityAvatar avatar, AttackResult attackResult) {
        if (!isClorinde(avatar) || !isNightVigil(avatar)) {
            return;
        }
        if (!isSwiftHuntHit(attackResult)) {
            return;
        }
        onSwiftHuntCast(avatar, "hit-fallback");
    }

    public static void onNightVigilShot(EntityAvatar avatar) {
        onSwiftHuntCast(avatar, "gunshot");
    }

    public static void onSwiftHuntCast(EntityAvatar avatar, String reason) {
        if (!isClorinde(avatar) || !isNightVigil(avatar)) {
            return;
        }
        // 贯夜窗口不再拦截赋契（连点贯夜会刷新窗口，否则驰猎永远加不上契）
        float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (maxHp <= 1f) {
            return;
        }
        float bol = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        // 已满契：官方不再加契，但必须维持强化档，否则客户端不判定强化普攻/契令
        if (bol + 0.5f >= maxHp) {
            syncClorindeDebtTiers(avatar, bol, maxHp, true);
            return;
        }
        int eid = avatar.getId();
        long now = System.currentTimeMillis();
        // executeAction 在线程池并发，必须同步 ICD，否则同一发会双加
        synchronized (LAST_SHOT_GRANT_MS) {
            if (LAST_SHOT_GRANT_MS.containsKey(eid)
                    && now - LAST_SHOT_GRANT_MS.get(eid) < SHOT_ICD_MS) {
                return;
            }
            LAST_SHOT_GRANT_MS.put(eid, now);
        }
        grant(avatar, SHOT_RATIO, reason);
    }

    public static boolean handleAddHpDebts(
            Ability ability, AbilityModifierAction action, GameEntity target) {
        EntityAvatar avatar = resolveAvatar(ability, target);
        if (!isClorinde(avatar)) {
            return false;
        }
        String name = abilityName(ability);
        if (name.contains("ElementalArt_GunShot") || name.contains("GunShot")) {
            onSwiftHuntCast(avatar, "addhpdebts");
            return true;
        }
        if (name.contains("ElementalBurst")) {
            return true;
        }
        return false;
    }

    public static boolean handleReduceHpDebts(Ability ability, GameEntity target) {
        EntityAvatar avatar = resolveAvatar(ability, target);
        if (!isClorinde(avatar)) {
            return false;
        }
        if (!isNightVigil(avatar)) {
            return false;
        }
        String name = abilityName(ability);
        boolean dodge =
                name.contains("Dodge")
                        || name.contains("ElementalArt_Heal")
                        || isDodgeHeal(avatar);
        if (!dodge) {
            Grasscutter.getLogger().info("[BoL][Clorinde] block ReduceHPDebts in vigil name=" + name);
            return true;
        }
        return false;
    }

    public static boolean handleHealHp(
            Ability ability, AbilityModifierAction action, GameEntity target) {
        // 夜巡转契 / 贯夜回血看的是「受治疗者」是否为克洛琳德，不是 ability owner
        EntityAvatar avatar = resolveClorindeHealTarget(ability, target);
        if (!isClorinde(avatar)) {
            return false;
        }
        String name = abilityName(ability);
        String healTag = action != null ? action.healTag : null;
        boolean dodgeHeal =
                "Clorinde_ElementalArt_Heal".equals(healTag)
                        || name.contains("ElementalArt_Dodge")
                        || name.contains("ElementalArt_Heal")
                        || isDodgeHeal(avatar);

        if (dodgeHeal) {
            return executeDodgeHeal(ability, action, avatar, "healhp");
        }

        if (isNightVigil(avatar)) {
            float amount = resolveHealAmount(ability, action, avatar);
            float ratio = healToBolRatio(avatar);
            float add = Math.max(0f, amount) * ratio;
            if (add > 0.5f) {
                float before = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
                float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
                float next = Math.min(before + add, maxHp * 2f);
                pushBoL(
                        avatar,
                        next,
                        ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_ADD_ABILITY);
                Grasscutter.getLogger()
                        .info(
                                "[BoL][Clorinde] heal→BoL amount="
                                        + amount
                                        + " ratio="
                                        + ratio
                                        + " "
                                        + before
                                        + " -> "
                                        + next);
            }
            return true;
        }

        return false;
    }

    /** 技能 10983 上报时直接结算；与 HealHP 共用 ICD 防双结算。 */
    private static void tryDodgeHealFromSkill(EntityAvatar avatar) {
        float bol = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (bol <= 0.5f) {
            Grasscutter.getLogger().info("[BoL][Clorinde] 贯夜 skill — no BoL to heal");
            return;
        }
        executeDodgeHeal(null, null, avatar, "skill-10983");
    }

    /**
     * 贯夜：治疗量 = 当前契 × HasDebt/FullDebt 比例（天赋 104%/110%）。
     * 官方不是单独「清空契」，而是治疗量高于契，经 {@link GameEntity#heal} 先还契再回血。
     * 须避开 ActionHealHP 在 ForbidFoodHeal/convertToHpDebt 下的 ×0.8 只扣契不回血错路径。
     */
    private static boolean executeDodgeHeal(
            Ability ability, AbilityModifierAction action, EntityAvatar avatar, String reason) {
        int eid = avatar.getId();
        long now = System.currentTimeMillis();
        synchronized (LAST_DODGE_HEAL_MS) {
            if (LAST_DODGE_HEAL_MS.containsKey(eid)
                    && now - LAST_DODGE_HEAL_MS.get(eid) < DODGE_HEAL_ICD_MS) {
                return true;
            }
            LAST_DODGE_HEAL_MS.put(eid, now);
        }

        DODGE_UNTIL_MS.put(eid, now + DODGE_WINDOW_MS);
        avatar.setConvertToHpDebt(false);
        clearForbidFoodHeal(avatar);

        float bol = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float ratio = resolveDodgeHealRatio(ability, bol, maxHp);
        float expected = Math.max(0f, bol) * ratio;

        // 配置栈常因 FullDebtHealHpRatio 未注入而算出 0/过小；贯夜以「契×比例」为准
        float healAmount = resolveHealAmount(ability, action, avatar);
        if (healAmount < expected * 0.5f) {
            healAmount = expected;
        }

        // 起手瞬间的强化档必须按 heal 前契量；清契后若立刻把 DodgeEnhanced=0，客户端会掉回弱贯夜
        float castTier = debtTier(bol, maxHp);
        syncClorindeDebtTiers(avatar, bol, maxHp, true);

        boolean mute = action != null && action.muteHealEffect;
        float realHp = healAmount > 0.5f ? avatar.heal(healAmount, mute) : 0f;
        float bolAfter = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (bolAfter > 0.5f && healAmount + 0.5f >= bol) {
            bolAfter = 0f;
        }
        pushBoL(
                avatar,
                bolAfter,
                bolAfter <= 0.5f
                        ? ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_PAY_FINISH
                        : ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_PAY,
                castTier);

        Grasscutter.getLogger()
                .info(
                        "[BoL][Clorinde] 贯夜 heal ("
                                + reason
                                + ") amount="
                                + healAmount
                                + " (bol="
                                + bol
                                + "×"
                                + ratio
                                + ") realHp="
                                + realHp
                                + " bol "
                                + bol
                                + " -> "
                                + bolAfter
                                + " keepTier="
                                + castTier);
        return true;
    }

    /** 0=无契 / 1=有契未满 / 2=契≥100%MaxHP（对应 DodgeFlag_Rank / 强化贯夜） */
    private static float debtTier(float bol, float maxHp) {
        if (bol <= 0.5f || maxHp <= 1f) {
            return 0f;
        }
        return bol + 0.5f >= maxHp ? 2f : 1f;
    }

    /**
     * 推克洛琳德强化档。force=true 时即使在贯夜窗口也按 bol 重算。
     * 贯夜还契后 bol≈0 时短暂保留起手档给动画；一旦又开始叠契，立刻按真实契量改档，
     * 避免 UI 仍显示满契强化而服务端只有 35%/70%。
     */
    private static void syncClorindeDebtTiers(
            EntityAvatar avatar, float bol, float maxHp, boolean force) {
        if (avatar == null || avatar.getGlobalAbilityValues() == null) {
            return;
        }
        float tier = debtTier(bol, maxHp);
        boolean inDodge =
                DODGE_UNTIL_MS.getOrDefault(avatar.getId(), 0L) > System.currentTimeMillis();
        if (!force && inDodge) {
            float cur = globalFlag(avatar, DODGE_ENHANCED);
            // 还契后空契：保留动画档；已重新叠契：必须跟真实 bol，禁止假满契
            if (bol <= 0.5f && tier + 0.1f < cur) {
                return;
            }
        }
        pushGlobalFloat(avatar, HP_DEBTS_ENHANCED, tier);
        pushGlobalFloat(avatar, DODGE_ENHANCED, tier);
        // 满契 High 档额外要求 HealFlag==0；粘在 1 时 High 失败且不进 Medium
        if (tier >= 1f) {
            pushGlobalFloat(avatar, DODGE_HEAL_FLAG, 0f);
        }
    }

    private static void syncClorindeDebtTiersKeep(EntityAvatar avatar, float keepTier) {
        if (avatar == null || avatar.getGlobalAbilityValues() == null) {
            return;
        }
        float tier = Math.max(0f, keepTier);
        pushGlobalFloat(avatar, HP_DEBTS_ENHANCED, tier);
        pushGlobalFloat(avatar, DODGE_ENHANCED, tier);
        // 起手后仍保持 HealFlag=0，直到客户端 High 分支自己置 1
        if (tier >= 1f) {
            pushGlobalFloat(avatar, DODGE_HEAL_FLAG, 0f);
        }
    }

    /** SGV + Ability META_GLOBAL_FLOAT，让客户端 ByTargetGlobalValue 能读到 */
    private static void pushGlobalFloat(EntityAvatar avatar, String key, float value) {
        if (avatar == null || key == null || key.isEmpty()) {
            return;
        }
        if (avatar.getGlobalAbilityValues() != null) {
            avatar.getGlobalAbilityValues().put(key, Float.valueOf(value));
        }
        try {
            avatar.onAbilityValueUpdate();
        } catch (Exception ignored) {
        }
        Player player = avatar.getPlayer();
        if (player == null) {
            return;
        }
        try {
            player.sendPacket(new PacketServerGlobalValueChangeNotify(avatar, key, value));
            if (avatar.getScene() != null) {
                avatar.getScene()
                        .broadcastPacket(new PacketServerGlobalValueChangeNotify(avatar, key, value));
            }
            AbilityScalarValueEntry entry =
                    AbilityScalarValueEntry.newBuilder()
                            .setKey(
                                    AbilityString.newBuilder()
                                            .setStr(key)
                                            .setHash(Utils.abilityHash(key))
                                            .build())
                            .setFloatValue(value)
                            .build();
            AbilityInvokeEntry invoke =
                    AbilityInvokeEntry.newBuilder()
                            .setEntityId(avatar.getId())
                            .setArgumentType(
                                    AbilityInvokeArgument
                                            .AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE)
                            .setForwardType(ForwardType.ForwardType_FORWARD_TO_ALL)
                            .setAbilityData(entry.toByteString())
                            .build();
            player.sendPacket(new PacketAbilityInvocationsNotify(invoke));
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .debug("[BoL][Clorinde] pushGlobalFloat {}={} fail: {}", key, value, e.toString());
        }
    }

    private static float resolveDodgeHealRatio(Ability ability, float bol, float maxHp) {
        float hasDebt = DODGE_HEAL_RATIO_HAS_DEBT;
        float fullDebt = DODGE_HEAL_RATIO_FULL_DEBT;
        if (ability != null && ability.getAbilitySpecials() != null) {
            float h = ability.getAbilitySpecials().getOrDefault("HasDebtHealHpRatio", 0f);
            float f = ability.getAbilitySpecials().getOrDefault("FullDebtHealHpRatio", 0f);
            if (h > 0.01f) {
                hasDebt = h;
            }
            if (f > 0.01f) {
                fullDebt = f;
            }
        }
        return bol + 0.5f >= maxHp ? fullDebt : hasDebt;
    }

    private static void clearForbidFoodHeal(EntityAvatar avatar) {
        if (avatar.getGlobalAbilityValues() == null) {
            return;
        }
        Float forbid = avatar.getGlobalAbilityValues().get(FORBID_FOOD_HEAL);
        if (forbid != null && forbid > 0f) {
            avatar.getGlobalAbilityValues().put(FORBID_FOOD_HEAL, Float.valueOf(0f));
        }
    }

    /** 受治疗者为克洛琳德时优先用 target；否则回退 caster 解析。 */
    private static EntityAvatar resolveClorindeHealTarget(Ability ability, GameEntity target) {
        if (target instanceof EntityAvatar ea && isClorinde(ea)) {
            return ea;
        }
        return resolveAvatar(ability, target);
    }

    private static float resolveHealAmount(
            Ability ability, AbilityModifierAction action, EntityAvatar avatar) {
        try {
            Object2FloatOpenHashMap<String> props = new Object2FloatOpenHashMap<>();
            for (FightProperty fp : FightProperty.values()) {
                props.put(fp.name(), avatar.getFightProperty(fp));
            }
            if (ability != null) {
                props.putAll(ability.getAbilitySpecials());
            }
            if (action != null && action.amount != null) {
                return action.amount.get(props, 0f);
            }
            if (action != null && action.ratio != null) {
                return action.ratio.get(props, 0f);
            }
        } catch (Exception ignored) {
        }
        return 0f;
    }

    private static float healToBolRatio(EntityAvatar avatar) {
        if (avatar.getAvatar() != null) {
            try {
                if (avatar.getAvatar().getPromoteLevel() >= 4) {
                    return 1.0f;
                }
            } catch (Exception ignored) {
            }
        }
        return HEAL_TO_BOL_RATIO;
    }

    public static boolean shouldSkipHealBoLPay(EntityAvatar avatar) {
        return isClorinde(avatar) && isNightVigil(avatar) && !isDodgeHeal(avatar);
    }

    public static void beforeHeal(GameEntity entity) {
        HEAL_SAVED_BOL.remove();
        if (!(entity instanceof EntityAvatar avatar) || !shouldSkipHealBoLPay(avatar)) {
            return;
        }
        float bol = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        HEAL_SAVED_BOL.set(bol);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 0f);
    }

    public static void afterHeal(GameEntity entity) {
        Float saved = HEAL_SAVED_BOL.get();
        HEAL_SAVED_BOL.remove();
        if (saved == null || !(entity instanceof EntityAvatar avatar)) {
            return;
        }
        pushBoL(
                avatar,
                saved,
                ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_ADD_ABILITY);
    }

    public static float burstBondRatio(Avatar avatar) {
        int level = 1;
        if (avatar != null && avatar.getSkillLevelMap() != null) {
            level = avatar.getSkillLevelMap().getOrDefault(SKILL_Q, 1);
        }
        if (level < 1) {
            level = 1;
        }
        if (level > 15) {
            level = 15;
        }
        return 0.60f + 0.06f * level;
    }

    public static void grant(EntityAvatar avatar, float ratioOfMaxHp, String reason) {
        if (avatar == null || ratioOfMaxHp <= 0f) {
            return;
        }
        float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float before = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        float ask = maxHp * ratioOfMaxHp;
        float next = Math.min(before + ask, maxHp * 2f);
        float gained = next - before;
        if (gained <= 0.01f) {
            return;
        }
        pushBoL(avatar, next, ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_ADD_ABILITY);
        Grasscutter.getLogger()
                .info(
                        "[BoL][Clorinde] grant "
                                + reason
                                + " ratio="
                                + ratioOfMaxHp
                                + " "
                                + before
                                + " -> "
                                + next
                                + " (+"
                                + gained
                                + ")");
    }

    /**
     * 克洛琳德契同步：只推 FIGHT_PROP_CUR_HP_DEBTS（契条）+ 她自己的强化档 GV。
     * 不走阿蕾 Cur_HPDebts/_HPDebts；也不发 AvatarFightPropNotify（会剥掉 HP_DEBTS，切人才能看见）。
     */
    public static void pushBoL(EntityAvatar avatar, float debts, ChangeHpDebtsReason reason) {
        pushBoL(avatar, debts, reason, Float.NaN);
    }

    /**
     * @param keepTier NaN=按 debts 重算档；有值=贯夜还契后保留起手档（动画/特效还在读 DodgeEnhanced）
     */
    public static void pushBoL(
            EntityAvatar avatar, float debts, ChangeHpDebtsReason reason, float keepTier) {
        if (avatar == null) {
            return;
        }
        float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float capped = Math.min(Math.max(debts, 0f), maxHp * 2f);
        float prev = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, capped);
        float change = capped - prev;

        PacketEntityFightPropUpdateNotify update =
                new PacketEntityFightPropUpdateNotify(
                        avatar, FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        PacketAvatarFightPropUpdateNotify avatarPkt =
                avatar.getAvatar() != null
                        ? new PacketAvatarFightPropUpdateNotify(
                                avatar.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP_DEBTS)
                        : null;
        Player player = avatar.getPlayer();
        if (player != null) {
            player.sendPacket(update);
            if (avatarPkt != null) {
                player.sendPacket(avatarPkt);
            }
        }
        if (avatar.getScene() != null) {
            avatar.getScene().broadcastPacket(update);
            if (Math.abs(change) >= 0.5f) {
                avatar.getScene()
                        .broadcastPacket(
                                new PacketEntityFightPropChangeReasonNotify(
                                        avatar,
                                        FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
                                        Float.valueOf(change),
                                        PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
                                        reason));
            }
        } else if (avatar.getWorld() != null) {
            avatar.getWorld().broadcastPacket(update);
        }

        if (!Float.isNaN(keepTier)) {
            syncClorindeDebtTiersKeep(avatar, keepTier);
        } else {
            syncClorindeDebtTiers(avatar, capped, maxHp, false);
        }
    }

    private static String abilityName(Ability ability) {
        if (ability == null || ability.getData() == null || ability.getData().abilityName == null) {
            return "";
        }
        return ability.getData().abilityName;
    }

    private static String resolveAbilityString(AbilityString abs) {
        if (abs == null) {
            return null;
        }
        if (abs.hasStr()) {
            return abs.getStr();
        }
        if (abs.getHash() != 0) {
            String s = GameData.getAbilityHashes().get(abs.getHash());
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    private static String resolveModifierName(String abilityName, int modifierLocalId) {
        try {
            AbilityData data = GameData.getAbilityData(abilityName);
            if (data == null || data.modifiers == null || data.modifiers.isEmpty()) {
                return null;
            }
            // modifiers 多为 LinkedHashMap：按插入序取第 N 个
            int i = 0;
            for (var e : data.modifiers.entrySet()) {
                if (i == modifierLocalId) {
                    return e.getKey();
                }
                i++;
            }
            // 部分版本 localId 与 AbilityModifier 内字段对应，尝试按值扫描
            for (var e : data.modifiers.entrySet()) {
                AbilityModifier mod = e.getValue();
                if (mod != null) {
                    try {
                        var f = mod.getClass().getField("localId");
                        Object v = f.get(mod);
                        if (v instanceof Integer id && id.intValue() == modifierLocalId) {
                            return e.getKey();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static EntityAvatar resolveAvatar(Ability ability, GameEntity target) {
        GameEntity entity = ability != null ? ability.getOwner() : null;
        if (entity instanceof EntityWeapon && ability != null && ability.getPlayerOwner() != null) {
            entity = ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
        }
        if (entity == null) {
            entity = target;
        }
        if (entity instanceof EntityWeapon && ability != null && ability.getPlayerOwner() != null) {
            entity = ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
        }
        return entity instanceof EntityAvatar ea ? ea : null;
    }

    /** Clears ICD / vigil / dodge windows that otherwise outlive a session. */
    public static void clearPlayerState(Player player) {
        if (player == null) {
            return;
        }
        clearEntityState(player.getUid());
        if (player.getTeamManager() != null) {
            for (EntityAvatar avatar : player.getTeamManager().getActiveTeam()) {
                if (avatar != null) {
                    clearEntityState(avatar.getId());
                }
            }
        }
    }

    public static void clearEntityState(int entityId) {
        LAST_SHOT_GRANT_MS.remove(entityId);
        LAST_DODGE_HEAL_MS.remove(entityId);
        VIGIL_UNTIL_MS.remove(entityId);
        DODGE_UNTIL_MS.remove(entityId);
    }
}
