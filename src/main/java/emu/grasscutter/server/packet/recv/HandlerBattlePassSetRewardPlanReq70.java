/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.protobuf.CodedOutputStream
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.net.packet.Opcodes
 *  emu.grasscutter.net.packet.PacketHandler
 *  emu.grasscutter.net.proto._BattlePassSetRewardPlanReq
 *  emu.grasscutter.server.game.GameSession
 *  emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify
 *  emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify
 */
package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.battlepass.BattlePassCompatHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

@Opcodes(value=4415)
public class HandlerBattlePassSetRewardPlanReq70
extends PacketHandler {
    public void handle(GameSession gameSession, byte[] byArray, byte[] byArray2) throws Exception {
        Player player = gameSession.getPlayer();
        int n = 1;
        boolean bl = false;
        try {
            n = HandlerBattlePassSetRewardPlanReq70.readVarintField(byArray2, 14);
            boolean bl2 = bl = HandlerBattlePassSetRewardPlanReq70.readVarintField(byArray2, 1) != 0 || HandlerBattlePassSetRewardPlanReq70.readVarintField(byArray2, 15) != 0;
            if (n <= 0) {
                n = HandlerBattlePassSetRewardPlanReq70.readVarintField(byArray2, 3);
            }
            if (n <= 0) {
                n = 1;
            }
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("SetRewardPlan parse failed", throwable);
        }
        if (player != null) {
            BattlePassCompatHelper.setSelectedPlan(player, n);
            Grasscutter.getLogger().info("SetRewardPlan uid={} plan={} noRemind={} payloadHex={}", new Object[]{player.getUid(), n, bl, HandlerBattlePassSetRewardPlanReq70.bytesToHex(byArray2)});
        }
        gameSession.send(HandlerBattlePassSetRewardPlanReq70.buildRsp(n, bl));
        if (player != null) {
            player.sendPacket((BasePacket)new PacketBattlePassCurScheduleUpdateNotify(player));
            player.sendPacket((BasePacket)new PacketBeyondBattlePassCurScheduleUpdateNotify(player));
        }
    }

    private static String bytesToHex(byte[] byArray) {
        if (byArray == null || byArray.length == 0) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder(Math.min(byArray.length, 64) * 2);
        int n = Math.min(byArray.length, 64);
        for (int i = 0; i < n; ++i) {
            stringBuilder.append(String.format("%02x", byArray[i] & 0xFF));
        }
        if (byArray.length > 64) {
            stringBuilder.append("...");
        }
        return stringBuilder.toString();
    }

    private static BasePacket buildRsp(int n, boolean bl) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance((OutputStream)byteArrayOutputStream);
        codedOutputStream.writeBool(1, bl);
        codedOutputStream.writeUInt32(5, 0);
        for (int i = 1; i <= 5; ++i) {
            codedOutputStream.writeUInt32(13, i);
        }
        codedOutputStream.writeUInt32(14, n);
        codedOutputStream.writeBool(15, bl);
        codedOutputStream.flush();
        BasePacket basePacket = new BasePacket(3557);
        basePacket.setData(byteArrayOutputStream.toByteArray());
        return basePacket;
    }

    private static int readVarintField(byte[] byArray, int n) {
        if (byArray == null) {
            return 0;
        }
        int n2 = 0;
        while (n2 < byArray.length) {
            int n3;
            int n4 = byArray[n2++] & 0xFF;
            int n5 = n4 >>> 3;
            int n6 = n4 & 7;
            if (n6 == 0) {
                long l = 0L;
                n3 = 0;
                while (n2 < byArray.length) {
                    int n7 = byArray[n2++] & 0xFF;
                    l |= (long)(n7 & 0x7F) << n3;
                    if ((n7 & 0x80) == 0) break;
                    if ((n3 += 7) <= 35) continue;
                    return 0;
                }
                if (n5 != n) continue;
                return (int)l;
            }
            if (n6 == 2) {
                int n8 = 0;
                int n9 = 0;
                while (n2 < byArray.length) {
                    n3 = byArray[n2++] & 0xFF;
                    n8 |= (n3 & 0x7F) << n9;
                    if ((n3 & 0x80) == 0) break;
                    n9 += 7;
                }
                n2 += n8;
                continue;
            }
            if (n6 == 5) {
                n2 += 4;
                continue;
            }
            if (n6 == 1) {
                n2 += 8;
                continue;
            }
            return 0;
        }
        return 0;
    }
}

