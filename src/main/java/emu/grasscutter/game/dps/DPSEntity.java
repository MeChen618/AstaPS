package emu.grasscutter.game.dps;

import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * DPS 测试用的靶子怪。
 *
 * <p>伤害数值本身由客户端算好之后通过 {@code CombatInvocationsNotify} 上报，服务端只是把结果落到实体上，
 * 所以这里只需要在 {@link #damage} 上做统计，再把血条换算成一条对数曲线，保证靶子在测试期间不会被打死。
 */
public class DPSEntity extends EntityMonster {

    /** 血条从满到空覆盖的伤害量级数（log10 刻度），越大血条掉得越慢。 */
    private static final double HP_BAR_DECADES = 8d;

    private float totalDamage;
    private float maxHit;
    private int hitCount;

    private final Map<ElementType, Float> damageByElement = new EnumMap<>(ElementType.class);
    private final Map<String, Float> damageByReaction = new HashMap<>();

    public DPSEntity(Scene scene, MonsterData monsterData, Position pos, Position rot, int level) {
        super(scene, monsterData, pos, rot, level);
        // 归到伪 group 下，挑战的击杀判定与结束清场都依赖它。
        this.setGroupId(DPSChallenge.GROUP_ID);
        this.setConfigId(DPSChallenge.CONFIG_ID);
        this.setAiId(DPSChallenge.AI_CONFIG_ID);
    }

    public float getTotalDamage() {
        return this.totalDamage;
    }

    public float getMaxHit() {
        return this.maxHit;
    }

    public int getHitCount() {
        return this.hitCount;
    }

    /** 按元素分列的直接伤害（不含已识别的反应伤害），只读。 */
    public Map<ElementType, Float> getDamageByElement() {
        return Collections.unmodifiableMap(this.damageByElement);
    }

    /** 按反应分列的伤害，只读。 */
    public Map<String, Float> getDamageByReaction() {
        return Collections.unmodifiableMap(this.damageByReaction);
    }

    /**
     * 不给靶子下发武器。丘丘暴徒拿不到斧子时 AI 无法初始化，靶子会站在原地不动，方便持续输出。
     */
    @Override
    public int getMonsterWeaponId() {
        return 0;
    }

    @Override
    public void damage(float amount, int killerId, ElementType attackType) {
        if (amount <= 0f || !Float.isFinite(amount)) {
            super.damage(amount, killerId, attackType);
            return;
        }

        var element = attackType == null ? ElementType.None : attackType;
        this.totalDamage += amount;
        this.hitCount++;
        if (amount > this.maxHit) this.maxHit = amount;

        AttackResult attack = DPSAttackContext.get();
        GameEntity attacker = this.getScene() != null ? this.getScene().getEntityById(killerId) : null;
        String reaction = DPSReactionHelper.detect(attack, attacker, element);
        if (reaction != null) {
            this.damageByReaction.merge(reaction, amount, Float::sum);
        } else {
            this.damageByElement.merge(element, amount, Float::sum);
        }

        // 血条按累计伤害对数衰减:靶子越打越残但永远留 1 点血,
        // 这样 60 秒里怪不会中途死掉,同时元素微粒仍按掉血阈值正常掉落。
        float maxHp = this.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float curHp = this.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        if (!Float.isFinite(maxHp) || maxHp <= 1f || !Float.isFinite(curHp)) {
            return;
        }

        double remaining =
                Math.max(0d, 1d - Math.log10(this.totalDamage / 100d + 1d) / HP_BAR_DECADES);
        float targetHp = (float) (remaining * (maxHp - 1f)) + 1f;
        if (targetHp < curHp) {
            super.damage(curHp - targetHp, killerId, attackType);
        }
    }
}
