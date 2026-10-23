package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.fallback.MissingDomainFallbackManager;
import emu.grasscutter.game.managers.blossom.BlossomType;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SelectWorktopOptionReqOuterClass.SelectWorktopOptionReq;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSelectWorktopOptionRsp;

@Opcodes(PacketOpcodes.SelectWorktopOptionReq)
public class HandlerSelectWorktopOptionReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SelectWorktopOptionReq req = SelectWorktopOptionReq.parseFrom(payload);

        try {
            GameEntity entity = session.getPlayer().getScene().getEntityById(req.getGadgetEntityId());
            EntityGadget outcrop = entity instanceof EntityGadget gadget ? gadget : null;
            if (req.getOptionId() == 187
                    || (outcrop != null && BlossomType.valueOf(outcrop.getGadgetId()) != null)) {
                var worktop = outcrop != null
                                && outcrop.getContent() instanceof emu.grasscutter.game.entity.gadget.GadgetWorktop wt
                        ? wt : null;
                Grasscutter.getLogger().info(
                        "Ley-line option request uid={} entity={} option={} present={} gadget={} "
                                + "group={} config={} state={} content={} options={} enabled={}",
                        session.getPlayer().getUid(), req.getGadgetEntityId(), req.getOptionId(),
                        outcrop != null, outcrop == null ? 0 : outcrop.getGadgetId(),
                        outcrop == null ? 0 : outcrop.getGroupId(),
                        outcrop == null ? 0 : outcrop.getConfigId(),
                        outcrop == null ? 0 : outcrop.getState(),
                        outcrop == null || outcrop.getContent() == null
                                ? "null" : outcrop.getContent().getClass().getSimpleName(),
                        worktop == null ? java.util.Set.of() : worktop.getWorktopOptions(),
                        outcrop != null && outcrop.toProto().getGadget().getIsEnableInteract());
            }

            if (!(entity instanceof EntityGadget)) {
                return;
            }

            if (entity instanceof EntityGadget jadeGadget
                    && jadeGadget.getContent() instanceof emu.grasscutter.game.entity.gadget.GadgetLiyueJadeMaterials jade) {
                jade.openCage(session.getPlayer(), req.getOptionId());
                return;
            }

            // Andrius first: avoid lua suite-3 CreateMonster stacking a second boss.
            if (entity instanceof EntityGadget andriusGadget
                    && emu.grasscutter.game.world.AndriusTrialStartHelper.tryStart(
                            session.getPlayer(), andriusGadget, req.getOptionId())) {
                return;
            }

            session.getPlayer().getScene().selectWorktopOptionWith(req);
            session
                    .getPlayer()
                    .getScene()
                    .getScriptManager()
                    .callEvent(
                            new ScriptArgs(
                                    entity.getGroupId(),
                                    EventType.EVENT_SELECT_OPTION,
                                    entity.getConfigId(),
                                    req.getOptionId()));
            session
                    .getPlayer()
                    .getQuestManager()
                    .queueEvent(
                            QuestContent.QUEST_CONTENT_WORKTOP_SELECT, entity.getConfigId(), req.getOptionId());

            MissingDomainFallbackManager.handleSelectWorktopOption(
                    session.getPlayer().getScene(), entity, req.getOptionId());

            // Secret-realm entrance: the option enters the domain. This is the private-server F fallback;
            // officially it goes through the activity page EnterReq.
            if (entity instanceof EntityGadget leyGadget
                    && emu.grasscutter.game.activity.leylinechallenge.LeyLineChallengeGroupLoader
                            .isEntranceGadget(leyGadget)
                    && req.getOptionId()
                            == emu.grasscutter.game.activity.leylinechallenge.LeyLineChallengeGroupLoader
                                    .ENTER_OPTION_ID) {
                emu.grasscutter.game.activity.leylinechallenge.LeyLineChallengeEnterFlow.enter(
                        session.getPlayer(), 1, true);
            }
        } finally {
            // Always send packet
            session.send(new PacketSelectWorktopOptionRsp(req.getGadgetEntityId(), req.getOptionId()));
        }
    }
}
