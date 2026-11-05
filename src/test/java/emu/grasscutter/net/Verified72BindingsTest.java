package emu.grasscutter.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AvatarPromoteReqOuterClass.AvatarPromoteReq;
import emu.grasscutter.net.proto.AvatarPromoteReqParser;
import emu.grasscutter.net.proto.GetGachaInfoRspOuterClass.GetGachaInfoRsp;
import emu.grasscutter.net.proto.HomeResourceOuterClass.HomeResource;
import emu.grasscutter.net.proto.TakeFurnitureMakeRspOuterClass.TakeFurnitureMakeRsp;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class Verified72BindingsTest {
    @Test
    void ascensionUsesTheNativeRequestGuid() throws Exception {
        // Native 7.2 request: uint64 guid = 3, including GUID bits above 32 bits.
        var bytes = HexFormat.of().parseHex("188180808010");
        assertEquals(6528, PacketOpcodes.AvatarPromoteReq);
        assertEquals(0x100000001L, AvatarPromoteReq.parseFrom(bytes).getGuid());
        assertEquals(0x100000001L, AvatarPromoteReqParser.parseGuid(bytes));
        assertArrayEquals(
                bytes, AvatarPromoteReq.newBuilder().setGuid(0x100000001L).build().toByteArray());
        assertEquals(0, AvatarPromoteReqParser.parseGuid(new byte[] {0x18, (byte) 0x80}));
    }

    @Test
    void homeResourceKeepsStoredValueSeparateFromCapacity() throws Exception {
        var nativeData = HexFormat.of().parseHex("087b38e807");
        var resource = HomeResource.parseFrom(nativeData);
        assertEquals(123, resource.getStoreValue());
        assertEquals(1000, resource.getStoreLimit());

        var output = HomeResource.newBuilder().setStoreValue(123).setStoreLimit(1000).build();
        var input = CodedInputStream.newInstance(output.toByteArray());
        var fields = new HashMap<Integer, Integer>();
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            fields.put(tag >>> 3, input.readUInt32());
        }
        assertEquals(Map.of(1, 123, 7, 1000), fields);
    }

    @Test
    void gachaFlagsAreReadFromTheCurrentClientSlots() throws Exception {
        var bytes = HexFormat.of().parseHex("08017001");
        var response = GetGachaInfoRsp.parseFrom(bytes);
        assertTrue(response.getBKMCHIJHJJE());
        assertTrue(response.getLHBELFNPPNK());
        assertArrayEquals(bytes, response.toByteArray());
    }

    @Test
    void furnitureOutputDoesNotBecomeReturnedMaterials() throws Exception {
        var bytes = HexFormat.of().parseHex("0a0508ca0110013a0508cb011002");
        var response = TakeFurnitureMakeRsp.parseFrom(bytes);
        assertEquals(1, response.getOutputItemListCount());
        assertEquals(202, response.getOutputItemList(0).getItemId());
        assertEquals(1, response.getOutputItemList(0).getCount());
        assertEquals(1, response.getReturnItemListCount());
        assertEquals(203, response.getReturnItemList(0).getItemId());
        assertEquals(2, response.getReturnItemList(0).getCount());
        assertArrayEquals(bytes, response.toByteArray());
    }
}
