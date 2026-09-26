package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SceneDataNotifyOuterClass.SceneDataNotify;

public class PacketSceneDataNotify extends BasePacket {
    public PacketSceneDataNotify(int sceneId) {
        super(PacketOpcodes.SceneDataNotify);
        this.setData(SceneDataNotify.newBuilder().setSceneId(sceneId).build());
    }
}
