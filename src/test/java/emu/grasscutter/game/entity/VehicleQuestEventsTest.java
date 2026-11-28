package emu.grasscutter.game.entity;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestEventRecorder;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.QuestSystem;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.quest.exec.ExecExitVehicle;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.VehicleInteractRspOuterClass.VehicleInteractRsp;
import emu.grasscutter.net.proto.VehicleInteractTypeOuterClass.VehicleInteractType;
import emu.grasscutter.net.proto.VehicleMemberOuterClass.VehicleMember;
import emu.grasscutter.server.packet.send.PacketVehicleInteractRsp;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;
import java.util.concurrent.TimeUnit;

@ExtendWith(ServerResourceFixture.class)
final class VehicleQuestEventsTest {
    @Test
    void packetRetriesCannotDuplicateMembershipOrQuestEvents() {
        var owner = new TestPlayer(42);
        var scene = new TestWorld.TestScene(TestWorld.create(owner), 3, SceneType.SCENE_WORLD);
        var vehicle = new TestVehicle(scene, owner, 45001001);
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
        var vehicle = new TestVehicle(scene, owner, 45001001);
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

    @Test
    void authoredQuestExitDismountsOnlyTheMatchingGuestOnce() throws Exception {
        var owner = new TestPlayer(42);
        var guest = new TestPlayer(43);
        var scene = new TestWorld.TestScene(TestWorld.create(owner), 3, SceneType.SCENE_WORLD);
        scene.getPlayers().add(guest);
        guest.setScene(scene);
        var vehicle = addVehicle(scene, owner, 45007002);
        var ownerMember = member(42, 7);
        var guestMember = member(43, 9);
        assertTrue(vehicle.updateMember(ownerMember, true));
        assertTrue(vehicle.updateMember(guestMember, true));

        var quest = questOwnedBy(guest);
        var system = new QuestSystem(null);
        var differentVehicle = exitAction("\"45007001\"");
        system.triggerExec(quest, differentVehicle, differentVehicle.getParam());
        awaitQuestQueue();
        assertEquals(List.of(ownerMember, guestMember), vehicle.getVehicleMembers());
        assertTrue(guest.vehiclePackets.isEmpty());

        var action = exitAction("\"45007002\"");
        assertEquals(QuestExec.QUEST_EXEC_EXIT_VEHICLE, action.getType());
        system.triggerExec(quest, action, action.getParam());
        system.triggerExec(quest, action, action.getParam());
        awaitQuestQueue();

        assertEquals(List.of(ownerMember), vehicle.getVehicleMembers());
        assertSame(vehicle, scene.getEntities().get(vehicle.getId()));
        assertEquals(List.of(QuestContent.QUEST_CONTENT_ENTER_VEHICLE), owner.quests.types());
        assertEquals(
                List.of(QuestContent.QUEST_CONTENT_ENTER_VEHICLE, QuestContent.QUEST_CONTENT_LEAVE_VEHICLE),
                guest.quests.types());
        assertEquals(List.of(45007002, 45007002), guest.quests.firstParams());
        assertTrue(owner.vehiclePackets.isEmpty());
        assertEquals(1, guest.vehiclePackets.size());
        var packet = VehicleInteractRsp.parseFrom(guest.vehiclePackets.getFirst().getData());
        assertEquals(vehicle.getId(), packet.getEntityId());
        assertEquals(guestMember, packet.getMember());
        assertEquals(VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_OUT, packet.getInteractType());
    }

    @Test
    void exitAcceptsAnyVehicleFormsAndRejectsInvalidParams() {
        var owner = new TestPlayer(42);
        var scene = new TestWorld.TestScene(TestWorld.create(owner), 3, SceneType.SCENE_WORLD);
        scene.getPlayers().add(owner);
        owner.setScene(scene);
        var vehicle = addVehicle(scene, owner, 45007001);
        var ownerMember = member(42, 7);
        var quest = questOwnedBy(owner);
        var handler = new ExecExitVehicle();
        for (String args : List.of("", "\"0\"")) {
            assertTrue(vehicle.updateMember(ownerMember, true));
            var action = exitAction(args);
            assertFalse(handler.execute(quest, action, "invalid"));
            assertFalse(handler.execute(quest, action, "-1"));
            assertFalse(handler.execute(quest, action, "0", "45007001"));
            assertEquals(List.of(ownerMember), vehicle.getVehicleMembers());

            assertTrue(handler.execute(quest, action, action.getParam()));
            assertTrue(vehicle.getVehicleMembers().isEmpty());
        }
        assertEquals(2, owner.vehiclePackets.size());
    }

    private static EntityVehicle addVehicle(Scene scene, Player owner, int gadgetId) {
        var vehicle = new TestVehicle(scene, owner, gadgetId);
        scene.getEntities().put(vehicle.getId(), vehicle);
        return vehicle;
    }

    private static GameQuest questOwnedBy(Player player) {
        var data = JsonUtils.decode("{\"subId\":701220,\"mainId\":7012,\"finishCond\":[],\"failCond\":[]}", QuestData.class);
        return new GameQuest(null, data) {
            @Override
            public Player getOwner() {
                return player;
            }
        };
    }

    private static QuestData.QuestExecParam exitAction(String params) {
        return JsonUtils.decode(
                "{\"type\":\"QUEST_EXEC_EXIT_VEHICLE\",\"param\":[%s]}".formatted(params),
                QuestData.QuestExecParam.class);
    }

    private static void awaitQuestQueue() throws Exception {
        QuestManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        QuestManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
    }

    private static VehicleMember member(int uid, long guid) {
        return VehicleMember.newBuilder().setUid(uid).setAvatarGuid(guid).build();
    }

    private static final class TestVehicle extends EntityVehicle {
        TestVehicle(Scene scene, Player owner, int gadgetId) {
            super(scene, owner, gadgetId, 0, new Position(), new Position());
        }

        @Override
        protected void fillFightProps(ConfigEntityGadget config) {
            // Membership tests do not need to initialize the shared lookup from gadget resources.
        }
    }

    private static final class TestPlayer extends Player {
        final QuestEventRecorder quests = new QuestEventRecorder(this);
        final List<PacketVehicleInteractRsp> vehiclePackets = new ArrayList<>();

        TestPlayer(int uid) {
            setUid(uid);
        }

        @Override
        public QuestManager getQuestManager() {
            return quests.manager();
        }

        @Override
        public void sendPacket(BasePacket packet) {
            if (packet instanceof PacketVehicleInteractRsp response) vehiclePackets.add(response);
        }
    }
}
