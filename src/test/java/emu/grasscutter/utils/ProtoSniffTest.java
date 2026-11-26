package emu.grasscutter.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ProtoSniffTest {
    @Test
    void describesLengthDelimitedMessagesToConfiguredDepth() {
        byte[] payload = {0x72, 0x02, 0x08, 0x05};

        assertEquals("#14=<2 bytes>", ProtoSniff.describe(payload, 1));
        assertEquals("#14=<2 bytes> { #1=varint(5) }", ProtoSniff.describe(payload, 2));
        assertEquals(ProtoSniff.describe(payload, 2), ProtoSniff.describe(payload));
    }

    @Test
    void leavesInvalidNestedPayloadsOpaque() {
        byte[] payload = {0x72, 0x01, (byte) 0x80};

        assertEquals("#14=<1 bytes>", ProtoSniff.describe(payload));
    }
}
