package emu.grasscutter.net.packet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.net.proto.ForgeGetQueueDataRspOuterClass.ForgeGetQueueDataRsp;
import emu.grasscutter.net.proto.ForgeQueueDataOuterClass.ForgeQueueData;
import org.junit.jupiter.api.Test;

final class ProtoFieldNumberOverridesTest {
    @Test
    void rewritesNestedFieldsOnSendAndRestoresThemOnReceive() throws Exception {
        var descriptor = ProtoFieldNumberOverrides.findMessageDescriptor("ForgeQueueData");
        ProtoFieldNumberOverrides.setFieldNumber(descriptor, "unfinish_count", 5);
        try {
            var queueData = ForgeQueueData.newBuilder()
                    .setUnfinishCount(12)
                    .setQueueId(1)
                    .build();
            var response = ForgeGetQueueDataRsp.newBuilder()
                    .putForgeQueueMap(1, queueData)
                    .build();

            byte[] onWire = ProtoFieldNumberOverrides.rewriteOutgoing(
                    PacketOpcodes.ForgeGetQueueDataRsp, response.toByteArray());
            var clientView = ForgeGetQueueDataRsp.parseFrom(onWire)
                    .getForgeQueueMapOrThrow(1);
            assertEquals(0, clientView.getUnfinishCount());
            assertTrue(clientView.getUnknownFields().hasField(5));

            byte[] restored = ProtoFieldNumberOverrides.rewriteIncoming(
                    PacketOpcodes.ForgeGetQueueDataRsp, null, onWire);
            assertEquals(12, ForgeGetQueueDataRsp.parseFrom(restored)
                    .getForgeQueueMapOrThrow(1).getUnfinishCount());
            assertArrayEquals(response.toByteArray(),
                    ProtoFieldNumberOverrides.toGeneratedFieldNumbers(
                            PacketOpcodes.ForgeGetQueueDataRsp, null, onWire));
        } finally {
            ProtoFieldNumberOverrides.clearFieldNumber(descriptor, "unfinish_count");
        }
    }

    @Test
    void resolvesNestedMessagesFromTheirProtoSource() {
        var descriptor = ProtoFieldNumberOverrides.findMessageDescriptor(
                "ShopCardProduct.proto:ShopCardProduct.ResinCard");

        assertNotNull(descriptor);
        assertEquals("ResinCard", descriptor.getName());
        assertEquals("base_item_list", descriptor.findFieldByNumber(1).getName());
    }
}
