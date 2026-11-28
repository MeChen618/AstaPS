package emu.grasscutter.net;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.DungeonWayPointActivateReqOuterClass.DungeonWayPointActivateReq;
import emu.grasscutter.net.proto.HomeSceneJumpReqOuterClass.HomeSceneJumpReq;
import emu.grasscutter.net.proto.SceneEntityDrownReqOuterClass.SceneEntityDrownReq;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Messages mapped from kitkat-multiverse/genshin-protocol 7.1.0 with their 7.1 field numbers. */
final class KitkatMappedProtocolTest {
    @Test
    void opcodesAreMapped() {
        assertEquals(0, PacketOpcodes.SceneEntityDrownReq);
        assertEquals(0, PacketOpcodes.SceneEntityDrownRsp);
        assertEquals(27218, PacketOpcodes.DungeonWayPointActivateReq);
        assertEquals(25477, PacketOpcodes.HomeSceneJumpReq);
        assertEquals(22526, PacketOpcodes.SetPlayerHeadImageReq);
    }

    @Test
    void requestsUseCurrentFieldsAndRetainTheUnboundDrownDescriptor() throws Exception {
        // entity_id 5 on field 15, way_point_id 7 on field 3, is_enter_room_scene on field 8.
        assertEquals(5, SceneEntityDrownReq.parseFrom(HexFormat.of().parseHex("7805")).getEntityId());
        assertEquals(7, DungeonWayPointActivateReq.parseFrom(HexFormat.of().parseHex("7007")).getWayPointId());
        assertTrue(HomeSceneJumpReq.parseFrom(HexFormat.of().parseHex("4801")).getIsEnterRoomScene());
    }

    @Test
    void onlyVerifiedCurrentEmptyQueriesAreEnabled() {
        // 这两条空请求均已核对 7.2 原生发包链。
        assertEquals(3966, PacketOpcodes.GetWorldMpInfoReq);
        assertEquals(20856, PacketOpcodes.GetMapMarkTipsReq);
    }
}
