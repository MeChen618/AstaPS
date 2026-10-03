package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.born.BornDataHelper;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.PacketPlayerLoginRsp;

@Opcodes(PacketOpcodes.PlayerLoginReq)
public class HandlerPlayerLoginReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        if (session.getAccount() == null) {
            session.close();
            return;
        }

        Player player = session.getPlayer();
        var intro = GAME_OPTIONS.newAccountIntro;
        boolean freshAccount = player.getAvatars().getAvatarCount() == 0;
        boolean skipIntro = freshAccount && intro.skip;

        if (freshAccount && intro.enabled && !skipIntro) {
            // Native selection keeps the account unborn until SetPlayerBornDataReq. World/scene
            // initialization therefore remains outside this login request.
            session.setState(SessionState.PICKING_CHARACTER);

            int notifyCmdId =
                    intro.doSetPlayerBornDataNotify > 0
                            ? intro.doSetPlayerBornDataNotify
                            : PacketOpcodes.DoSetPlayerBornDataNotify;
            if (notifyCmdId > 0) session.send(new BasePacket(notifyCmdId));

            Grasscutter.getLogger()
                    .info(
                            "[born-flow] uid={} mode=select; waiting for native character selection (notify cmdId={}).",
                            player.getUid(),
                            notifyCmdId > 0 ? notifyCmdId : "unsent");
            session.send(new PacketPlayerLoginRsp(session));
            return;
        }

        boolean autoBornNow = false;
        if (freshAccount) {
            int avatarId = BornDataHelper.resolveAutomaticAvatarId();
            String nickname = BornDataHelper.resolveAutomaticNickname();
            if (!BornDataHelper.completeBirth(player, avatarId, nickname)) {
                Grasscutter.getLogger()
                        .error(
                                "[born-flow] uid={} automatic birth failed; closing incomplete session.",
                                player.getUid());
                session.close();
                return;
            }

            // Register before Player.onLogin so first-scene code knows this is a fresh-player
            // bootstrap. The normal scene entry is preserved; only Quest 351 waits for scene-ready.
            BornIntroGate.armSceneReady(session);
            BornDataHelper.sendWelcomeMail(player);
            autoBornNow = true;

            Grasscutter.getLogger()
                    .info(
                            "[born-flow] uid={} mode=auto avatar={} skipIntro={} introEnabled={}; visuals bypassed, scene-ready quest bootstrap preserved.",
                            player.getUid(),
                            avatarId,
                            skipIntro,
                            intro.enabled);
        } else {
            BornDataHelper.ensureMainCharacter(player);
        }

        player.onLogin();

        if (autoBornNow) {
            // World/login state is complete. Fresh-player quests still wait for the client's first
            // PostEnterSceneReq acknowledgement.
            BornIntroGate.markWorldLoginComplete(session);
        }

        session.send(new PacketPlayerLoginRsp(session));
    }
}
