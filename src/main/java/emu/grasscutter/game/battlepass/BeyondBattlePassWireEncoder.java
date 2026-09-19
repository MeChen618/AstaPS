/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.battlepass;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.data.excels.BattlePassMissionData;
import emu.grasscutter.game.battlepass.BattlePassCompatHelper;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.battlepass.BattlePassMission;
import emu.grasscutter.game.props.BattlePassMissionStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

public final class BeyondBattlePassWireEncoder {
    public static final int FIXED_BEGIN = 1785528000;
    public static final int FIXED_END = 1795982399;
    private BeyondBattlePassWireEncoder() {
    }

    public static byte[] encodeAllDataNotify(BattlePassManager battlePassManager) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            CodedOutputStream codedOutputStream = CodedOutputStream.newInstance((OutputStream) byteArrayOutputStream);
            codedOutputStream.writeByteArray(4, BeyondBattlePassWireEncoder.encodeProduct());
            codedOutputStream.writeByteArray(5, BeyondBattlePassWireEncoder.encodeSchedule(battlePassManager));
            codedOutputStream.writeBool(10, true);
            codedOutputStream.writeUInt32(15, 6700);
            codedOutputStream.flush();
            return byteArrayOutputStream.toByteArray();
        }
        catch (IOException iOException) {
            throw new IllegalStateException("encode _BeyondBattlePassAllDataNotify failed", iOException);
        }
    }

    public static byte[] encodeCurScheduleUpdateNotify(BattlePassManager battlePassManager) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            CodedOutputStream codedOutputStream = CodedOutputStream.newInstance((OutputStream) byteArrayOutputStream);
            codedOutputStream.writeBool(2, true);
            codedOutputStream.writeByteArray(3, BeyondBattlePassWireEncoder.encodeSchedule(battlePassManager));
            codedOutputStream.writeUInt32(5, 6700);
            codedOutputStream.flush();
            return byteArrayOutputStream.toByteArray();
        }
        catch (IOException iOException) {
            throw new IllegalStateException("encode _BeyondBattlePassCurScheduleUpdateNotify failed", iOException);
        }
    }

    public static byte[] encodeMissionUpdateNotify(BattlePassMission battlePassMission) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            CodedOutputStream codedOutputStream = CodedOutputStream.newInstance((OutputStream) byteArrayOutputStream);
            BattlePassMissionData battlePassMissionData = battlePassMission != null ? battlePassMission.getData() : null;
            codedOutputStream.writeByteArray(6, BeyondBattlePassWireEncoder.encodeMission(battlePassMission, battlePassMissionData));
            codedOutputStream.flush();
            return byteArrayOutputStream.toByteArray();
        }
        catch (IOException iOException) {
            throw new IllegalStateException("encode _BeyondBattlePassMissionUpdateNotify failed", iOException);
        }
    }

    public static byte[] encodeMissionUpdateNotify(Collection<BattlePassMission> collection) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            CodedOutputStream codedOutputStream = CodedOutputStream.newInstance((OutputStream) byteArrayOutputStream);
            if (collection != null) {
                for (BattlePassMission battlePassMission : collection) {
                    if (battlePassMission == null) continue;
                    codedOutputStream.writeByteArray(6, BeyondBattlePassWireEncoder.encodeMission(battlePassMission, battlePassMission.getData()));
                }
            }
            codedOutputStream.flush();
            return byteArrayOutputStream.toByteArray();
        }
        catch (IOException iOException) {
            throw new IllegalStateException("encode _BeyondBattlePassMissionUpdateNotify failed", iOException);
        }
    }

    private static byte[] encodeSchedule(BattlePassManager battlePassManager) throws IOException {
        int n = battlePassManager != null ? battlePassManager.getLevel() : 0;
        int n2 = battlePassManager != null ? battlePassManager.getPoint() : 0;
        int n3 = BattlePassCompatHelper.beginTime();
        int n4 = BattlePassCompatHelper.endTime();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance((OutputStream) byteArrayOutputStream);
        boolean bl = battlePassManager != null && battlePassManager.isPaid();
        int n5 = bl ? 2 : 1;
        codedOutputStream.writeUInt32(1, n2);
        codedOutputStream.writeBool(2, true);
        codedOutputStream.writeBool(3, false);
        codedOutputStream.writeEnum(4, n5);
        codedOutputStream.writeUInt32(5, 6700);
        codedOutputStream.writeUInt32(6, n);
        codedOutputStream.writeUInt32(7, n4);
        codedOutputStream.writeUInt32(8, 0);
        codedOutputStream.writeUInt32(9, n);
        codedOutputStream.writeUInt32(12, 6700);
        codedOutputStream.writeByteArray(13, BeyondBattlePassWireEncoder.encodeProduct());
        codedOutputStream.flush();
        return byteArrayOutputStream.toByteArray();
    }

    private static byte[] encodeProduct() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance((OutputStream) byteArrayOutputStream);
        codedOutputStream.writeString(1, "10201");
        codedOutputStream.writeString(11, "10203");
        codedOutputStream.writeString(14, "10201");
        codedOutputStream.flush();
        return byteArrayOutputStream.toByteArray();
    }

    private static byte[] encodeMission(BattlePassMission battlePassMission, BattlePassMissionData battlePassMissionData) throws IOException {
        int n = battlePassMission != null ? battlePassMission.getId() : (battlePassMissionData != null ? battlePassMissionData.getId() : 0);
        int n2 = battlePassMissionData != null ? Math.max(1, battlePassMissionData.getProgress()) : 1;
        int n3 = battlePassMission != null ? battlePassMission.getProgress() : 0;
        int n4 = battlePassMissionData != null ? battlePassMissionData.getAddPoint() : 0;
        int n5 = 0;
        if (battlePassMissionData != null && battlePassMissionData.getRefreshType() != null) {
            n5 = battlePassMissionData.getRefreshType().getValue();
        }
        int n6 = BeyondBattlePassWireEncoder.toBeyondStatusValue(battlePassMission != null ? battlePassMission.getStatus() : null);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance((OutputStream) byteArrayOutputStream);
        codedOutputStream.writeUInt32(1, n2);
        codedOutputStream.writeUInt32(2, Math.min(Math.max(0, n3), n2));
        codedOutputStream.writeUInt32(3, n4);
        codedOutputStream.writeEnum(7, n6);
        codedOutputStream.writeUInt32(13, n5);
        codedOutputStream.writeUInt32(14, n);
        codedOutputStream.flush();
        return byteArrayOutputStream.toByteArray();
    }

    private static int toBeyondStatusValue(BattlePassMissionStatus battlePassMissionStatus) {
        if (battlePassMissionStatus == null) {
            return 1;
        }
        if (battlePassMissionStatus == BattlePassMissionStatus.MISSION_STATUS_FINISHED) {
            return 2;
        }
        if (battlePassMissionStatus == BattlePassMissionStatus.MISSION_STATUS_POINT_TAKEN) {
            return 3;
        }
        return 1;
    }
}
