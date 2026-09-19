package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;

/**
 * Handbook / 备战 "quick open" path. Proto dump has no named BuyResin-style message for 7.0;
 * parse field 1 (uint32 dungeon_entry_config_id) from the raw body.
 */
@Opcodes(PacketOpcodes.DungeonQuickOpenReq)
public class HandlerDungeonQuickOpenReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int entryConfigId = 0;
        if (payload != null && payload.length > 0) {
            try {
                CodedInputStream input = CodedInputStream.newInstance(payload);
                while (!input.isAtEnd()) {
                    int tag = input.readTag();
                    int field = tag >>> 3;
                    if (field == 1) {
                        entryConfigId = input.readUInt32();
                        break;
                    }
                    input.skipField(tag);
                }
            } catch (Exception ignored) {
            }
        }
        DomainDungeonHelper.handleQuickOpen(session.getPlayer(), entryConfigId);
        session.send(new BasePacket(PacketOpcodes.DungeonQuickOpenRsp));
    }
}
