package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Phlogiston;

/**
 * The client asks to spend phlogiston here. Fuel is unlimited, so the answer is always a full tank
 * sent straight back - the client drains its own copy of the gauge locally, and this is what puts
 * the needle back.
 */
@AbilityMixin(value = AbilityMixinData.Type.PhlogistonCostMixin)
public class PhlogistonCostMixin extends AbilityMixinHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityMixinData mixinData, ByteString abilityData, GameEntity target) {
        Phlogiston.refill(ability.getPlayerOwner(), ability.getOwner());
        return true;
    }
}
