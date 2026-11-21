package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.DungeonRestartGuard;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DungeonRestartRspOuterClass.DungeonRestartRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.DungeonRestartReq)
public class HandlerDungeonRestartReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        var scene = player == null ? null : player.getScene();
        var manager = scene == null ? null : scene.getDungeonManager();
        var response =
                DungeonRestartRsp.newBuilder().setRetcode(Retcode.RET_DUNGEON_ENTER_FAIL_VALUE);
        try {
            if (manager != null) {
                // 校验和重开共用领奖锁，避免重复请求重置尚未发完的奖励。
                synchronized (manager) {
                    if (player.getScene() == scene
                            && DungeonRestartGuard.mayRestart(player, payload)) {
                        response.setDungeonId(manager.getDungeonData().getId())
                                .setPointId(scene.getPrevScenePoint());
                        if (session.getServer().getDungeonSystem().restartDungeon(player)) {
                            response.setRetcode(Retcode.RET_SUCC_VALUE);
                        }
                    }
                }
            }
        } catch (Exception exception) {
            Grasscutter.getLogger()
                    .warn(
                            "Dungeon restart failed uid={}",
                            player == null ? 0 : player.getUid(),
                            exception);
        }
        if (response.getRetcode() != Retcode.RET_SUCC_VALUE) {
            Grasscutter.getLogger()
                    .debug(
                            "DungeonRestartReq denied uid={} scene={}",
                            player == null ? 0 : player.getUid(),
                            scene == null ? 0 : scene.getId());
        }
        var packet = new BasePacket(PacketOpcodes.DungeonRestartRsp);
        packet.setData(response);
        if (header != null && header.length > 0) packet.setHeader(header);
        session.send(packet);
    }
}
