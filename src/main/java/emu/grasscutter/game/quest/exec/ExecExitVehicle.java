package emu.grasscutter.game.quest.exec;

import emu.grasscutter.data.excels.quest.QuestData.QuestExecParam;
import emu.grasscutter.game.ability.NatsaurusVehicleHelper;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestValueExec;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;

@QuestValueExec(QuestExec.QUEST_EXEC_EXIT_VEHICLE)
public final class ExecExitVehicle extends QuestExecHandler {
    @Override
    public boolean execute(GameQuest quest, QuestExecParam condition, String... params) {
        if (params != null && params.length > 1) return false;
        int gadgetId = 0;
        if (params != null && params.length == 1) {
            try {
                gadgetId = Integer.parseInt(params[0]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (gadgetId < 0) return false;
        }

        var player = quest.getOwner();
        var scene = player.getScene();
        if (scene == null) return true;
        synchronized (scene) {
            if (player.getScene() != scene) return true;
            for (var entity : scene.getEntities().values()) {
                if (!(entity instanceof EntityVehicle vehicle)
                        || (gadgetId != 0 && vehicle.getGadgetId() != gadgetId)) {
                    continue;
                }
                synchronized (vehicle) {
                    var member = vehicle.getVehicleMembers().stream()
                            .filter(rider -> rider.getUid() == player.getUid())
                            .findFirst()
                            .orElse(null);
                    if (member != null) {
                        NatsaurusVehicleHelper.dismount(player, vehicle, member);
                        return true;
                    }
                }
            }
        }
        return true;
    }
}
