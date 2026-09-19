/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;

@Opcodes(value=20526)
public class HandlerAnecdoteGetDataReq
extends PacketHandler {
    @Override
    public void handle(GameSession gameSession, byte[] byArray, byte[] byArray2) {
        gameSession.send(new BasePacket(7897));
        Grasscutter.getLogger().debug("Handled AnecdoteGetDataReq (20526) for uid {}", (Object)gameSession.getPlayer().getUid());
    }
}
