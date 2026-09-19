package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerEnterLevelRspOuterClass.TowerEnterLevelRsp;
import java.util.List;

public class PacketTowerEnterLevelRsp extends BasePacket {

    public PacketTowerEnterLevelRsp(int floorId, int levelIndex, List<Integer> towerBuffIds) {
        super(PacketOpcodes.TowerEnterLevelRsp);

        var builder =
                TowerEnterLevelRsp.newBuilder()
                        .setFloorId(floorId)
                        .setLevelIndex(levelIndex);
        if (towerBuffIds != null) {
            builder.addAllTowerBuffIdList(towerBuffIds);
        }
        this.setData(builder.build());
    }
}
