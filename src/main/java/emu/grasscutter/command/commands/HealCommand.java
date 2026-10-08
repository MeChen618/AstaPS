package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.ChangeHpDebtsReason._ChangeHpDebtsReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.*;
import java.util.List;

@Command(
        label = "heal",
        aliases = {"h"},
        permission = "player.heal",
        permissionTargeted = "player.heal.others")
public final class HealCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        // "all" covers every avatar the player owns. The default only walks the active team, which
        // leaves avatars that were granted/levelled through GM commands stuck at the low HP they
        // were created with.
        if (args.size() == 1 && args.get(0).equalsIgnoreCase("all")) {
            int offTeam = targetPlayer.getTeamManager().healAllAvatars();
            CommandHandler.sendMessage(
                    sender,
                    "Healed the active team and "
                            + offTeam
                            + " avatar(s) that were not in it.");
            return;
        }
        targetPlayer
                .getTeamManager()
                .getActiveTeam()
                .forEach(
                        entity -> {
                            boolean isAlive = entity.isAlive();
                            entity.setFightProperty(
                                    FightProperty.FIGHT_PROP_CUR_HP,
                                    entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
                                   if (entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS) > 0) {
                                        entity.setFightProperty(
                                            FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
                                            0.0f

                                    );
                                    entity
                                    .getWorld()
                                    .broadcastPacket(new PacketEntityFightPropUpdateNotify(entity, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
                                    entity.getWorld().broadcastPacket(new PacketEntityFightPropChangeReasonNotify(entity, FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 0f, PropChangeReason.PropChangeReason_PROP_CHANGE_NONE,

                                    _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY_FINISH
                                   ));
                                   }

                            entity
                                    .getWorld()
                                    .broadcastPacket(
                                            new PacketAvatarFightPropUpdateNotify(
                                                    entity.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));


                            if (!isAlive) {
                                entity
                                        .getWorld()
                                        .broadcastPacket(new PacketAvatarLifeStateChangeNotify(entity.getAvatar()));
                            }
                        });
        CommandHandler.sendMessage(sender, translate(sender, "commands.heal.success"));
    }
}
