package emu.grasscutter.game.activity.leylinechallenge;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StygianProtocolTest {
    private static byte[] roundRequest(int round, boolean trial, int... avatars) throws IOException {
        var teamBytes = new ByteArrayOutputStream();
        var team = CodedOutputStream.newInstance(teamBytes);
        for (int id : avatars) {
            var avatarBytes = new ByteArrayOutputStream();
            var avatar = CodedOutputStream.newInstance(avatarBytes);
            avatar.writeUInt32(5, id);
            if (trial) avatar.writeBool(15, true);
            avatar.flush();
            team.writeByteArray(8, avatarBytes.toByteArray());
        }
        team.flush();
        var bytes = new ByteArrayOutputStream();
        var out = CodedOutputStream.newInstance(bytes);
        out.writeUInt32(14, round);
        out.writeByteArray(10, teamBytes.toByteArray());
        out.flush();
        return bytes.toByteArray();
    }

    @Test
    void roundRequestKeepsSelectionOrderAndRejectsInvalidTeams() throws IOException {
        var request = StygianRoundRequest.parse(roundRequest(2, false, 10000007, 10000021));
        assertEquals(2, request.round());
        assertEquals(List.of(10000007, 10000021), request.avatars());
        assertThrows(IOException.class, () -> StygianRoundRequest.parse(roundRequest(0, false, 1)));
        assertThrows(IOException.class, () -> StygianRoundRequest.parse(roundRequest(4, false, 1)));
        assertThrows(IOException.class, () -> StygianRoundRequest.parse(roundRequest(1, false, 1, 1)));
        assertThrows(IOException.class, () -> StygianRoundRequest.parse(roundRequest(1, true, 1)));
        assertThrows(IOException.class, () -> StygianRoundRequest.parse(roundRequest(1, false, 1, 2, 3, 4, 5)));
    }

    @Test
    void nativeStateCarriesRoundRecordsInFieldFourteen() throws IOException {
        var records = List.of(
                new StygianNativeState.RoundRecord(25, true, 1, 123, List.of(10000007)),
                new StygianNativeState.RoundRecord(0, false, 2, 123, List.of(10000021)));
        var packet = StygianNativeState.packet(123, 1, 2, true, 42L, 83302, List.of(), records);
        assertEquals(1439, packet.getOpcode());
        var in = CodedInputStream.newInstance(packet.getData());
        var rounds = new ArrayList<byte[]>();
        for (int tag; (tag = in.readTag()) != 0;) {
            if (tag == 114) rounds.add(in.readByteArray());
            else in.skipField(tag);
        }
        assertEquals(2, rounds.size());
        var first = StygianProtocol.fields(rounds.get(0));
        var second = StygianProtocol.fields(rounds.get(1));
        assertEquals(25, first.get(1));
        assertEquals(1, first.get(9));
        assertEquals(1, first.get(14));
        assertEquals(2, second.get(14));
    }
}
