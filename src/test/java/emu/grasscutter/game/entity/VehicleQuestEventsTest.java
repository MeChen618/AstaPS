package emu.grasscutter.game.entity;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.QuestEventRecorder;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.VehicleInteractTypeOuterClass.VehicleInteractType;
import emu.grasscutter.net.proto.VehicleMemberOuterClass.VehicleMember;
import emu.grasscutter.server.packet.send.PacketVehicleInteractRsp;

import org.junit.jupiter.api.Test;

import java.util.*;

final class VehicleQuestEventsTest {
    @Test
    void packetRetriesCannotDuplicateMembershipOrQuestEvents() {
        var owner = new TestPlayer(42);
        var scene = new TestWorld.TestScene(TestWorld.create(owner), 3, SceneType.SCENE_WORLD);
        var vehicle = new EntityVehicle(scene, owner, 45001001, 0, new Position(), new Position());
        var original = member(42, 7);
        var changedAvatar = member(42, 99);
        vehicle.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 123);
        new PacketVehicleInteractRsp(
                vehicle, original, VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_IN);
        new PacketVehicleInteractRsp(
                vehicle, original, VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_IN);
        assertEquals(1, vehicle.getVehicleMembers().size());
        assertEquals(List.of(QuestContent.QUEST_CONTENT_ENTER_VEHICLE), owner.quests.types());
        new PacketVehicleInteractRsp(
                vehicle,
                changedAvatar,
                VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_OUT);
        new PacketVehicleInteractRsp(
                vehicle,
                changedAvatar,
                VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_OUT);
        assertTrue(vehicle.getVehicleMembers().isEmpty());
        assertEquals(
                List.of(
                        QuestContent.QUEST_CONTENT_ENTER_VEHICLE,
                        QuestContent.QUEST_CONTENT_LEAVE_VEHICLE),
                owner.quests.types());
        assertEquals(123, vehicle.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(List.of(45001001, 45001001), owner.quests.firstParams());
    }

    @Test
    void aGuestLeavingTriggersTheGuestsQuestRatherThanTheOwners() {
        var owner = new TestPlayer(42);
        var guest = new TestPlayer(43);
        var scene = new TestWorld.TestScene(TestWorld.create(owner), 3, SceneType.SCENE_WORLD);
        scene.getPlayers().add(guest);
        var vehicle = new EntityVehicle(scene, owner, 45001001, 0, new Position(), new Position());
        assertTrue(vehicle.updateMember(member(43, 9), true));
        assertTrue(vehicle.updateMember(member(43, 10), false));
        assertTrue(owner.quests.types().isEmpty());
        assertEquals(
                List.of(
                        QuestContent.QUEST_CONTENT_ENTER_VEHICLE,
                        QuestContent.QUEST_CONTENT_LEAVE_VEHICLE),
                guest.quests.types());
        assertFalse(vehicle.updateMember(member(99, 9), false));
    }

    private static VehicleMember member(int uid, long guid) {
        return VehicleMember.newBuilder().setUid(uid).setAvatarGuid(guid).build();
    }

    private static final class TestPlayer extends Player {
        final QuestEventRecorder quests = new QuestEventRecorder(this);

        TestPlayer(int uid) {
            setUid(uid);
        }

        @Override
        public QuestManager getQuestManager() {
            return quests.manager();
        }

        @Override
        public void sendPacket(BasePacket packet) {}
    }
}
