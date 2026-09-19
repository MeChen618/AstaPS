/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.avatar;

import emu.grasscutter.Grasscutter;

public final class AvatarExtraLevelOpcodes {
    public static final int PROMOTE_REQ = 6091;
    public static final int PROMOTE_RSP = 28930;
    public static final int KAMPJK_REQ = 7979;
    public static final int KAMPJK_RSP = 7980;
    public static final int EXTRA_LEVEL_UPGRADE_REQ = 25098;
    public static final int EXTRA_LEVEL_UPGRADE_RSP = 27534;
    public static final int REQ = 0;
    public static final int RSP = 0;
    private static int discoveredReqOpcode;
    private static int discoveredRspOpcode;

    private AvatarExtraLevelOpcodes() {
    }

    public static boolean isKnownRequestOpcode(int n) {
        return n == 6091 || n == 7979 || n == 25098 || discoveredReqOpcode > 0 && n == discoveredReqOpcode;
    }

    public static void noteDiscoveredRequest(int n) {
        if (n <= 0 || discoveredReqOpcode > 0) {
            return;
        }
        discoveredReqOpcode = n;
        Grasscutter.getLogger().info("AvatarExtraLevel discovered REQ opcode={}", (Object)n);
    }

    public static void noteDiscoveredResponse(int n) {
        if (n <= 0 || discoveredRspOpcode > 0) {
            return;
        }
        discoveredRspOpcode = n;
        Grasscutter.getLogger().info("AvatarExtraLevel discovered RSP opcode={}", (Object)n);
    }

    public static int resolveRequestOpcode(int n) {
        if (discoveredReqOpcode > 0) {
            return discoveredReqOpcode;
        }
        return n;
    }

    public static int resolveResponseOpcode(int n) {
        if (discoveredRspOpcode > 0) {
            return discoveredRspOpcode;
        }
        if (n == 7979) {
            return 7980;
        }
        if (n == 25098) {
            return 27534;
        }
        if (n > 0 && n != 6091) {
            return n + 1;
        }
        return 0;
    }
}
