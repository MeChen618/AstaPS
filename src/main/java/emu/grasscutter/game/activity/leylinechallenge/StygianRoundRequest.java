package emu.grasscutter.game.activity.leylinechallenge;

import com.google.protobuf.CodedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** EDOKCAJDBBI: round 14, team 10; team.avatar_list 8; avatar id 5/trial 15. */
public record StygianRoundRequest(int round, List<Integer> avatars) {
    public static StygianRoundRequest parse(byte[] payload) throws IOException {
        var in = CodedInputStream.newInstance(payload);
        int round = 0; var avatars = new ArrayList<Integer>(); boolean teamSeen = false;
        for (int tag; (tag = in.readTag()) != 0;) {
            if (tag == 112) round = in.readUInt32();
            else if (tag == 82) {
                if (teamSeen) throw new IOException("Duplicate team");
                teamSeen = true;
                var team = CodedInputStream.newInstance(in.readByteArray());
                for (int t; (t = team.readTag()) != 0;) {
                    if (t != 66) { team.skipField(t); continue; }
                    var avatar = CodedInputStream.newInstance(team.readByteArray());
                    int id = 0; boolean trial = false;
                    for (int a; (a = avatar.readTag()) != 0;) {
                        if (a == 40) id = avatar.readUInt32();
                        else if (a == 120) trial = avatar.readBool();
                        else avatar.skipField(a);
                    }
                    if (trial || id <= 0 || avatars.size() >= 4) throw new IOException("Invalid avatar selection");
                    avatars.add(id);
                }
            } else in.skipField(tag);
        }
        if (round < 1 || round > 3 || avatars.isEmpty()
                || new HashSet<>(avatars).size() != avatars.size()) throw new IOException("Invalid round/team");
        return new StygianRoundRequest(round, List.copyOf(avatars));
    }
}
