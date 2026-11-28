package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.SnezhnayaFrigid;

import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "kresnik",
        aliases = {"kshnik"},
        permission = "player.setprop",
        permissionTargeted = "player.setprop.others")
public final class KresnikCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var command = new CommandLine(new Args(sender, targetPlayer));
        command.registerConverter(
                Float.class,
                value -> {
                    float energy;
                    try {
                        energy = Float.parseFloat(value);
                    } catch (NumberFormatException invalid) {
                        throw new CommandLine.TypeConversionException(
                                "Kresnik energy must be a number from 0 to 500.");
                    }
                    if (!Float.isFinite(energy)
                            || energy < 0f
                            || energy > SnezhnayaFrigid.MAX_ENERGY) {
                        throw new CommandLine.TypeConversionException(
                                "Kresnik energy must be from 0 to 500.");
                    }
                    return energy;
                });
        return command;
    }

    @CommandLine.Command(
            name = "kresnik",
            description = "Query Kresnik energy or set its current value (0-500).")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[energy]")
        private Float energy;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (energy != null) SnezhnayaFrigid.setEnergy(targetPlayer, energy);
            CommandOutput.sendMessage(
                    sender,
                    "Kresnik energy = "
                            + SnezhnayaFrigid.stateOf(targetPlayer).getEnergy()
                            + " / 500");
        }
    }
}
