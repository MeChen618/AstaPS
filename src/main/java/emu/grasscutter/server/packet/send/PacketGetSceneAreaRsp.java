package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetSceneAreaRspOuterClass.GetSceneAreaRsp;
import java.util.stream.IntStream;

public class PacketGetSceneAreaRsp extends BasePacket {

    /** Unlock all scene areas so inventory「前往采集」can mark Natlan / Nod-Krai materials. */
    private static final java.util.List<Integer> ALL_AREAS =
            IntStream.rangeClosed(1, 1000).boxed().toList();

    public PacketGetSceneAreaRsp(Player player, int sceneId) {
        super(PacketOpcodes.GetSceneAreaRsp);

        this.buildHeader(0);

        // Persist unlock so later AreaUnlockNotify / save stay consistent.
        try {
            player.getUnlockedSceneAreas(sceneId).addAll(ALL_AREAS);
        } catch (Throwable ignored) {
        }

        GetSceneAreaRsp.Builder b =
                GetSceneAreaRsp.newBuilder()
                        .setSceneId(sceneId)
                        .addAllAreaIdList(ALL_AREAS);

        // Cities 1..10 (Mondstadt..Nod-Krai era); SotS trees / statue levels.
        for (int cityId = 1; cityId <= 10; cityId++) {
            try {
                b.addCityInfoList(player.getSotsManager().getCityInfo(cityId).toProto());
            } catch (Throwable ignored) {
            }
        }

        this.setData(b.build());
    }
}
