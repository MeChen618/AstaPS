package emu.grasscutter.game.activity.leylinechallenge;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.CoopRequest;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.server.game.GameServer;
import java.util.*;

/** Restricted to the three resource-backed Stygian dungeon queues. */
public final class StygianMatchmaking {
    private static final Map<GameServer, StygianMatchmaking> INSTANCES = new HashMap<>();
    public static synchronized StygianMatchmaking of(GameServer server) {
        return INSTANCES.computeIfAbsent(server, StygianMatchmaking::new);
    }
    private record Source(Player player, Scene scene, int worldLevel) {}
    private final GameServer server;
    private final StygianMatchQueue queue = new StygianMatchQueue();
    private final Map<Integer, Source> sources = new HashMap<>();
    private boolean scheduled;
    private StygianMatchmaking(GameServer server) { this.server = server; }
    private static long now() { return System.currentTimeMillis() / 1000; }

    private static boolean eligible(Player p) {
        return p != null && p.isOnline() && p.getWorld() != null && p.getScene() != null
                && p.getWorld().getHost() == p && p.getWorld().getPlayerCount() == 1
                && !p.getWorld().isMultiplayer() && p.getScene().getId() == 3
                && p.getSceneLoadState() == SceneLoadState.LOADED;
    }
    private boolean current(StygianMatchQueue.Run r) {
        var schedule = LeyLineChallengeData.currentSchedule();
        if (!LeyLineChallengeData.isScheduleActive(schedule) || schedule.id() != r.schedule) return false;
        for (int uid : r.members) {
            Source s = sources.get(uid);
            Player p = server.getPlayerByUid(uid);
            if (s == null || p != s.player() || !p.isOnline()
                    || p.getWorldLevel() != s.worldLevel()) return false;
            if (r.stage != StygianMatchQueue.Stage.LOADING
                    && (!eligible(p) || p.getScene() != s.scene())) return false;
            if (r.stage == StygianMatchQueue.Stage.LOADING) {
                Player host = server.getPlayerByUid(r.host);
                if (host == null || p.getWorld() != host.getWorld()
                        || p.getWorld().getHost() != host || p.getScene() != host.getScene()
                        || p.getScene().getId() != 3
                        || p.getWorld().getPlayerCount() != r.members.size()) return false;
            }
        }
        return true;
    }
    public synchronized void start(Player p, int type, int dungeon, int playId, int matchId) {
        var schedule = LeyLineChallengeData.currentSchedule();
        int code = 0;
        if (type != 1 || dungeon < 20003 || dungeon > 20005 || playId != 0 || matchId != 0
                || !GameData.getDungeonDataMap().containsKey(dungeon)
                || !LeyLineChallengeData.isScheduleActive(schedule)) code = 1101;
        else if (!eligible(p)) code = 1204;
        else if (queue.get(p.getUid()) != null) code = 1562;
        p.sendPacket(StygianProtocol.start(type, dungeon, code));
        if (code != 0) return;
        queue.enqueue(p.getUid(), dungeon, schedule.id(), p.getWorldLevel(), now());
        sources.put(p.getUid(), new Source(p, p.getScene(), p.getWorldLevel()));
        p.sendPacket(StygianProtocol.info(dungeon, p.getUid(), (int) now()));
        Grasscutter.getLogger().info("Stygian matching queued uid={} dungeon={}", p.getUid(), dungeon);
        scheduleTick();
    }
    public synchronized void cancel(Player p, int type) {
        var r = type == 1 ? queue.get(p.getUid()) : null;
        p.sendPacket(StygianProtocol.packet(24288, 10, type, 12, r == null ? 1563 : 0));
        if (r != null) stop(r, 2);
    }
    public synchronized void confirm(Player p, int type, boolean agree, boolean guest) {
        var r = queue.get(p.getUid());
        boolean valid = type == 1 && !guest && r != null && current(r)
                && r.stage == StygianMatchQueue.Stage.CONFIRMING && now() < r.deadline;
        p.sendPacket(StygianProtocol.confirm(type, agree, guest, valid ? 0 : 1563));
        if (!valid) return;
        if (!agree) { stop(r, 2); return; }
        queue.confirm(p.getUid(), now());
        if (r.stage == StygianMatchQueue.Stage.PERMITS)
            send(r, StygianProtocol.packet(6439, 10, r.host, 15, 1));
    }
    public synchronized void permit(Player p, int host) {
        var r = queue.get(p.getUid());
        if (r != null && current(r)) queue.permit(p.getUid(), host, now());
    }
    public synchronized boolean reserved(int uid) { return queue.get(uid) != null; }
    private void scheduleTick() {
        if (!scheduled && !queue.runs().isEmpty()) {
            scheduled = true;
            server.getScheduler().scheduleDelayedTask(this::tick, 1);
        }
    }
    private synchronized void tick() {
        scheduled = false;
        try {
            for (var r : queue.runs()) {
                try {
                    if (now() >= r.deadline) { stop(r, r.stage == StygianMatchQueue.Stage.QUEUED ? 3 : 9); continue; }
                    if (!current(r)) { stop(r, 7); continue; }
                    if (queue.beginTransfer(r, now())) {
                        Player host = server.getPlayerByUid(r.host);
                        Player guest = server.getPlayerByUid(r.members.get(1));
                        // Both players have confirmed the match and sent AllowEnterMpAfterAgreeMatch.
                        // Use the existing MP system so its reconnect/guest ownership bookkeeping remains intact.
                        var mp = server.getMultiplayerSystem();
                        synchronized (mp) {
                            host.getCoopRequests().put(guest.getUid(), new CoopRequest(guest));
                            mp.applyEnterMpReply(host, guest.getUid(), true);
                        }
                        if (host.getWorld() != guest.getWorld()) { stop(r, 6); continue; }
                    } else if (r.stage == StygianMatchQueue.Stage.LOADING
                            && r.members.stream().allMatch(uid -> server.getPlayerByUid(uid)
                                    .getSceneLoadState() == SceneLoadState.LOADED)) {
                        enterMatched(r);
                    }
                } catch (Exception error) {
                    Grasscutter.getLogger().error("Stygian matching failed host={}", r.host, error);
                    stop(r, 6);
                }
            }
            for (var r : queue.pair(now()))
                send(r, StygianProtocol.success(r.dungeon, r.host, (int) r.deadline));
        } finally { scheduleTick(); }
    }
    private void enterMatched(StygianMatchQueue.Run r) {
        Player host = server.getPlayerByUid(r.host);
        Player guest = server.getPlayerByUid(r.members.get(1));
        var previous = guest.getScene();
        var position = new Position(guest.getPosition());
        int difficulty = LeyLineChallengeData.difficultyForDungeon(r.dungeon);
        int level = LeyLineChallengeData.levelIdForDifficulty(LeyLineChallengeData.currentSchedule(), difficulty);
        if (LeyLineChallengeEntry.enter(host, level, 1) != 0) { stop(r, 6); return; }
        var data = GameData.getDungeonDataMap().get(r.dungeon);
        // DungeonSystem must run once: a second call would replace the shared DungeonManager.
        if (!host.getWorld().transferPlayerToScene(guest, data.getSceneId(), data)) {
            stop(r, 6); return;
        }
        guest.setPrevSceneForDungeon(previous.getId());
        guest.setPrevPosForDungeon(position);
        Grasscutter.getLogger().info("Stygian matched entry host={} guest={} dungeon={}",
                host.getUid(), guest.getUid(), r.dungeon);
        stop(r, 1);
    }
    private void send(StygianMatchQueue.Run r, emu.grasscutter.net.packet.BasePacket packet) {
        for (int uid : r.members) {
            Source s = sources.get(uid);
            if (s != null && s.player().isOnline()) s.player().sendPacket(packet);
        }
    }
    private void stop(StygianMatchQueue.Run r, int reason) {
        send(r, StygianProtocol.stop(r.host, reason));
        queue.remove(r.host);
        for (int uid : r.members) sources.remove(uid);
    }
}
