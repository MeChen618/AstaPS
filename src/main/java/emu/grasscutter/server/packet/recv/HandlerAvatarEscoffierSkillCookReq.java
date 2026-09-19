package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.ability.EscoffierSkillCookHelper;
import emu.grasscutter.game.ability.EscoffierSkillCookOpcodes;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;

/** 爱可菲即兴烹饪：锅充能完成后客户端上报，服务端发菜。 */
@Opcodes(value = EscoffierSkillCookOpcodes.COOK_REQ)
public class HandlerAvatarEscoffierSkillCookReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        Player player = session != null ? session.getPlayer() : null;
        if (player == null) {
            return;
        }
        Grasscutter.getLogger()
                .info("[EscoffierCook] recv SkillCookReq uid={}", player.getUid());
        EscoffierSkillCookHelper.handleCookRequest(player);
    }
}
