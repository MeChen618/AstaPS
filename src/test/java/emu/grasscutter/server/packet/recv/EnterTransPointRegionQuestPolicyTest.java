package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class EnterTransPointRegionQuestPolicyTest {
    @Test
    void nativeQuestingDoesNotAutoUnlockStatuesOnProximity() {
        assertFalse(HandlerEnterTransPointRegionNotify.allowsAutomaticStatueUnlock(true));
    }

    @Test
    void questingOffRetainsLegacyProximityUnlock() {
        assertTrue(HandlerEnterTransPointRegionNotify.allowsAutomaticStatueUnlock(false));
    }
}
