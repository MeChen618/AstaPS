package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.ArlecchinoBoLSync;
import emu.grasscutter.game.ability.ArlecchinoBurstBoL;
import emu.grasscutter.game.ability.ClorindeBoLUtil;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.ChangeHpDebtsReasonOuterClass.ChangeHpDebtsReason;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

/**
 * ys7.0 ReduceHPDebts. Arlecchino Q wipe often runs here <em>before</em> EvtDoSkillSucc — pre-arm
 * lock first.
 */
@AbilityAction(value = AbilityModifier.AbilityModifierAction.Type.ReduceHPDebts)
public final class ActionReduceHPDebts extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability,
            AbilityModifier.AbilityModifierAction action,
            ByteString abilityData,
            GameEntity target) {
        if (ClorindeBoLUtil.handleReduceHpDebts(ability, target)) {
            return true;
        }

        GameEntity debtTarget = target;
        if (action.target != null && !action.target.isEmpty()) {
            debtTarget = getTarget(ability, ability.getOwner(), action.target);
        }
        if (!(debtTarget instanceof EntityAvatar avatar)) {
            Grasscutter.getLogger().warn("[ActionReduceHPDebts] CANNOT REDUCE HP DEBT TO NON AVATAR ENTITY");
            return false;
        }

        String abilityName =
                ability != null && ability.getData() != null && ability.getData().abilityName != null
                        ? ability.getData().abilityName
                        : "";

        if (avatar.getAvatar() != null && avatar.getAvatar().getAvatarId() == 10000096) {
            // Pre-arm BEFORE EvtDoSkillSucc so client wipe packets cannot land first.
            ArlecchinoBurstBoL.tryPreArmFromAbility(ability, avatar);

            var properties = new Object2FloatOpenHashMap<String>();
            for (var property : FightProperty.values()) {
                properties.put(property.name(), debtTarget.getFightProperty(property));
            }
            properties.putAll(ability.getAbilitySpecials());
            float debtAmt = action.ratio.get(properties, 0f);
            if (debtAmt == 0f) {
                debtAmt = action.ratio.get(ability);
            }
            float curDebt = debtTarget.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
            float maxHp = debtTarget.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            float newDebt = Math.min(Math.max(curDebt - debtAmt, 0f), 2f * maxHp);

            if (ArlecchinoBurstBoL.looksLikeBurstWipe(ability, curDebt, newDebt)
                    && !ArlecchinoBurstBoL.isConsumeBlocked(avatar)) {
                ArlecchinoBurstBoL.onBurstCast(avatar);
            }

            if (ArlecchinoBurstBoL.isConsumeBlocked(avatar)) {
                ArlecchinoBurstBoL.repinClientBoL(avatar);
                Grasscutter.getLogger()
                        .info(
                                "[BoL] skip ReduceHPDebts ability={} cur={}→would {} (consume-lock)",
                                abilityName,
                                curDebt,
                                newDebt);
                return true;
            }
        }

        var properties = new Object2FloatOpenHashMap<String>();
        for (var property : FightProperty.values()) {
            properties.put(property.name(), debtTarget.getFightProperty(property));
        }
        properties.putAll(ability.getAbilitySpecials());

        float debt = action.ratio.get(properties, 0f);
        if (debt == 0f) {
            debt = action.ratio.get(ability);
        }

        float curDebt = debtTarget.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        float maxHp = debtTarget.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float newDebt = Math.min(Math.max(curDebt - debt, 0f), 2f * maxHp);
        float changeDebt = newDebt - curDebt;
        if (changeDebt == 0f) {
            return true;
        }

        ArlecchinoBoLSync.pushBoL(
                avatar,
                newDebt,
                changeDebt,
                newDebt <= 0f
                        ? ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_PAY_FINISH
                        : ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_PAY);
        return true;
    }
}
