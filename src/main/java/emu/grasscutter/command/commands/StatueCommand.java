package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketGetSceneAreaRsp;
import emu.grasscutter.server.packet.send.PacketGetScenePointRsp;
import emu.grasscutter.server.packet.send.PacketLevelupCityRsp;
import emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify;
import java.util.List;

/**
 * Lock/unlock Statue-of-the-Seven for testing the unlock F flow.
 *
 * <p>{@code /statue lock} — locks the nearest statue ({@code maxSpringVolume} or Nod-Krai SotS
 * gadgets). Nod-Krai City 7: points {@code 1515}/{@code 1516}/{@code 1517} (areas 70/71/72).
 *
 * <p>{@code /statue level <cityId> <level>} — set SotS / city level (e.g. {@code /statue level 8
 * 1} resets Snezhnaya so you can re-offer).
 */
@Command(
        label = "statue",
        aliases = {"sots"},
        usage = {
            "lock [pointId]",
            "unlock [pointId]",
            "status [pointId]",
            "level <cityId> <level>",
            "city [cityId]"
        },
        permission = "player.teleport",
        permissionTargeted = "player.teleport.others")
public final class StatueCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.isEmpty()) {
            this.sendUsageMessage(sender);
            return;
        }

        String op = args.get(0).toLowerCase();

        // City-level ops do not need a statue point.
        if (op.equals("level") || op.equals("city") || op.equals("reset")) {
            handleCityLevel(sender, targetPlayer, op, args);
            return;
        }

        int sceneId = targetPlayer.getSceneId();
        Integer pointId = null;
        if (args.size() >= 2) {
            try {
                pointId = Integer.parseInt(args.get(1));
            } catch (NumberFormatException e) {
                CommandHandler.sendMessage(sender, "Not a point id: " + args.get(1));
                return;
            }
        }

        if (pointId == null) {
            pointId = findNearestStatue(targetPlayer, sceneId);
            if (pointId == null) {
                CommandHandler.sendMessage(
                        sender, "No nearby statue found. Pass a point id, e.g. /statue lock 471");
                return;
            }
        }

        switch (op) {
            case "lock" -> {
                targetPlayer.getUnlockedScenePoints(sceneId).remove(pointId);
                targetPlayer.getForceLockedScenePoints(sceneId).add(pointId);

                // Unlock tip Talk needs gate quest ≠ finished (state 3).
                var entry = GameData.getScenePointEntryById(sceneId, pointId);
                if (entry != null && entry.getPointData() != null) {
                    int questId =
                            emu.grasscutter.game.managers.StatueTalkQuests.questForArea(
                                    entry.getPointData().getAreaId());
                    if (questId > 0) {
                        // state 2 = QUEST_STATE_UNFINISHED
                        targetPlayer.sendPacket(
                                new emu.grasscutter.server.packet.send.PacketQuestListUpdateNotify(
                                        questId, 303, 2));
                    }
                }

                targetPlayer.save();
                targetPlayer.sendPacket(PacketScenePointUnlockNotify.lock(sceneId, pointId));
                targetPlayer.sendPacket(new PacketGetScenePointRsp(targetPlayer, sceneId));
                CommandHandler.sendMessage(
                        sender,
                        "Locked scene "
                                + sceneId
                                + " statue/point "
                                + pointId
                                + " for uid "
                                + targetPlayer.getUid()
                                + ". Walk away & back. Expect unlock F (not goddess).");
            }
            case "unlock" -> {
                targetPlayer.getForceLockedScenePoints(sceneId).remove(pointId);
                boolean ok =
                        targetPlayer.getProgressManager().unlockTransPoint(sceneId, pointId, true);
                if (!ok) {
                    targetPlayer.getUnlockedScenePoints(sceneId).add(pointId);
                    targetPlayer.sendPacket(new PacketScenePointUnlockNotify(sceneId, pointId));
                    targetPlayer.sendPacket(new PacketGetScenePointRsp(targetPlayer, sceneId));
                }
                targetPlayer.save();
                CommandHandler.sendMessage(
                        sender,
                        (ok ? "Unlocked" : "Already unlocked / refreshed")
                                + " scene "
                                + sceneId
                                + " point "
                                + pointId);
            }
            case "status" -> {
                boolean force = targetPlayer.isScenePointForceLocked(sceneId, pointId);
                boolean unlocked = targetPlayer.getUnlockedScenePoints(sceneId).contains(pointId);
                var entry = GameData.getScenePointEntryById(sceneId, pointId);
                Integer spring =
                        entry != null && entry.getPointData() != null
                                ? entry.getPointData().getMaxSpringVolume()
                                : null;
                CommandHandler.sendMessage(
                        sender,
                        "scene="
                                + sceneId
                                + " point="
                                + pointId
                                + " forceLocked="
                                + force
                                + " unlockedSet="
                                + unlocked
                                + " maxSpring="
                                + spring);
            }
            default -> this.sendUsageMessage(sender);
        }
    }

    private void handleCityLevel(
            Player sender, Player targetPlayer, String op, List<String> args) {
        var sots = targetPlayer.getSotsManager();
        if (sots == null) {
            CommandHandler.sendMessage(sender, "No SotS manager");
            return;
        }

        if (op.equals("city")) {
            Integer cityId = null;
            if (args.size() >= 2) {
                try {
                    cityId = Integer.parseInt(args.get(1));
                } catch (NumberFormatException e) {
                    CommandHandler.sendMessage(sender, "Not a city id: " + args.get(1));
                    return;
                }
            }
            if (cityId == null) {
                // Dump all known cities on this account.
                var map = targetPlayer.getCityInfoData();
                if (map == null || map.isEmpty()) {
                    CommandHandler.sendMessage(sender, "cityInfo empty");
                    return;
                }
                StringBuilder sb = new StringBuilder("cityInfo uid=").append(targetPlayer.getUid());
                for (var e : map.entrySet()) {
                    var c = e.getValue();
                    sb.append(" | city")
                            .append(e.getKey())
                            .append(" Lv.")
                            .append(c.getLevel())
                            .append(" crystal=")
                            .append(c.getNumCrystal());
                }
                CommandHandler.sendMessage(sender, sb.toString());
                return;
            }
            var info = sots.getCityInfo(cityId);
            CommandHandler.sendMessage(
                    sender,
                    "uid="
                            + targetPlayer.getUid()
                            + " city"
                            + cityId
                            + " Lv."
                            + info.getLevel()
                            + " crystal="
                            + info.getNumCrystal());
            return;
        }

        // level / reset
        int cityId;
        int level;
        if (op.equals("reset")) {
            try {
                cityId = args.size() >= 2 ? Integer.parseInt(args.get(1)) : 8;
            } catch (NumberFormatException e) {
                CommandHandler.sendMessage(sender, "Not a city id: " + args.get(1));
                return;
            }
            level = 1;
        } else {
            if (args.size() < 3) {
                CommandHandler.sendMessage(sender, "Usage: /statue level <cityId> <level>");
                return;
            }
            try {
                cityId = Integer.parseInt(args.get(1));
                level = Integer.parseInt(args.get(2));
            } catch (NumberFormatException e) {
                CommandHandler.sendMessage(sender, "cityId/level must be integers");
                return;
            }
        }

        if (cityId < 1 || cityId > 8 || level < 1 || level > 10) {
            CommandHandler.sendMessage(sender, "cityId 1-8, level 1-10");
            return;
        }

        var info = sots.getCityInfo(cityId);
        int before = info.getLevel();
        info.setLevel(level);
        info.setNumCrystal(0);
        sots.addCityInfo(info);
        targetPlayer.save();

        int sceneId = targetPlayer.getSceneId();
        int areaId = 0;
        targetPlayer.sendPacket(
                new PacketLevelupCityRsp(sceneId, info.getLevel(), cityId, 0, areaId, 0));
        targetPlayer.sendPacket(new PacketGetSceneAreaRsp(targetPlayer, sceneId));

        CommandHandler.sendMessage(
                sender,
                "Set city"
                        + cityId
                        + " Lv."
                        + before
                        + " → Lv."
                        + level
                        + " (crystal=0) for uid "
                        + targetPlayer.getUid()
                        + ". Re-open map / re-offer to verify.");
    }

    private static Integer findNearestStatue(Player player, int sceneId) {
        var pointIds = GameData.getScenePointsPerScene().get(sceneId);
        if (pointIds == null || pointIds.isEmpty()) {
            return null;
        }
        var pos = player.getPosition();
        Integer best = null;
        double bestDist = Double.MAX_VALUE;
        for (int pointId : pointIds) {
            var entry = GameData.getScenePointEntryById(sceneId, pointId);
            if (entry == null || entry.getPointData() == null) continue;
            var data = entry.getPointData();
            if (!emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(data)) continue;
            var p = data.getPos();
            if (p == null) continue;
            double dx = p.getX() - pos.getX();
            double dy = p.getY() - pos.getY();
            double dz = p.getZ() - pos.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = pointId;
            }
        }
        // Only accept if within ~80m
        if (best != null && bestDist <= 80 * 80) {
            return best;
        }
        return best; // still return nearest even if far — better than nothing
    }
}
