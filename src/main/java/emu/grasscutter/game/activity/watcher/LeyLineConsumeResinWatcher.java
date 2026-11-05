package emu.grasscutter.game.activity.watcher;

import emu.grasscutter.game.activity.ActivityWatcher;
import emu.grasscutter.game.activity.ActivityWatcherType;
import emu.grasscutter.game.props.WatcherTriggerType;

@ActivityWatcherType(WatcherTriggerType.TRIGGER_LEY_LINE_CHALLENGE_CONSUME_RESIN)
public final class LeyLineConsumeResinWatcher extends ActivityWatcher {
    @Override
    protected boolean isMeet(String... param) {
        return true;
    }

    @Override
    protected int getProgressDelta(String... param) {
        if (param == null || param.length == 0) return 1;
        try {
            return Math.max(1, Integer.parseInt(param[0]));
        } catch (NumberFormatException invalid) {
            return 1;
        }
    }
}
