package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityModifierController;
import emu.grasscutter.game.ability.ColumbinaMountainDew;
import emu.grasscutter.game.ability.EscoffierHealUtil;
import emu.grasscutter.game.ability.LaumaC1HealHelper;
import emu.grasscutter.game.ability.LohenExtraArtSkillLevelHelper;
import emu.grasscutter.game.ability.PredicateEvaluator;
import emu.grasscutter.game.entity.GameEntity;
import java.util.List;
import java.util.Map;

@AbilityAction(AbilityModifierAction.Type.ApplyModifier)
public final class ActionApplyModifier extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (action.predicates != null && !action.predicates.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> preds = (List<Map<String, Object>>) (List<?>) action.predicates;
            if (!PredicateEvaluator.all(preds, ability, ability.getOwner(), target, action)) return true;
        }
        var modifierData = ability.getData().modifiers.get(action.modifierName);
        if (modifierData == null) return false;

        if ("Unique".equals(modifierData.stacking)) {
            ability.getModifiers().remove(action.modifierName);
        }

        AbilityModifierController modifier = new AbilityModifierController(ability, ability.getData(), modifierData);
        ability.getModifiers().put(action.modifierName, modifier);
        var manager = ability.getManager();

        // 菈乌玛一命：在跑 onAdded 前先按精通结算回血，避免 GetFightProperty/HealHP 竞态与目标解析问题。
        boolean laumaC1Healed = LaumaC1HealHelper.isHealModifier(action.modifierName)
                && LaumaC1HealHelper.tryHeal(ability);

        if (modifierData.onAdded != null) {
            for (var a : modifierData.onAdded) {
                if (a == null) continue;
                // 已由 helper 结算过则跳过 HealHP，防止双倍治疗；仍跑 GetFightProperty/特效等。
                if (laumaC1Healed && a.type == AbilityModifierAction.Type.HealHP) {
                    continue;
                }
                // onAdded 是序列：前一步写的全局值后一步要立刻读到。
                manager.executeActionNow(ability, a, abilityData, target);
            }
        }
        if (modifierData.onAttackLanded != null) {
            for (var b : modifierData.onAttackLanded) {
                if (b == null) continue;
                manager.executeActionNow(ability, b, abilityData, target);
            }
        }

        if (EscoffierHealUtil.isStrikeModifier(action.modifierName)) {
            EscoffierHealUtil.onStrikeModifier(ability);
        }

        // 山月草露 UI is driven by RGV_TempMoonOvergrowPoint — grant when the talent's
        // AddMoonOverGrowCount modifier lands (Moon Bloom in 月之领域).
        if (ColumbinaMountainDew.isAddMoonOverGrowModifier(action.modifierName)) {
            ColumbinaMountainDew.grantOne(ability, target);
        }

        if (LohenExtraArtSkillLevelHelper.isExtraArtModifier(action.modifierName)) {
            LohenExtraArtSkillLevelHelper.onModifierApplied(ability);
        }

        return true;
    }
}
