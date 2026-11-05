package emu.grasscutter.game.activity.leylinechallenge;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

/** Entry flow for activity 5269. */
public final class LeyLineChallengeEntry {
    private LeyLineChallengeEntry() {}

    public static int enter(Player player, int levelId, int playType) {
        int difficulty = LeyLineChallengeData.difficultyForLevel(levelId);
        if (difficulty <= 0) {
            Grasscutter.getLogger()
                    .warn(
                            "Stygian enter rejected uid={} levelId={} playType={}",
                            player.getUid(),
                            levelId,
                            playType);
            return Retcode.RET_SVR_ERROR_VALUE;
        }

        int dungeonId = LeyLineChallengeData.dungeonForDifficulty(difficulty);
        if (!player.getServer().getDungeonSystem().enterDungeon(player, 0, dungeonId, true)) {
            Grasscutter.getLogger()
                    .warn(
                            "Stygian dungeon enter failed uid={} difficulty={} dungeon={}",
                            player.getUid(),
                            difficulty,
                            dungeonId);
            return Retcode.RET_DUNGEON_ENTER_FAIL_VALUE;
        }

        var scene = player.getScene();
        if (scene != null) {
            scene.addDungeonSettleObserver(new LeyLineChallengeSettleListener(difficulty));
            LeyLineChallengeCombat.scheduleStart(player, difficulty, levelId);
        }

        Grasscutter.getLogger()
                .info(
                        "Stygian enter uid={} difficulty={} levelId={} dungeon={} playType={}",
                        player.getUid(),
                        difficulty,
                        levelId,
                        dungeonId,
                        playType);
        return 0;
    }
}
