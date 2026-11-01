package emu.grasscutter.game.activity.leylinechallenge;

/** Arena variants, not the open-world local-legend variant 25139001. */
public final class StygianBossRoster {
    private static final int[][] MONSTERS = {
        {25139002, 24090501, 25135202},
        {25139003, 24090502, 25135205},
        {25139004, 24090503, 25135206},
        {25139005, 24090504, 25135207}
    };
    private static final int[] LEVELS = {40,70,90,100,105,110};
    private StygianBossRoster() {}
    public static int monster(int difficulty, String icon) {
        if(difficulty<1 || difficulty>6)throw new IllegalArgumentException("Invalid difficulty");
        int index = icon.contains("Karzelek") ? 0 : icon.contains("DragonCollar") ? 1
                : icon.contains("Snegurochka_Contessa") ? 2 : -1;
        if(index<0)throw new IllegalArgumentException("Unsupported Stygian monster icon: "+icon);
        return MONSTERS[Math.max(0,difficulty-3)][index];
    }
    public static int level(int difficulty) {
        if(difficulty<1 || difficulty>6)throw new IllegalArgumentException("Invalid difficulty");
        return LEVELS[difficulty-1];
    }
}
