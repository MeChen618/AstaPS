package emu.grasscutter.game.ability;

/** 爱可菲长按 E「即兴烹饪」相关协议号。 */
public final class EscoffierSkillCookOpcodes {
    /** 客户端充能完成请求发菜（空 payload）。 */
    public static final int COOK_REQ = 26785;
    /** 发菜结果回包。 */
    public static final int COOK_RSP = 8444;
    /** 周次数剩余/上限同步（字段过多会毒化充能条）。 */
    public static final int COOK_DATA_NOTIFY = 21842;

    private EscoffierSkillCookOpcodes() {}
}
