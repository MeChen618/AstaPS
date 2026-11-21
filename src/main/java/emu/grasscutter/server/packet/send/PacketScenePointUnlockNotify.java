package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ScenePointUnlockNotifyOuterClass.ScenePointUnlockNotify;

public class PacketScenePointUnlockNotify extends BasePacket {
    public PacketScenePointUnlockNotify(int sceneId, int pointId) {
        super(PacketOpcodes.ScenePointUnlockNotify);

        ScenePointUnlockNotify.Builder p =
                ScenePointUnlockNotify.newBuilder().setSceneId(sceneId).addPointList(pointId);

        this.setData(p);
    }

    public static PacketScenePointUnlockNotify lock(int sceneId, int pointId) {
        return lock(sceneId, java.util.List.of(pointId));
    }

    public static PacketScenePointUnlockNotify lock(int sceneId, Iterable<Integer> pointIds) {
        PacketScenePointUnlockNotify packet = new PacketScenePointUnlockNotify();

        ScenePointUnlockNotify.Builder p =
                ScenePointUnlockNotify.newBuilder()
                        .setSceneId(sceneId)
                        .addAllLockedPointList(pointIds);

        packet.setData(p);
        return packet;
    }

    public static PacketScenePointUnlockNotify visibility(
            int sceneId, Iterable<Integer> pointIds, boolean visible) {
        var packet = new PacketScenePointUnlockNotify();
        var data = ScenePointUnlockNotify.newBuilder().setSceneId(sceneId);
        if (visible) data.addAllUnhidePointList(pointIds);
        else data.addAllHidePointList(pointIds);
        packet.setData(data);
        return packet;
    }

    private PacketScenePointUnlockNotify() {
        super(PacketOpcodes.ScenePointUnlockNotify);
    }

    public PacketScenePointUnlockNotify(int sceneId, Iterable<Integer> pointIds) {
        super(PacketOpcodes.ScenePointUnlockNotify);

        ScenePointUnlockNotify.Builder p =
                ScenePointUnlockNotify.newBuilder().setSceneId(sceneId).addAllPointList(pointIds);

        this.setData(p);
    }
}
