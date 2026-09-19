package emu.grasscutter.game.ability;

/** Protocol ids for Escoffier's hold-E improvised cooking. */
public final class EscoffierSkillCookOpcodes {
    /** Client asks for the dish once charging completes; empty payload. */
    public static final int COOK_REQ = 26785;
    /** Dish result response. */
    public static final int COOK_RSP = 8444;
    /** Weekly remaining/cap sync; sending too many fields poisons the charge bar. */
    public static final int COOK_DATA_NOTIFY = 21842;

    private EscoffierSkillCookOpcodes() {}
}
