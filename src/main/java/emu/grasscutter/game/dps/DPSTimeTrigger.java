package emu.grasscutter.game.dps;

import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.challenge.trigger.ChallengeTrigger;

/**
 * DPS 计时器。超时走 {@link WorldChallenge#done()}。
 *
 * <p>刻意不发 {@code ChallengeDataNotify}：用场景绝对截止时刻每秒刷新时，客户端会闪「-1s」
 * 并在锁时/对时后跳秒。开测时长已在 {@code DungeonChallengeBeginNotify.paramList} 里，
 * 客户端本地倒数即可；是否到点由 {@link DPSChallenge} 挂钟判定。
 */
public class DPSTimeTrigger extends ChallengeTrigger {

    @Override
    public void onBegin(WorldChallenge challenge) {
        // no-op: do not touch ChallengeDataNotify
    }

    @Override
    public void onCheckTimeout(WorldChallenge challenge) {
        if (challenge instanceof DPSChallenge dps) {
            if (dps.elapsedSeconds() >= dps.getTimeLimit()) {
                challenge.done();
            }
            return;
        }
        var elapsed = challenge.getScene().getSceneTimeSeconds() - challenge.getStartedAt();
        if (elapsed >= challenge.getTimeLimit()) {
            challenge.done();
        }
    }
}
