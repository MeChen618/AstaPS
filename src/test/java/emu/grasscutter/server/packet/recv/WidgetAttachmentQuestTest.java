package emu.grasscutter.server.packet.recv;

import static emu.grasscutter.game.quest.enums.QuestContent.QUEST_CONTENT_SPECIFIC_WIDGET_ATTACHED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.QuestEventRecorder;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.SetWidgetSlotReqOuterClass.SetWidgetSlotReq;
import emu.grasscutter.net.proto.WidgetSlotOpOuterClass.WidgetSlotOp;
import emu.grasscutter.server.game.GameSession;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class WidgetAttachmentQuestTest {
    @Test
    void queuesQuestProgressOnlyForAttachingAnOwnedWidget() throws Exception {
        var widget =
                new Gson().fromJson(
                        """
                        {
                          "id": 220133,
                          "itemType": "ITEM_MATERIAL",
                          "materialType": "MATERIAL_WIDGET",
                          "stackLimit": 1
                        }
                        """,
                        ItemData.class);
        int widgetId = widget.getId();
        var previous = GameData.getItemDataMap().put(widgetId, widget);
        try {
            var player = new RecordingPlayer();
            var session = new GameSession(null) {
                @Override
                public void send(BasePacket packet) {}
            };
            session.setPlayer(player);
            var handler = new HandlerSetWidgetSlotReq();
            var attach = SetWidgetSlotReq.newBuilder()
                    .setOp(WidgetSlotOp.WidgetSlotOp_ATTACH)
                    .setMaterialId(widgetId)
                    .build();
            var detach = attach.toBuilder().setOp(WidgetSlotOp.WidgetSlotOp_DETACH).build();

            handler.handle(session, new byte[0], attach.toByteArray());
            assertTrue(player.events.types().isEmpty());
            handler.handle(session, new byte[0], detach.toByteArray());

            player.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL)
                    .onAddItem(new GameItem(widget));
            handler.handle(session, new byte[0], attach.toByteArray());
            assertEquals(widgetId, player.getWidgetId());
            assertEquals(List.of(QUEST_CONTENT_SPECIFIC_WIDGET_ATTACHED), player.events.types());
            assertEquals(List.of(widgetId), player.events.firstParams());

            handler.handle(session, new byte[0], detach.toByteArray());
            assertEquals(0, player.getWidgetId());
            assertEquals(List.of(QUEST_CONTENT_SPECIFIC_WIDGET_ATTACHED), player.events.types());
        } finally {
            if (previous == null) GameData.getItemDataMap().remove(widgetId);
            else GameData.getItemDataMap().put(widgetId, previous);
        }
    }

    private static final class RecordingPlayer extends Player {
        private final QuestEventRecorder events = new QuestEventRecorder(this);

        @Override
        public QuestManager getQuestManager() {
            return events.manager();
        }
    }
}
