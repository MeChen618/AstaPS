package emu.grasscutter.game.activity.leylinechallenge;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;

/** The actual single-player button sends difficulty (field 5), not an activity level ID. */
public final class StygianNativeEntry {
    private StygianNativeEntry() {}
    public static int enter(Player p, int difficulty) {
        var schedule = LeyLineChallengeData.currentSchedule();
        int level = LeyLineChallengeData.levelIdForDifficulty(schedule, difficulty);
        int result;
        if (difficulty < 1 || difficulty > 6 || level == 0
                || !LeyLineChallengeData.isScheduleActive(schedule)) result = 1101;
        else if (p.getWorld() == null || p.getScene() == null
                || p.getWorld().isMultiplayer() || p.getWorld().getPlayerCount() != 1
                || p.getScene().getId() != 3 || p.getSceneLoadState() != SceneLoadState.LOADED
                || StygianMatchmaking.of(p.getServer()).reserved(p.getUid())) result = 1204;
        else result = LeyLineChallengeEntry.enter(p, level, 0);
        // Successful entry is driven by the existing native PlayerEnterSceneNotify flow.
        // The dedicated obfuscated 7.1 response ID is unverified; never send guessed response IDs.
        Grasscutter.getLogger().info("Stygian native entry uid={} difficulty={} level={} result={}",
                p.getUid(), difficulty, level, result);
        return result;
    }
}
