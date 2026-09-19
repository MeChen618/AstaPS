package emu.grasscutter.command.commands;

import static emu.grasscutter.command.CommandHelpers.matchIntOrNeg;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.game.dps.DPSMeter;
import emu.grasscutter.game.player.Player;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code /dps} - the DPS test.
 *
 * <p>In game the prefix-free form is more common: send "dps30" in chat to start and "dpsstop" to end,
 * which {@link emu.grasscutter.game.chat.ChatSystem} forwards to {@link DPSMeter}.
 */
@Command(
        label = "dps",
        usage = {"[<seconds>] [x<targetCount>]", "stop"},
        targetRequirement = Command.TargetRequirement.ONLINE)
public final class DPSCommand implements CommandHandler {

    private static final Pattern COUNT_REGEX = Pattern.compile("^x(\\d+)$");
    private static final Pattern TIME_REGEX = Pattern.compile("^s?(\\d+)$");

    private static final Set<String> STOP_WORDS = Set.of("stop");
    private static final Set<String> START_WORDS = Set.of("start");

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        var seconds = DPSMeter.DEFAULT_SECONDS;
        var count = 1;

        for (var arg : args) {
            var token = arg.toLowerCase();

            if (STOP_WORDS.contains(token)) {
                DPSMeter.stop(targetPlayer);
                return;
            }
            if (START_WORDS.contains(token)) continue;

            var parsedCount = matchIntOrNeg(COUNT_REGEX, token);
            if (parsedCount != -1) {
                count = parsedCount;
                continue;
            }

            var parsedTime = matchIntOrNeg(TIME_REGEX, token);
            if (parsedTime != -1) {
                seconds = parsedTime;
            }
        }

        DPSMeter.start(targetPlayer, seconds, count);
    }
}
