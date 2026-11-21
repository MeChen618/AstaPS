package emu.grasscutter.game.activity.leylinechallenge;

import emu.grasscutter.game.activity.ActivityHandler;
import emu.grasscutter.game.activity.GameActivity;
import emu.grasscutter.game.activity.PlayerActivityData;
import emu.grasscutter.game.props.ActivityType;
import emu.grasscutter.net.proto.ActivityInfoOuterClass.ActivityInfo;
import emu.grasscutter.net.proto.HDHLLCCCLJMOuterClass.HDHLLCCCLJM;
import emu.grasscutter.net.proto.LeyLineChallengeDetailInfo._LeyLineChallengeDetailInfo;
import emu.grasscutter.net.proto.MKADIBABGKHOuterClass.MKADIBABGKH;
import emu.grasscutter.net.proto.JJKBEPPIFIEOuterClass.JJKBEPPIFIE;
import emu.grasscutter.net.proto.PPOBOBDKPHIOuterClass.PPOBOBDKPHI;

/** Activity 5269: Stygian Onslaught. */
@GameActivity(ActivityType.NEW_ACTIVITY_LEY_LINE_CHALLENGE)
public class LeyLineChallengeActivityHandler extends ActivityHandler {
    private static final int FIRST_WATCHER = 1526901;
    private static final int LAST_WATCHER = 1526906;

    @Override
    public void onInitPlayerActivityData(PlayerActivityData playerActivityData) {
        // Watcher progress is the activity's persistent state.
    }

    @Override
    public void onProtoBuild(
            PlayerActivityData playerActivityData, ActivityInfo.Builder activityInfo) {
        var schedule = LeyLineChallengeData.currentSchedule();
        int scheduleId = schedule == null ? 0 : schedule.id();
        long now = System.currentTimeMillis() / 1000;
        boolean active =
                schedule != null && now >= schedule.begin() && now <= schedule.end();

        // Activity id (5269) and the native activity type (5701) are different identifiers.
        activityInfo.setActivityType(ActivityType.NEW_ACTIVITY_LEY_LINE_CHALLENGE.getValue());
        activityInfo.setIsStarting(active);
        activityInfo.setIsFinished(false);

        var detail = _LeyLineChallengeDetailInfo.newBuilder();

        // The challenge UI reads its open state from this summary, not a best-run record.
        // These names are retained from 7.0; the generated 7.1 descriptors supply the wire tags.
        var state = JJKBEPPIFIE.newBuilder()
                .setEBMOODIKLDM(3)
                .setKOMPLLJLEOL(active)
                .setFIDIFABIGBH(scheduleId);
        if (schedule != null) {
            state.setOFCFHPFDGLD(PPOBOBDKPHI.newBuilder().setScheduleId(scheduleId));
        }
        detail.setALJFGLAPKKM(state);
        detail.setLBCGNOOJDIC(active);

        // Empty progress lists describe uncompleted difficulties. Do not put level config ids
        // or watcher ids into these lists: the client interprets them as per-round progress.
        if (active) {
            for (int difficulty = 1; difficulty <= schedule.levelIds().size(); difficulty++) {
                detail.addDifficultyInfoList(MKADIBABGKH.newBuilder().setDifficulty(difficulty));
            }
        }

        if (active) {
            // Retain the existing record while fixing the independently missing open-state block.
            detail.setIDIHOMKNMHE(
                    HDHLLCCCLJM.newBuilder()
                            .setScheduleId(scheduleId)
                            .setTimestamp(now)
                            .build());
            // Countdown target for the "unlocks again" line: this schedule's end.
            detail.setLEEHFFLICIK(schedule.end());
        }

        if (playerActivityData != null) {
            for (var watcher : playerActivityData.getAllWatcherInfoList()) {
                int difficulty = watcher.getWatcherId() - FIRST_WATCHER + 1;
                if (watcher.getWatcherId() < FIRST_WATCHER || watcher.getWatcherId() > LAST_WATCHER
                        || watcher.getCurProgress() <= 0) continue;
                detail.addHDAKHICDPPN(
                        HDHLLCCCLJM.newBuilder()
                                .setDifficulty(difficulty)
                                .setScheduleId(scheduleId)
                                .setTimestamp(now));
            }
        }

        activityInfo.setLeyLineChallengeDetailInfo(detail);
    }
}
