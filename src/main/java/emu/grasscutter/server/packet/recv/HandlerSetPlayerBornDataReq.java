package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.config.Configuration.*;

import emu.grasscutter.*;
import emu.grasscutter.command.commands.SendMailCommand.MailBuilder;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SetPlayerBornDataReqOuterClass.SetPlayerBornDataReq;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.*;
import java.util.Arrays;

@Opcodes(PacketOpcodes.SetPlayerBornDataReq)
public class HandlerSetPlayerBornDataReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SetPlayerBornDataReq req = SetPlayerBornDataReq.parseFrom(payload);

        int avatarId = req.getAvatarId();
        int startingSkillDepot;
        if (avatarId == GameConstants.MAIN_CHARACTER_MALE) {
            startingSkillDepot = 504;
        } else if (avatarId == GameConstants.MAIN_CHARACTER_FEMALE) {
            startingSkillDepot = 704;
        } else {
            session.send(new PacketSetPlayerBornDataRsp(-1));
            return;
        }

        if (!GameData.getAvatarDataMap().containsKey(avatarId)) {
            Grasscutter.getLogger()
                    .error("No avatar data found! Please check your ExcelBinOutput folder.");
            session.send(new PacketSetPlayerBornDataRsp(-1));
            session.close();
            return;
        }

        Player player = session.getPlayer();
        player.setNickname(req.getNickName());

        if (player.getAvatars().getAvatarCount() == 0) {
            Avatar mainCharacter = new Avatar(avatarId);

            if (!GAME_OPTIONS.questing.enabled) {
                mainCharacter.setSkillDepotData(
                        GameData.getAvatarSkillDepotDataMap().get(startingSkillDepot));
            }

            player.addAvatar(mainCharacter, false);
            player.setMainCharacterId(avatarId);
            player.setHeadImage(avatarId);
            var team = player.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars();
            team.clear();
            team.add(mainCharacter.getAvatarId());
            player.save();
        } else {
            return;
        }

        // The working play/rino 7.1 birth flow arms the native-intro gate before acknowledging
        // birth. A fast client can send the first pause cycle immediately after the response.
        session.setState(SessionState.ACTIVE);
        BornIntroGate.armNativeIntro(session);

        // Publish the chosen Traveler and name before acknowledging SetPlayerBornDataReq.
        // The client initializes its post-born quest actor/tracking state after this boundary.
        // Keep the legacy response-opcode override for deployments that explicitly configure it.
        session.send(new PacketAvatarDataNotify(player));
        session.send(new PacketPlayerNicknameNotify(req.getNickName()));
        int configuredRsp = GAME_OPTIONS.newAccountIntro.setPlayerBornDataRsp;
        if (configuredRsp > 0 && configuredRsp != PacketSetPlayerBornDataRsp.CMD_ID) {
            session.send(new BasePacket(configuredRsp));
        } else {
            session.send(new PacketSetPlayerBornDataRsp());
        }

        Grasscutter.getLogger()
                .info(
                        "[intro] character creation finished: {} picked avatar {}; waiting for native intro handoff.",
                        req.getNickName(),
                        avatarId);

        var welcomeMail = GAME_INFO.joinOptions.welcomeMail;
        MailBuilder mailBuilder = new MailBuilder(player.getUid(), new Mail());
        mailBuilder.mail.mailContent.title = welcomeMail.title;
        mailBuilder.mail.mailContent.sender = welcomeMail.sender;
        mailBuilder.mail.mailContent.content =
                welcomeMail.content
                        + "\n<type=\"browser\" text=\"GitHub\" href=\"https://github.com/Grasscutters/Grasscutter\"/>";
        mailBuilder.mail.itemList.addAll(Arrays.asList(welcomeMail.items));
        mailBuilder.mail.importance = 1;
        player.sendMail(mailBuilder.mail);
    }
}
