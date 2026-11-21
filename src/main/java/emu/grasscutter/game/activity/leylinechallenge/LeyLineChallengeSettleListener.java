package emu.grasscutter.game.activity.leylinechallenge;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.dungeons.DungeonSettleListener;
import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult.DungeonEndReason;
import emu.grasscutter.game.props.WatcherTriggerType;

/** Credits the selected difficulty after a successful Stygian Onslaught clear. */
public final class LeyLineChallengeSettleListener implements DungeonSettleListener {

    private final int difficulty;

    public LeyLineChallengeSettleListener(int difficulty) {
        this.difficulty = difficulty;
    }

    @Override
    public void onDungeonSettle(DungeonManager dungeonManager, DungeonEndReason endReason) {
        if (endReason != DungeonEndReason.COMPLETED || difficulty <= 0) return;
        int seconds =
                dungeonManager.getScene().getSceneTimeSeconds() - dungeonManager.getStartSceneTime();
        int score = seconds <= 180 ? 180 : 0;
        for (var player : dungeonManager.getScene().getPlayers()) {
            player.getActivityManager()
                    .triggerWatcher(
                            WatcherTriggerType.TRIGGER_LEY_LINE_CHALLENGE_FINISH_DIFFICULTY,
                            String.valueOf(difficulty),
                            String.valueOf(score),
                            "1");

            Grasscutter.getLogger()
                    .info(
                            "Stygian clear uid={} difficulty={} seconds={} score={}",
                            player.getUid(),
                            difficulty,
                            seconds,
                            score);
        }
    }
}
