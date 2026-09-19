package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetReliquaryStarStateRsp;
import emu.grasscutter.server.packet.send.PacketStoreItemChangeNotify;
import emu.grasscutter.utils.ProtoWire;

import java.util.List;
import java.util.Map;

/** SetReliquaryStarStateReq — toggle relic star; also ack so relic UI can close. */
@Opcodes(PacketOpcodes.SetReliquaryStarStateReq)
public class HandlerSetReliquaryStarStateReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        long guid = 0L;
        boolean starred = false;
        Map<Integer, List<Object>> fields = ProtoWire.parse(payload);
        if (fields.get(2) != null && !fields.get(2).isEmpty()) {
            Object v = fields.get(2).get(0);
            if (v instanceof Long l) {
                guid = l;
            } else if (v instanceof Integer i) {
                guid = i & 0xFFFFFFFFL;
            }
        }
        List<Integer> flags = ProtoWire.asUint32List(fields.get(6));
        if (!flags.isEmpty()) {
            starred = flags.get(0) != 0;
        }

        GameItem item = session.getPlayer().getInventory().getItemByGuid(guid);
        if (item != null && item.getItemType() == emu.grasscutter.game.inventory.ItemType.ITEM_RELIQUARY) {
            item.setRelicStarred(starred);
            item.save();
            session.send(new PacketStoreItemChangeNotify(item));
        }

        session.send(new PacketSetReliquaryStarStateRsp(guid, starred));
    }
}
