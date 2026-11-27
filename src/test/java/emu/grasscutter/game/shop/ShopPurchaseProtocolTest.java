package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.BuyGoodsReqOuterClass.BuyGoodsReq;
import emu.grasscutter.net.proto.BuyGoodsRspOuterClass.BuyGoodsRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerBuyGoodsReq;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.Utils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

final class ShopPurchaseProtocolTest {
    private static final int SHOP_TYPE = 1001;
    private static final int GOODS_ID = 102578;
    private static final int MATERIAL_ID = 112125;
    private static final int STARDUST_ID = 222;

    @Test
    void advertisesRemainingPacksOnThe72Wire() throws Exception {
        var goods = ShopGoodsBuilder.fromShopInfo(stardustGoods(), 27, 0).build();
        var fields = readVarints(goods.toByteArray());
        assertEquals(30, fields.get(11), "period limit");
        assertEquals(27, fields.get(15), "packs already exchanged");
        assertEquals(3, fields.get(1983), "maximum for the next exchange");
        assertEquals(3, goods.getGoodsItem().getCount(), "materials in one pack");
    }

    @Test
    void buysTheAdvertisedPacksAndReturnsPurchaseQuantityAndUpdatedStock() throws Exception {
        var player = new Player() {
            @Override public void save() {}
            @Override public void sendPacket(BasePacket packet) {}
        };
        var session = new RecordingSession(serverWith(stardustGoods()));
        session.setPlayer(player);
        var inventory = player.getInventory();
        var items = GameData.getItemDataMap();
        var oldStardust = items.putIfAbsent(STARDUST_ID, material(STARDUST_ID));
        var oldMaterial = items.putIfAbsent(MATERIAL_ID, material(MATERIAL_ID));
        try {
            assertTrue(inventory.addItem(STARDUST_ID, 200));
            player.addShopLimit(GOODS_ID, 27, Utils.getCurrentSeconds() + 86400);

            var rejected = exchange(session, 4);
            assertEquals(Retcode.RET_SHOP_BATCH_BUY_COUNT_LIMIT.getNumber(), rejected.getRetcode());
            assertEquals(200, inventory.getFirstItem(STARDUST_ID).getCount());
            assertNull(inventory.getFirstItem(MATERIAL_ID));
            assertEquals(27, player.getGoodsLimit(GOODS_ID).getHasBoughtInPeriod());

            var accepted = exchange(session, 3);
            assertEquals(Retcode.RET_SUCC.getNumber(), accepted.getRetcode());
            assertEquals(3, accepted.getBuyCount());
            assertEquals(185, inventory.getFirstItem(STARDUST_ID).getCount());
            assertEquals(9, inventory.getFirstItem(MATERIAL_ID).getCount());
            assertEquals(30, player.getGoodsLimit(GOODS_ID).getHasBoughtInPeriod());
            var updated = accepted.getGoodsList(0);
            assertEquals(30, updated.getBuyLimit());
            assertEquals(30, updated.getBoughtNum());
            assertEquals(0, updated.getSingleLimit());
        } finally {
            if (oldMaterial == null) items.remove(MATERIAL_ID);
            if (oldStardust == null) items.remove(STARDUST_ID);
        }
    }

    private static ShopInfo stardustGoods() {
        return JsonUtils.decode("""
                {"goodsId":102578,"goodsItem":{"id":112125,"count":3},
                 "costItemList":[{"id":222,"count":5}],"buyLimit":30,
                 "refreshType":"SHOP_REFRESH_MONTHLY","shopRefreshParam":1}
                """, ShopInfo.class);
    }

    private static ItemData material(int id) {
        return JsonUtils.decode("""
                {"id":%d,"itemType":"ITEM_MATERIAL","materialType":"MATERIAL_NONE","stackLimit":9999}
                """.formatted(id), ItemData.class);
    }

    private static BuyGoodsRsp exchange(RecordingSession session, int quantity) throws Exception {
        var request = BuyGoodsReq.newBuilder()
                .setShopType(SHOP_TYPE)
                .setGoods(ShopGoodsBuilder.fromShopInfo(stardustGoods(), 27, 0))
                .setBuyCount(quantity)
                .build();
        session.reply = null;
        new HandlerBuyGoodsReq().handle(session, new byte[0], request.toByteArray());
        assertNotNull(session.reply);
        return BuyGoodsRsp.parseFrom(session.reply.getData());
    }

    private static Map<Integer, Integer> readVarints(byte[] payload) throws Exception {
        var fields = new HashMap<Integer, Integer>();
        var input = CodedInputStream.newInstance(payload);
        for (int tag; (tag = input.readTag()) != 0; ) {
            if (WireFormat.getTagWireType(tag) == WireFormat.WIRETYPE_VARINT) {
                fields.put(WireFormat.getTagFieldNumber(tag), input.readUInt32());
            } else {
                input.skipField(tag);
            }
        }
        return fields;
    }

    private static GameServer serverWith(ShopInfo goods) throws Exception {
        var unsafe = Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        var server = (GameServer) ((Unsafe) unsafe.get(null)).allocateInstance(GameServer.class);
        var shop = new ShopSystem(server);
        shop.getShopData().put(SHOP_TYPE, List.of(goods));
        var field = GameServer.class.getDeclaredField("shopSystem");
        field.setAccessible(true);
        field.set(server, shop);
        return server;
    }

    private static final class RecordingSession extends GameSession {
        private BasePacket reply;

        private RecordingSession(GameServer server) {
            super(server);
        }

        @Override public void send(BasePacket packet) {
            if (packet.getOpcode() == PacketOpcodes.BuyGoodsRsp) reply = packet;
        }
    }
}
