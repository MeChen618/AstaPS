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
 * {@code /dps} —— DPS 测试。
 *
 * <p>游戏里更常用的是免前缀写法：聊天框直接发「dps30秒」开测、「dps停止」结束，
 * 由 {@link emu.grasscutter.game.chat.ChatSystem} 转交给 {@link DPSMeter}。
 */
@Command(
        label = "dps",
        usage = {"[<秒数>] [x<靶子数量>]", "stop"},
        targetRequirement = Command.TargetRequirement.ONLINE)
public final class DPSCommand implements CommandHandler {

    private static final Pattern COUNT_REGEX = Pattern.compile("^x(\\d+)$");
    private static final Pattern TIME_REGEX = Pattern.compile("^s?(\\d+)秒?$");

    private static final Set<String> STOP_WORDS = Set.of("stop", "停", "停止", "结束");
    private static final Set<String> START_WORDS = Set.of("start", "开始");

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
