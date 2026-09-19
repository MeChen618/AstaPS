package emu.grasscutter.game.dps;

import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;

/**
 * 把当前正在处理的 {@link AttackResult} 挂到线程上，供 {@link DPSEntity} 在 {@code damage}
 * 回调里读取反应类型等字段，避免改动庞大的 {@code Scene#handleAttack}。
 */
public final class DPSAttackContext {
    private static final ThreadLocal<AttackResult> CURRENT = new ThreadLocal<>();

    private DPSAttackContext() {}

    public static void set(AttackResult result) {
        CURRENT.set(result);
    }

    public static AttackResult get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
