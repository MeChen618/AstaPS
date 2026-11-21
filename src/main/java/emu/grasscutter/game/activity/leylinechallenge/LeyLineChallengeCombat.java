package emu.grasscutter.game.activity.leylinechallenge;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.GameConstants;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.dungeons.challenge.DungeonChallenge;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.challenge.trigger.ChallengeTrigger;
import emu.grasscutter.game.dungeons.challenge.trigger.InTimeTrigger;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.server.packet.send.PacketChallengeDataNotify;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit preparation followed by three separate encounters. */
public final class LeyLineChallengeCombat {
    private static final int GROUP = 5269001;
    private static final ConcurrentHashMap<Scene, Run> RUNS = new ConcurrentHashMap<>();
    private LeyLineChallengeCombat() {}
    private static final class Run {
        final Scene scene;
        final Player owner;
        final int difficulty, level;
        final Position origin;
        final StygianRounds rounds = new StygianRounds();
        EntityGadget blossom;
        DungeonChallenge challenge;
        boolean prepared;
        boolean pendingStart;
        long stageBeginMillis = System.currentTimeMillis();
        int activeGallery;
        int wait = 90;
        Run(Player p, int difficulty, int level) {
            this.owner = p; this.scene = p.getScene(); this.difficulty = difficulty;
            this.level = level; this.origin = new Position(p.getPosition());
            this.activeGallery = StygianGalleryPackets.gallery(scene.getId(), false);
        }
        int gallery() {
            return activeGallery;
        }
        void stopGallery(int reason) { scene.broadcastPacket(StygianProtocol.packet(480, 8, gallery(), 13, reason)); }
        void sync() {
            int id = StygianGalleryPackets.gallery(scene.getId(), rounds.phase() == StygianRounds.Phase.BATTLE);
            activeGallery = id;
            // Initial preparation only reports gallery info; it is not a timed battle start.
            if (rounds.phase() == StygianRounds.Phase.BATTLE) {
                int now = (int)(System.currentTimeMillis() / 1000);
                int limit = difficulty >= 6 ? 180 : 600;
                scene.broadcastPacket(StygianProtocol.packet(25398, 2, id));
                scene.broadcastPacket(StygianProtocol.packet(20814, 1, id, 3, owner.getUid(),
                        10, scene.getPlayers().size(), 11, now, 9, now + limit));
            }
            scene.broadcastPacket(StygianGalleryPackets.notify(id, owner.getUid(), scene.getPlayers().size()));
            // OONOGOEHGIB round records: the client's round page HUD renders the three-round
            // progression and the current battle from these; the minimal 1439 omitted them.
            var avatarIds = owner.getTeamManager().getCurrentTeamInfo().getAvatars();
            var records = new java.util.ArrayList<StygianNativeState.RoundRecord>();
            for (int i = 0; i < rounds.cleared(); i++)
                records.add(new StygianNativeState.RoundRecord(0, true, i + 1, owner.getUid(), avatarIds));
            if (rounds.phase() == StygianRounds.Phase.BATTLE)
                records.add(new StygianNativeState.RoundRecord(0, false, rounds.round(),
                        owner.getUid(), avatarIds));
            scene.broadcastPacket(StygianNativeState.packet(owner.getUid(), difficulty, rounds.round(),
                    rounds.phase() == StygianRounds.Phase.BATTLE, stageBeginMillis, id,
                    scene.getPlayers(), records));
        }
        synchronized void prepare() {
            if (rounds.phase() != StygianRounds.Phase.PREPARE || blossom != null) return;
            stageBeginMillis = System.currentTimeMillis();
            sync();
            var pos = new Position(origin.getX(), origin.getY(), origin.getZ() + 3);
            blossom = new EntityGadget(scene, 73051002, pos, new Position(0, 0, 0));
            blossom.setGroupId(GROUP); blossom.setConfigId(1000 + rounds.round());
            var worktop = new GadgetWorktop(blossom);
            worktop.addWorktopOptions(new int[]{7});
            worktop.setOnSelectWorktopOptionEvent((g, option) -> { if (option == 7) begin(); return false; });
            blossom.replaceContent(worktop);
            scene.addEntity(blossom);
            Grasscutter.getLogger().info("Stygian prepare uid={} round={} scene={} (no monsters)",
                    owner.getUid(), rounds.round(), scene.getId());
        }
        synchronized void begin() {
            if (RUNS.get(scene) != this || rounds.phase() != StygianRounds.Phase.PREPARE
                    || scene.getPlayers().isEmpty() || !loaded()) return;
            var levelData = LeyLineChallengeData.level(level);
            int index = rounds.cleared();
            if (levelData == null || levelData.levelConfigIds().size() != 3) return;
            int monsterId = StygianBossRoster.monster(difficulty,
                    LeyLineChallengeData.levelConfigIcon(levelData.levelConfigIds().get(index)));
            var data = GameData.getMonsterDataMap().get(monsterId);
            if (data == null) return;
            var pos = new Position(origin.getX(), origin.getY(), origin.getZ() + 10);
            var monster = new StygianBossMonster(scene, data, pos, new Position(0, 180, 0), StygianBossRoster.level(difficulty));
            monster.setGroupId(GROUP); monster.setConfigId(index + 1);
            if (!rounds.start(monster.getId())) return;
            pendingStart = false;
            stageBeginMillis = System.currentTimeMillis();
            if (blossom != null) { scene.removeEntity(blossom); blossom = null; }
            scene.broadcastPacket(StygianProtocol.packet(480, 8, StygianGalleryPackets.gallery(scene.getId(), false), 13, 9));
            sync();
            var group = new SceneGroup(); group.id = GROUP;
            int limit = difficulty >= 6 ? 180 : 600;
            challenge = new DungeonChallenge(scene, group, 1, 1, List.of(3, limit), limit, 3,
                    List.of(new BossTrigger(this), new InTimeTrigger()));
            scene.setChallenge(challenge);
            challenge.start();
            scene.addEntity(monster);
            Grasscutter.getLogger().info("Stygian round started uid={} round={} monster={} entity={}",
                    owner.getUid(), index + 1, monsterId, monster.getId());
        }
        synchronized int confirm(Player p, StygianRoundRequest request) {
            if (p != owner) return 14279;
            if (pendingStart || rounds.phase() != StygianRounds.Phase.PREPARE || !loaded()) return 14277;
            if (request.round() != rounds.round()) return 14278;
            var guids = new java.util.ArrayList<Long>();
            for (int id : request.avatars()) {
                var avatar = p.getAvatars().getAvatarById(id);
                if (avatar == null) return 14276;
                guids.add(avatar.getGuid());
            }
            // Only the temporary party changes; the user's saved world parties remain intact.
            p.getTeamManager().setupTemporaryTeam(List.of(guids));
            p.getTeamManager().useTemporaryTeam(0);
            pendingStart = true; wait = 90;
            // Reload the existing arena to close the native selection page and rebuild the selected party.
            // INTERNAL preserves this Run/scene rather than allocating a new dungeon with no Run.
            int dungeon = scene.getDungeonManager().getDungeonData().getId();
            boolean moved = p.getWorld().transferPlayerToScene(p, TeleportProperties.builder()
                    .sceneId(scene.getId()).dungeonId(dungeon).teleportType(TeleportType.INTERNAL)
                    .enterType(EnterType.EnterType_ENTER_DUNGEON).enterReason(EnterReason.DungeonEnter)
                    .teleportTo(new Position(origin)).build());
            if (!moved) { pendingStart = false; return 14289; }
            p.getWorld().setPaused(false);
            return 0;
        }
        boolean loaded() {
            var members = prepared ? scene.getPlayers() : scene.getWorld().getPlayers();
            return members.stream().allMatch(p -> p.isOnline() && p.getScene() == scene
                    && p.getSceneLoadState() == Player.SceneLoadState.LOADED);
        }
        synchronized void defeated(WorldChallenge c, EntityMonster monster) {
            if (!rounds.defeated(monster.getId())) return;
            stopGallery(9);
            scene.broadcastPacket(new PacketChallengeDataNotify(c, 1, rounds.cleared()));
            if (rounds.phase() == StygianRounds.Phase.COMPLETE) {
                c.done();
                if (scene.getDungeonManager() != null && !scene.getDungeonManager().isFinishedSuccessfully())
                    scene.getDungeonManager().finishDungeon();
                Grasscutter.getLogger().info("Stygian three rounds complete uid={}", owner.getUid());
            } else {
                // Finish this round's HUD without settling the whole dungeon or granting completion.
                c.finishSuccessQuiet(); scene.setChallenge(null); prepare();
            }
        }
        synchronized void cancel() {
            if (rounds.phase() == StygianRounds.Phase.EXITED) return;
            stopGallery(2); rounds.exit();
            if (scene.getChallenge() != null) scene.getChallenge().abortQuiet();
            scene.setChallenge(null);
            if (blossom != null) { scene.removeEntity(blossom); blossom = null; }
            for (var entity : List.copyOf(scene.getEntities().values()))
                if (entity.getGroupId() == GROUP) scene.removeEntity(entity);
            RUNS.remove(scene, this);
        }
        void tick() {
            synchronized (this) {
                if (RUNS.get(scene) != this) return;
                if (scene.getPlayers().isEmpty() || rounds.phase() == StygianRounds.Phase.EXITED) { cancel(); return; }
                if (!prepared) {
                    if (loaded()) { prepared = true; prepare(); }
                    else if (--wait == 0) { cancel(); return; }
                } else if (pendingStart) {
                    if (loaded()) begin();
                    else if (--wait == 0) { pendingStart = false; exit(owner); return; }
                }
            }
            owner.getServer().getScheduler().scheduleDelayedTask(this::tick, 1);
        }
    }
    private static final class BossTrigger extends ChallengeTrigger {
        final Run run;
        BossTrigger(Run run) { this.run = run; }
        @Override public void onBegin(WorldChallenge c) {
            c.getScene().broadcastPacket(new PacketChallengeDataNotify(c, 1, run.rounds.cleared()));
        }
        @Override public void onMonsterDeath(WorldChallenge c, EntityMonster monster) { run.defeated(c, monster); }
        @Override public void onFinish(WorldChallenge c) {
            if (!c.isSuccess() && run.rounds.phase() == StygianRounds.Phase.BATTLE) run.cancel();
        }
    }
    public static void scheduleStart(Player player, int difficulty, int levelId) {
        if (player.getScene() == null) return;
        Run run = new Run(player, difficulty, levelId);
        Run old = RUNS.put(player.getScene(), run);
        if (old != null) old.cancel();
        player.getServer().getScheduler().scheduleDelayedTask(run::tick, 1);
    }
    public static boolean active(Player p) { return p.getScene() != null && RUNS.containsKey(p.getScene()); }
    public static int confirmRound(Player p, StygianRoundRequest request) {
        Run run = p.getScene() == null ? null : RUNS.get(p.getScene());
        return run == null ? 14272 : run.confirm(p, request);
    }
    public static BasePacket galleries(Player p) {
        Run run = p.getScene() == null ? null : RUNS.get(p.getScene());
        return run == null || run.rounds.phase() == StygianRounds.Phase.COMPLETE ? new BasePacket(9640)
                : StygianGalleryPackets.list(run.gallery(), run.owner.getUid(), run.scene.getPlayers().size());
    }
    public static boolean exit(Player p) {
        Run run = p.getScene() == null ? null : RUNS.get(p.getScene());
        if (run == null) return false;
        // A guest may leave without cancelling the host's ongoing encounter.
        boolean closeRoom = p == run.scene.getWorld().getHost();
        if (closeRoom) run.cancel();
        var participants = closeRoom ? List.copyOf(run.scene.getPlayers()) : List.of(p);
        for (var member : participants) {
            if (run.scene.getDungeonManager() != null) run.scene.getDungeonManager().unsetTrialTeam(member);
            member.getTeamManager().cleanTemporaryTeam();
            int previous = member.getPrevSceneForDungeon() > 0 ? member.getPrevSceneForDungeon() : 3;
            Position position = member.getPrevPosForDungeon();
            if (position == null) position = new Position(GameConstants.START_POSITION);
            boolean moved = member.getWorld().transferPlayerToScene(member, previous, new Position(position));
            Grasscutter.getLogger().info("Stygian exit uid={} scene={} restoredScene={} result={}",
                    member.getUid(), run.scene.getId(), previous, moved);
        }
        return true;
    }
    public static boolean interrupt(Player p, int gallery) {
        Run run = p.getScene() == null ? null : RUNS.get(p.getScene());
        if (run == null || gallery != run.gallery()) return false;
        return exit(p);
    }
}
