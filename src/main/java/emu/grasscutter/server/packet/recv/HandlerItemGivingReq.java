package emu.grasscutter.server.packet.recv;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.giving.GivingData;
import emu.grasscutter.data.excels.giving.GivingData.GiveMethod;
import emu.grasscutter.data.excels.giving.GivingData.GiveType;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ItemGivingReqOuterClass.ItemGivingReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketItemGivingRsp;
import emu.grasscutter.server.packet.send.PacketItemGivingRsp.Mode;
import java.util.HashMap;
import java.util.Map;

@Opcodes(PacketOpcodes.ItemGivingReq)
public final class HandlerItemGivingReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = ItemGivingReq.parseFrom(payload);
        var player = session.getPlayer();
        if (player == null) return;

        // Run alongside quest transitions so a retry observes updated objective progress.
        QuestManager.eventExecutor.execute(() -> {
            if (session.getState() == GameSession.SessionState.INACTIVE
                    || player.getSession() != session) return;
            int givingId = req.getGivingId();
            var data = GameData.getGivingDataMap().get(givingId);
            boolean success = givingId > 0 && data != null && data.getGiveType() == GiveType.QUEST
                    && data.getGivingMethod() == GiveMethod.GIVING_METHOD_EXACT
                    && player.getQuestManager().tryCompleteExactItemGiving(
                            givingId, () -> submitExactItems(player.getInventory(), data, req));
            session.send(success
                    ? new PacketItemGivingRsp(givingId, Mode.EXACT_SUCCESS)
                    : new PacketItemGivingRsp());
        });
    }

    private static boolean submitExactItems(Inventory inventory, GivingData data, ItemGivingReq req) {
        var costs = data.getExactItems();
        if (costs == null) return false;
        var expected = new HashMap<Integer, Integer>();
        for (var cost : costs) {
            if (cost == null) return false;
            // The authored table pads its fixed-size array with empty entries.
            if (cost.getId() == 0 && cost.getCount() == 0) continue;
            if (!addQuantity(expected, cost.getId(), cost.getCount())) return false;
        }
        if (expected.isEmpty()) return false;

        boolean hasItemIds = req.getItemParamListCount() > 0;
        boolean hasGuids = req.getItemGuidCountMapCount() > 0;
        if (!hasItemIds && !hasGuids) return false;
        if (hasItemIds) {
            var byId = new HashMap<Integer, Integer>();
            for (var item : req.getItemParamListList()) {
                if (!addQuantity(byId, item.getItemId(), item.getCount())) return false;
            }
            if (!expected.equals(byId)) return false;
        }

        synchronized (inventory) {
            if (hasGuids) {
                var byGuid = new HashMap<Integer, Integer>();
                for (var entry : req.getItemGuidCountMapMap().entrySet()) {
                    var item = inventory.getItemByGuid(entry.getKey());
                    int count = entry.getValue();
                    if (item == null || item.getItemType() != ItemType.ITEM_MATERIAL
                            || item.getCount() < count
                            || inventory.getItemById(item.getItemId()) != item
                            || !addQuantity(byGuid, item.getItemId(), count)) {
                        return false;
                    }
                }
                if (!expected.equals(byGuid)) return false;
            }

            return data.isRemoveItem()
                    ? inventory.payItems(costs, 1, ActionReason.ItemGiving)
                    : inventory.canPayItems(costs, 1);
        }
    }

    private static boolean addQuantity(Map<Integer, Integer> quantities, int itemId, int count) {
        if (itemId <= 0 || count <= 0) return false;
        long total = (long) quantities.getOrDefault(itemId, 0) + count;
        if (total > Integer.MAX_VALUE) return false;
        quantities.put(itemId, (int) total);
        return true;
    }
}
