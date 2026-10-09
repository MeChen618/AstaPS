package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EnterTransPointRegionNotifyOuterClass.EnterTransPointRegionNotify;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.Utils;
import java.util.ArrayList;
import java.util.List;

@Opcodes(PacketOpcodes.EnterTransPointRegionNotify)
public class HandlerEnterTransPointRegionNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        String hex = Utils.bytesToHex(payload == null ? new byte[0] : payload);
        String tags = dumpVarintTags(payload);
        int uid = session.getPlayer() != null ? session.getPlayer().getUid() : 0;
        try {
            EnterTransPointRegionNotify notify = EnterTransPointRegionNotify.parseFrom(payload);
            int sceneId = notify.getSceneId();
            int pointId = notify.getPointId();
            // ===== 7.1 真字段布局未确认：proto 解析不出来时，从原始 varint 猜 scene/point =====
            if (sceneId <= 0 || pointId <= 0) {
                int curScene =
                        (session.getPlayer() != null && session.getPlayer().getScene() != null)
                                ? session.getPlayer().getSceneId()
                                : 0;
                int[] sp = bruteScenePoint(payload, curScene);
                if (sp != null) {
                    sceneId = sp[0];
                    pointId = sp[1];
                }
            }
            Grasscutter.getLogger()
                    .info(
                            "EnterTransPoint uid={} sceneId={} pointId={} hex={} tags={}",
                            uid,
                            sceneId,
                            pointId,
                            hex,
                            tags);

            var player = session.getPlayer();
            // Locked statues: auto-unlock on enter — no Talk/quest playthrough required for F
            // or map unlock. Works whether questing is on or off.
            if (player != null && sceneId > 0 && pointId > 0) {
                try {
                    var entry =
                            emu.grasscutter.data.GameData.getScenePointEntryById(sceneId, pointId);
                    boolean isStatue =
                            entry != null
                                    && emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(
                                            entry.getPointData());
                    boolean locked =
                            isStatue
                                    && (player.isScenePointForceLocked(sceneId, pointId)
                                            || !player.getUnlockedScenePoints(sceneId)
                                                    .contains(pointId));
                    if (locked) { // 靠近就解锁；已解锁则完全不碰
                        try {
                            player.getProgressManager().miaoUnlockStatue(sceneId, pointId);
                            player.sendPacket(
                                    new emu.grasscutter.server.packet.send.PacketGetSceneAreaRsp(
                                            player, sceneId));
                        } catch (Throwable t2) {
                        }
                    } else if (isStatue) {
                    }
                } catch (Throwable t) {
                    Grasscutter.getLogger()
                            .warn("Auto-unlock statue failed uid={} point={}", uid, pointId, t);
                }
            }

            // Only nudge unlock notify for points that are already unlocked server-side.
            if (false && sceneId > 0 // 已关闭“靠近即发激活提示”
                    && pointId > 0
                    && player != null
                    && !player.isScenePointForceLocked(sceneId, pointId)
                    && player.getUnlockedScenePoints(sceneId).contains(pointId)) {
                session.send(
                        new emu.grasscutter.server.packet.send.PacketScenePointUnlockNotify(
                                sceneId, pointId));
                // Re-push Talk gate so goddess F appears without playing 303xx.
                player.getProgressManager().refreshStatueTalkGate(sceneId, pointId);
                // Re-scan nearby NPC suites — Fontaine+ SotS groups often lack server Lua;
                // loadNpcForPlayer now still sends GroupSuiteNotify so the goddess appears.
                try {
                    player.getScene().loadNpcForPlayerEnter(player);
                } catch (Throwable ignored) {
                }
            }
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .warn(
                            "EnterTransPoint parse failed uid={} err={} hex={} tags={}",
                            uid,
                            e.toString(),
                            hex,
                            tags);
        }
        session.getPlayer().getSotsManager().handleEnterTransPointRegionNotify();
    }

    /**
     * 从 payload 的 protobuf varint 字段里猜 scene/point：将值等于“当前场景 id”的那个字段
     * 当作 scene_id，另一个字段的值当作 point_id。用来兼容 7.1 客户端与本地 proto 字段号不一致的情况。
     */
    private static int[] bruteScenePoint(byte[] payload, int curScene) {
        if (payload == null || payload.length == 0 || curScene <= 0) return null;
        java.util.List<int[]> kv = new java.util.ArrayList<>();
        int i = 0;
        try {
            while (i < payload.length) {
                long key = 0;
                int shift = 0;
                while (i < payload.length) {
                    int b = payload[i++] & 0xff;
                    key |= (long) (b & 0x7f) << shift;
                    if ((b & 0x80) == 0) break;
                    shift += 7;
                    if (shift > 28) return null;
                }
                int field = (int) (key >>> 3);
                int wire = (int) (key & 0x7);
                if (field <= 0) return null;
                if (wire == 0) {
                    long val = 0;
                    shift = 0;
                    while (i < payload.length) {
                        int b = payload[i++] & 0xff;
                        val |= (long) (b & 0x7f) << shift;
                        if ((b & 0x80) == 0) break;
                        shift += 7;
                        if (shift > 28) return null;
                    }
                    kv.add(new int[] {field, (int) val});
                } else if (wire == 2) {
                    long len = 0;
                    shift = 0;
                    while (i < payload.length) {
                        int b = payload[i++] & 0xff;
                        len |= (long) (b & 0x7f) << shift;
                        if ((b & 0x80) == 0) break;
                        shift += 7;
                        if (shift > 28) return null;
                    }
                    if (len < 0 || i + len > payload.length) return null;
                    i += (int) len;
                } else if (wire == 5) {
                    i += 4;
                } else if (wire == 1) {
                    i += 8;
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
            return null;
        }
        if (kv.size() < 2) return null;
        int sc = -1, pt = -1, ptField = -1;
        for (int[] e : kv) {
            if (e[1] == curScene && sc < 0) {
                sc = e[1];
                continue;
            }
            if (pt < 0) {
                pt = e[1];
                ptField = e[0];
            }
        }
        if (sc <= 0 || pt <= 0) return null;
        return new int[] {sc, pt};
    }

    /** Dump protobuf field_number->varint for quick wire-layout checks. */
    private static String dumpVarintTags(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return "[]";
        }
        List<String> out = new ArrayList<>();
        int i = 0;
        try {
            while (i < payload.length) {
                long key = 0;
                int shift = 0;
                while (i < payload.length) {
                    int b = payload[i++] & 0xff;
                    key |= (long) (b & 0x7f) << shift;
                    if ((b & 0x80) == 0) {
                        break;
                    }
                    shift += 7;
                    if (shift > 63) {
                        return out + "+badKey";
                    }
                }
                int field = (int) (key >>> 3);
                int wire = (int) (key & 0x7);
                if (wire == 0) {
                    long val = 0;
                    shift = 0;
                    while (i < payload.length) {
                        int b = payload[i++] & 0xff;
                        val |= (long) (b & 0x7f) << shift;
                        if ((b & 0x80) == 0) {
                            break;
                        }
                        shift += 7;
                        if (shift > 63) {
                            return out + "+badVarint";
                        }
                    }
                    out.add(field + "=" + val);
                } else if (wire == 2) {
                    long len = 0;
                    shift = 0;
                    while (i < payload.length) {
                        int b = payload[i++] & 0xff;
                        len |= (long) (b & 0x7f) << shift;
                        if ((b & 0x80) == 0) {
                            break;
                        }
                        shift += 7;
                        if (shift > 63) {
                            return out + "+badLen";
                        }
                    }
                    if (len < 0 || i + len > payload.length) {
                        return out + "+badLenVal";
                    }
                    out.add(field + ":bytes(" + len + ")");
                    i += (int) len;
                } else if (wire == 5) {
                    if (i + 4 > payload.length) {
                        return out + "+badI32";
                    }
                    out.add(field + ":i32");
                    i += 4;
                } else if (wire == 1) {
                    if (i + 8 > payload.length) {
                        return out + "+badI64";
                    }
                    out.add(field + ":i64");
                    i += 8;
                } else {
                    out.add(field + ":wire" + wire);
                    break;
                }
            }
        } catch (Exception e) {
            return out + "+ex:" + e.getClass().getSimpleName();
        }
        return out.toString();
    }
}
