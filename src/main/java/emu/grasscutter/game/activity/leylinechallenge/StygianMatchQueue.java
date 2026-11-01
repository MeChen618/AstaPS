package emu.grasscutter.game.activity.leylinechallenge;

import java.util.*;

/** Two solo entrants of the same difficulty/world level; requires both native confirmations. */
public final class StygianMatchQueue {
    public enum Stage { QUEUED, CONFIRMING, PERMITS, LOADING }
    public static final class Run {
        public final int host, dungeon, schedule, worldLevel;
        public final List<Integer> members = new ArrayList<>();
        public final Set<Integer> agreed = new HashSet<>(), permitted = new HashSet<>();
        public Stage stage = Stage.QUEUED;
        public long deadline;
        Run(int uid, int dungeon, int schedule, int worldLevel, long now) {
            this.host = uid; this.dungeon = dungeon; this.schedule = schedule;
            this.worldLevel = worldLevel; this.deadline = now + 300; members.add(uid);
        }
    }
    private final Map<Integer, Run> byUid = new LinkedHashMap<>();
    public Run get(int uid) { return byUid.get(uid); }
    public List<Run> runs() { return new ArrayList<>(new LinkedHashSet<>(byUid.values())); }
    public boolean enqueue(int uid, int dungeon, int schedule, int worldLevel, long now) {
        if (byUid.containsKey(uid)) return false;
        byUid.put(uid, new Run(uid, dungeon, schedule, worldLevel, now)); return true;
    }
    public List<Run> pair(long now) {
        var paired = new ArrayList<Run>();
        var waiting = runs();
        for (int i = 0; i < waiting.size(); i++) {
            Run a = waiting.get(i);
            if (get(a.host) != a || a.stage != Stage.QUEUED || now >= a.deadline) continue;
            for (int j = i + 1; j < waiting.size(); j++) {
                Run b = waiting.get(j);
                if (b.stage != Stage.QUEUED || now >= b.deadline || get(b.host) != b
                        || a.dungeon != b.dungeon || a.schedule != b.schedule
                        || a.worldLevel != b.worldLevel) continue;
                a.members.add(b.host); byUid.put(b.host, a);
                a.stage = Stage.CONFIRMING; a.deadline = now + 30;
                paired.add(a); break;
            }
        }
        return paired;
    }
    public boolean confirm(int uid, long now) {
        Run r = get(uid);
        if (r == null || r.stage != Stage.CONFIRMING || now >= r.deadline) return false;
        r.agreed.add(uid);
        if (r.agreed.size() == r.members.size()) { r.stage = Stage.PERMITS; r.deadline = now + 30; }
        return true;
    }
    public boolean permit(int uid, int host, long now) {
        Run r = get(uid);
        if (r == null || r.stage != Stage.PERMITS || r.host != host || now >= r.deadline) return false;
        r.permitted.add(uid); return true;
    }
    public boolean beginTransfer(Run r, long now) {
        if (get(r.host) != r || r.stage != Stage.PERMITS || now >= r.deadline
                || r.permitted.size() != r.members.size()) return false;
        r.stage = Stage.LOADING; r.deadline = now + 90; return true;
    }
    public Run remove(int uid) {
        Run r = get(uid);
        if (r != null) for (int member : r.members) byUid.remove(member, r);
        return r;
    }
}
