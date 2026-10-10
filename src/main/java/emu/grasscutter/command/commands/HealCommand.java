package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

/** Legacy heal and heal all commands alongside restore hp. */
@Command(
        label = "heal",
        aliases = {"h"},
        permission = "player.heal",
        permissionTargeted = "player.heal.others")
public final class HealCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "heal")
    private final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[all]")
        private String scope;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (scope == null) {
                RestoreCommand.restoreHp(targetPlayer);
                CommandOutput.sendMessage(sender, translate(sender, "commands.heal.success"));
            } else if (scope.equalsIgnoreCase("all")) {
                int offTeam = targetPlayer.getTeamManager().healAllAvatars();
                CommandOutput.sendMessage(
                        sender,
                        "Healed the active team and "
                                + offTeam
                                + " avatar(s) that were not in it.");
            } else {
                HealCommand.this.sendUsageMessage(sender);
            }
        }
    }
}
