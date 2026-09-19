/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.database;

import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import java.util.concurrent.ThreadLocalRandom;

public final class PlayerUidAllocator {
    public static final int MIN_UID = 50000;
    public static final int MAX_UID = 59999;

    private PlayerUidAllocator() {
    }

    public static synchronized int next(int n) {
        int n2;
        // Preserve the existing account-level reserved UID contract. The patched allocator's
        // 50000-59999 range is the fallback for accounts without an explicit reservation.
        if (n > 0 && !DatabaseHelper.checkIfPlayerExists(n)) {
            return n;
        }
        ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
        for (n2 = 0; n2 < 400; ++n2) {
            int n3 = 50000 + threadLocalRandom.nextInt(10000);
            if (DatabaseHelper.checkIfPlayerExists(n3)) continue;
            return n3;
        }
        for (n2 = 50000; n2 <= 59999; ++n2) {
            if (DatabaseHelper.checkIfPlayerExists(n2)) continue;
            return n2;
        }
        // Keep the datastore allocator as a last-resort escape hatch if the compatibility range is
        // exhausted, matching the pre-patch server behavior instead of failing account creation.
        do {
            n2 = emu.grasscutter.database.DatabaseManager.getNextId(Player.class);
        } while (DatabaseHelper.checkIfPlayerExists(n2));
        return n2;
    }

    public static synchronized void assign(Player player, int n) {
        int n2 = PlayerUidAllocator.next(n);
        player.setUid(n2);
        DatabaseHelper.savePlayer(player);
    }
}
