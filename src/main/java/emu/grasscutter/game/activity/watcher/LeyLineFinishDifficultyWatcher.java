package emu.grasscutter.game.activity.watcher;

import emu.grasscutter.game.activity.ActivityWatcher;
import emu.grasscutter.game.activity.ActivityWatcherType;
import emu.grasscutter.game.props.WatcherTriggerType;

@ActivityWatcherType(WatcherTriggerType.TRIGGER_LEY_LINE_CHALLENGE_FINISH_DIFFICULTY)
public final class LeyLineFinishDifficultyWatcher extends ActivityWatcher {
    @Override
    protected boolean isMeet(String... param) {
        var wanted = getActivityWatcherData().getTriggerConfig().getParamList();
        if (param == null || param.length == 0 || wanted == null || wanted.isEmpty()) return false;
        if (!param[0].equals(wanted.get(0))) return false;
        if (wanted.size() > 1 && !wanted.get(1).isBlank() && !"0".equals(wanted.get(1))) {
            try {
                int required = Integer.parseInt(wanted.get(1));
                int score = param.length > 1 ? Integer.parseInt(param[1]) : 0;
                return score >= required;
            } catch (NumberFormatException invalid) {
                return false;
            }
        }
        return true;
    }
}
