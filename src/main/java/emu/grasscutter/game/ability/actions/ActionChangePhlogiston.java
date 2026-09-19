package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Phlogiston;

/**
 * Both directions of a phlogiston change end the same way: fuel is unlimited, so the tank is full
 * whether the ability meant to spend it or hand some back. The value still goes out on the wire,
 * because the client drains its own gauge and only corrects it when told.
 */
@AbilityAction(value = AbilityModifier.AbilityModifierAction.Type.ChangePhlogiston)
public final class ActionChangePhlogiston extends AbilityActionHandler {

    @Override
    public boolean execute(
            Ability ability,
            AbilityModifier.AbilityModifierAction action,
            ByteString abilityData,
            GameEntity target) {
        Phlogiston.refill(ability.getPlayerOwner(), ability.getOwner());
        return true;
    }
}
