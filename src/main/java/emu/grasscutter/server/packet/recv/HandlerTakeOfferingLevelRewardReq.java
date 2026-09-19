package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.entity.gadget.OfferingHelper;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import com.google.protobuf.CodedInputStream;

/** 供奉奖励页领取；7.0：offering_id=1, level=10。 */
@Opcodes(PacketOpcodes.TakeOfferingLevelRewardReq)
public class HandlerTakeOfferingLevelRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        if (player == null) {
            return;
        }
        int offeringId = OfferingHelper.OFFERING_ORAIONOKAMI;
        int takeLevel = 0;
        if (payload != null && payload.length > 0) {
            CodedInputStream in = CodedInputStream.newInstance(payload);
            while (!in.isAtEnd()) {
                int tag = in.readTag();
                int field = tag >>> 3;
                int wt = tag & 7;
                if (wt != 0) {
                    in.skipField(tag);
                    continue;
                }
                int v = in.readUInt32();
                if (field == 1) {
                    offeringId = v;
                } else if (field == 10) {
                    takeLevel = v;
                }
            }
        }
        if (takeLevel <= 0) {
            takeLevel = 1;
        }
        OfferingHelper.tryTakeLevelReward(player, offeringId, takeLevel, header);
    }
}
