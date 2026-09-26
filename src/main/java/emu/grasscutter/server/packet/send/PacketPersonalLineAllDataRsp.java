package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.LockedPersonallineDataOuterClass.LockedPersonallineData;
import emu.grasscutter.net.proto.PersonalLineAllDataRspOuterClass;
import java.util.*;
import java.util.stream.Collectors;

public class PacketPersonalLineAllDataRsp extends BasePacket {

    public PacketPersonalLineAllDataRsp(
            Collection<GameMainQuest> gameMainQuestList, Set<Integer> unlockedLines) {
        super(PacketOpcodes.PersonalLineAllDataRsp);

        var proto = PersonalLineAllDataRspOuterClass.PersonalLineAllDataRsp.newBuilder();

        var questList =
                gameMainQuestList.stream()
                        .map(GameMainQuest::getChildQuests)
                        .map(Map::values)
                        .flatMap(Collection::stream)
                        .map(GameQuest::getSubQuestId)
                        .collect(Collectors.toSet());

        // locked_personal_line_list and its fields are named by the 7.1 name translations. Lines
        // not started and not unlocked with a key are listed as locked, as in the private repo.
        GameData.getPersonalLineDataMap().values().stream()
                .filter(i -> !questList.contains(i.getStartQuestId()))
                .filter(i -> unlockedLines == null || !unlockedLines.contains(i.getId()))
                .forEach(
                        i ->
                                proto.addLockedPersonalLineList(
                                        LockedPersonallineData.newBuilder()
                                                .setPersonalLineId(i.getId())
                                                .setLockReason(
                                                        LockedPersonallineData.LockReason.LockReason_QUEST)
                                                .build()));

        this.setData(proto);
    }
}
