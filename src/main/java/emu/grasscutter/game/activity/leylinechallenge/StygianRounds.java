package emu.grasscutter.game.activity.leylinechallenge;

/** State changes are driven by explicit start and the active boss's death, never scene loading. */
public final class StygianRounds {
    public enum Phase { PREPARE, BATTLE, COMPLETE, EXITED }
    private volatile Phase phase = Phase.PREPARE;
    private volatile int cleared, boss;
    public Phase phase() { return phase; }
    public int cleared() { return cleared; }
    public int round() { return cleared + 1; }
    public int boss() { return boss; }
    public boolean start(int entityId) {
        if (phase != Phase.PREPARE || entityId <= 0) return false;
        boss = entityId; phase = Phase.BATTLE; return true;
    }
    public boolean defeated(int entityId) {
        if (phase != Phase.BATTLE || entityId != boss) return false;
        boss = 0; cleared++;
        phase = cleared == 3 ? Phase.COMPLETE : Phase.PREPARE;
        return true;
    }
    public void exit() { phase = Phase.EXITED; boss = 0; }
}
