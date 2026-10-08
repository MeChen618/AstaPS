package emu.grasscutter.game.ability.actions;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.ArlecchinoBoLSync;
import emu.grasscutter.game.ability.ArlecchinoBoLUtil;
import emu.grasscutter.game.ability.ArlecchinoBurstBoL;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.ChangHpReasonOuterClass.ChangHpReason;
import emu.grasscutter.net.proto.CombatInvocationsNotifyOuterClass.CombatInvocationsNotify;
import emu.grasscutter.net.proto.EntityFightPropChangeReasonNotifyOuterClass.EntityFightPropChangeReasonNotify;
import emu.grasscutter.net.proto.EntityFightPropUpdateNotifyOuterClass.EntityFightPropUpdateNotify;
import emu.grasscutter.net.proto.EvtBeingHealedNotifyOuterClass.EvtBeingHealedNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEvtBeingHealedNotify;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import sun.misc.Unsafe;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ExtendWith(ServerResourceFixture.class)
public final class ActionHealHPArlecchinoTest {
    private static final String BURST_HEAL_TAG = "Arlecchino_ElementalBurst_Heal";
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(96_000);

    @Test
    void pendingBurstKeepsHpAndBondUntilTickClearsThenHeals() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            fixture.packets.clear();

            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));

            fixture.assertState(2_000f, 3_000f);
            assertTrue(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            assertTrue(fixture.packets.isEmpty(), "Deferred healing must not notify a heal early");

            expirePendingCast(fixture.entity);
            ArlecchinoBurstBoL.onTick(fixture.player);

            fixture.assertState(3_000f, 0f);
            assertFalse(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            fixture.assertHealNotification(1_000f, 1_000f);
            fixture.assertHpUpdate(3_000f, 1_000f);
            assertTrue(
                    fixture.firstPropertyUpdate(FightProperty.FIGHT_PROP_CUR_HP_DEBTS)
                            < fixture.firstPropertyUpdate(FightProperty.FIGHT_PROP_CUR_HP));

            fixture.packets.clear();
            ArlecchinoBurstBoL.onTick(fixture.player);
            fixture.assertState(3_000f, 0f);
            assertTrue(fixture.packets.isEmpty(), "The deferred heal must settle only once");
        }
    }

    @Test
    void deferredBurstHealDoesNotRepayBondAddedAfterCast() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, 4_000f);
            fixture.packets.clear();

            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));
            fixture.assertState(2_000f, 4_000f);

            expirePendingCast(fixture.entity);
            ArlecchinoBurstBoL.onTick(fixture.player);

            fixture.assertState(3_000f, 1_000f);
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            fixture.assertHealNotification(1_000f, 1_000f);
        }
    }

    @Test
    void tickWaitingOnAvatarFlushesTheHealRegisteredBeforeSettlementExactlyOnce() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            fixture.packets.clear();
            var started = new CountDownLatch(1);
            var completion = new CompletableFuture<Void>();
            var tick =
                    new Thread(
                            () -> {
                                started.countDown();
                                try {
                                    ArlecchinoBurstBoL.onTick(fixture.player);
                                    completion.complete(null);
                                } catch (Throwable failure) {
                                    completion.completeExceptionally(failure);
                                }
                            },
                            "arlecchino-burst-heal-tick-test");
            tick.setDaemon(true);
            try {
                synchronized (fixture.entity) {
                    tick.start();
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    awaitBlockedOn(tick, fixture.entity);

                    assertTrue(
                            execute(
                                    fixture,
                                    "Avatar_Arlecchino_Common",
                                    heal(BURST_HEAL_TAG, 1_000f)));
                    fixture.assertState(2_000f, 3_000f);
                    assertTrue(fixture.packets.isEmpty());
                    expirePendingCast(fixture.entity);
                }
                completion.get(5, TimeUnit.SECONDS);

                fixture.assertState(3_000f, 0f);
                assertFalse(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
                assertEquals(0, fixture.entity.ordinaryHealCalls);
                fixture.assertHealNotification(1_000f, 1_000f);
                assertFalse(hasDeferredHeal(fixture.entity.getId()));
                fixture.packets.clear();
                ArlecchinoBurstBoL.onTick(fixture.player);
                assertTrue(fixture.packets.isEmpty());
            } finally {
                tick.join(5_000L);
                assertFalse(tick.isAlive(), "The settlement thread must terminate");
            }
        }
    }

    @Test
    void burstHealWithoutPendingAddsHpAndLeavesBondUnchanged() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            fixture.entity.setConvertToHpDebt(true);

            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));

            fixture.assertState(3_000f, 3_000f);
            assertFalse(ArlecchinoBurstBoL.isPending(fixture.entity.getId()));
            assertTrue(fixture.entity.isConvertToHpDebtRaw());
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            fixture.assertHealNotification(1_000f, 1_000f);
            fixture.assertHpUpdate(3_000f, 1_000f);
        }
    }

    @Test
    void ordinaryHealStillRepaysBondBeforeRestoringHp() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(null, 1_000f)));

            fixture.assertState(2_000f, 2_000f);
            assertEquals(1, fixture.entity.ordinaryHealCalls);
            assertTrue(
                    fixture.packets.stream()
                            .noneMatch(PacketEvtBeingHealedNotify.class::isInstance));
            assertTrue(fixture.firstPropertyUpdate(FightProperty.FIGHT_PROP_CUR_HP_DEBTS) >= 0);
            assertEquals(-1, fixture.firstPropertyUpdate(FightProperty.FIGHT_PROP_CUR_HP));
        }
    }

    @Test
    void missingHealTagUsesArlecchinoElementalBurstAbilityName() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            assertTrue(
                    execute(
                            fixture,
                            "Avatar_Arlecchino_ElementalBurst_HealDelay",
                            heal(null, 1_000f)));

            fixture.assertState(3_000f, 3_000f);
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            fixture.assertHealNotification(1_000f, 1_000f);
        }
    }

    @Test
    void obfuscatedHealTagIsParsedAndUsesBurstHealing() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            var action =
                    JsonUtils.decode(
                            "{\"$type\":\"HealHP\",\"FFNEJGGNAFF\":\"Arlecchino_ElementalBurst_Heal\","
                                + "\"amount\":1000,\"ignoreAbilityProperty\":true}",
                            AbilityModifierAction.class);
            assertEquals(BURST_HEAL_TAG, action.healTag);

            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", action));

            fixture.assertState(3_000f, 3_000f);
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            fixture.assertHealNotification(1_000f, 1_000f);
        }
    }

    @Test
    void teammateElementalBurstHealUsesOrdinaryBondRepayment() throws Exception {
        try (var target = fixture(10000096, 2_000f, 3_000f);
                var caster = fixture(10000032, 5_000f, 0f)) {
            var ability = ability(caster.entity, "Avatar_Bennett_ElementalBurst");

            assertTrue(
                    new ActionHealHP()
                            .execute(
                                    ability,
                                    heal("Bennett_ElementalBurst_Heal", 1_000f),
                                    ByteString.EMPTY,
                                    target.entity));

            target.assertState(2_000f, 2_000f);
            assertEquals(1, target.entity.ordinaryHealCalls);
            assertFalse(ArlecchinoBurstBoL.isPending(target.entity.getId()));
        }
    }

    @Test
    void arlecchinoBurstTagDoesNotGiveOtherAvatarsSpecialHealing() throws Exception {
        try (var target = fixture(10000032, 2_000f, 3_000f);
                var caster = fixture(10000096, 5_000f, 0f)) {
            var ability = ability(caster.entity, "Avatar_Arlecchino_ElementalBurst_HealDelay");

            assertTrue(
                    new ActionHealHP()
                            .execute(
                                    ability,
                                    heal(BURST_HEAL_TAG, 1_000f),
                                    ByteString.EMPTY,
                                    target.entity));

            target.assertState(2_000f, 2_000f);
            assertEquals(1, target.entity.ordinaryHealCalls);
        }
    }

    @Test
    void burstHealDoesNotReviveAnAvatarWithZeroHp() throws Exception {
        try (var fixture = fixture(10000096, 0f, 3_000f)) {
            set(fixture.entity, GameEntity.class, "isDead", true);

            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));

            fixture.assertState(0f, 3_000f);
            assertTrue(fixture.entity.isDead());
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            assertTrue(fixture.packets.isEmpty());
        }
    }

    @Test
    void cappedBurstHealReportsRequestedAndActualAmountsInTheirCorrectFields() throws Exception {
        try (var fixture = fixture(10000096, 9_500f, 3_000f)) {
            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", heal(BURST_HEAL_TAG, 1_000f)));

            fixture.assertState(10_000f, 3_000f);
            fixture.assertHealNotification(1_000f, 500f);
            fixture.assertHpUpdate(10_000f, 500f);
        }
    }

    @Test
    void mutedDeferredBurstHealStillUpdatesHpWithoutBeingHealedNotification() throws Exception {
        try (var fixture = fixture(10000096, 2_000f, 3_000f)) {
            ArlecchinoBurstBoL.onBurstCast(fixture.entity);
            fixture.packets.clear();
            var action = heal(BURST_HEAL_TAG, 1_000f);
            action.muteHealEffect = true;

            assertTrue(execute(fixture, "Avatar_Arlecchino_Common", action));
            fixture.assertState(2_000f, 3_000f);

            expirePendingCast(fixture.entity);
            ArlecchinoBurstBoL.onTick(fixture.player);

            fixture.assertState(3_000f, 0f);
            assertEquals(0, fixture.entity.ordinaryHealCalls);
            assertTrue(fixture.firstPropertyUpdate(FightProperty.FIGHT_PROP_CUR_HP) >= 0);
            assertTrue(
                    fixture.packets.stream()
                            .noneMatch(PacketEvtBeingHealedNotify.class::isInstance));
        }
    }

    private static boolean execute(
            Fixture fixture, String abilityName, AbilityModifierAction action) throws Exception {
        return new ActionHealHP()
                .execute(
                        ability(fixture.entity, abilityName),
                        action,
                        ByteString.EMPTY,
                        fixture.entity);
    }

    private static AbilityModifierAction heal(String tag, float amount) {
        var action = new AbilityModifierAction();
        action.healTag = tag;
        action.amount = new DynamicFloat(amount);
        action.ignoreAbilityProperty = true;
        return action;
    }

    private static Ability ability(GameEntity owner, String name) throws Exception {
        var ability = allocate(Ability.class);
        var data = new AbilityData();
        data.abilityName = name;
        set(ability, Ability.class, "owner", owner);
        set(ability, Ability.class, "data", data);
        set(ability, Ability.class, "abilitySpecials", new Object2FloatOpenHashMap<String>());
        return ability;
    }

    private static Fixture fixture(int avatarId, float hp, float debt) throws Exception {
        var fixture = new Fixture();
        fixture.entity = allocate(RecordingEntityAvatar.class);
        fixture.player = allocate(RecordingPlayer.class);
        var scene = allocate(RecordingScene.class);
        var world = allocate(RecordingWorld.class);
        var avatar = allocate(Avatar.class);
        fixture.entity.setId(ENTITY_IDS.incrementAndGet());
        scene.packets = world.packets = fixture.player.packets = fixture.packets;
        scene.world = world;
        set(avatar, Avatar.class, "avatarId", avatarId);
        set(avatar, Avatar.class, "guid", (long) fixture.entity.getId());
        set(avatar, Avatar.class, "fightProperties", new Int2FloatOpenHashMap());
        avatar.setOwner(fixture.player);
        set(fixture.entity, EntityAvatar.class, "avatar", avatar);
        set(fixture.entity, GameEntity.class, "scene", scene);
        set(
                fixture.entity,
                GameEntity.class,
                "globalAbilityValues",
                new ConcurrentHashMap<String, Float>());
        set(fixture.entity, GameEntity.class, "instancedModifiers", new ConcurrentHashMap<>());
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, hp);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 10_000f);
        fixture.entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, debt);
        return fixture;
    }

    // Move the real pending cast past its miss timeout without sleeping or bypassing settlement.
    private static void expirePendingCast(EntityAvatar entity) throws Exception {
        var pendingField = ArlecchinoBurstBoL.class.getDeclaredField("PENDING");
        pendingField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var pending = (Map<Integer, Object>) pendingField.get(null);
        var original = pending.get(entity.getId());
        assertNotNull(original);
        var type = original.getClass();
        var snapshot = type.getDeclaredMethod("castSnapshot");
        snapshot.setAccessible(true);
        var constructor =
                type.getDeclaredConstructor(
                        EntityAvatar.class,
                        long.class,
                        long.class,
                        float.class,
                        float.class,
                        long.class);
        constructor.setAccessible(true);
        pending.put(
                entity.getId(),
                constructor.newInstance(
                        entity,
                        System.currentTimeMillis() - 10_000L,
                        0L,
                        -1f,
                        snapshot.invoke(original),
                        0L));
    }

    private static void awaitBlockedOn(Thread thread, Object monitor) {
        var threads = ManagementFactory.getThreadMXBean();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (!thread.isAlive()) fail("The tick ended before waiting on the avatar monitor");
            var info = threads.getThreadInfo(thread.threadId());
            if (info != null
                    && info.getThreadState() == Thread.State.BLOCKED
                    && info.getLockInfo() != null
                    && info.getLockInfo().getIdentityHashCode()
                            == System.identityHashCode(monitor)) {
                return;
            }
            Thread.yield();
        }
        fail("The tick must wait on the avatar monitor before healing is registered");
    }

    private static boolean hasDeferredHeal(int entityId) throws Exception {
        var field = ArlecchinoBurstBoL.class.getDeclaredField("DEFERRED_HEAL");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).containsKey(entityId);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void set(Object instance, Class<?> owner, String name, Object value)
            throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static final class Fixture implements AutoCloseable {
        private final ArrayList<BasePacket> packets = new ArrayList<>();
        private RecordingEntityAvatar entity;
        private RecordingPlayer player;

        private void assertState(float hp, float debt) {
            assertEquals(hp, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
            assertEquals(debt, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        }

        private void assertHealNotification(float requested, float actual) throws Exception {
            var healed =
                    packets.stream().filter(PacketEvtBeingHealedNotify.class::isInstance).toList();
            assertEquals(1, healed.size());
            var combat = CombatInvocationsNotify.parseFrom(healed.get(0).getData());
            assertEquals(1, combat.getInvokeListCount());
            var notification =
                    EvtBeingHealedNotify.parseFrom(combat.getInvokeList(0).getCombatData());
            assertEquals(entity.getId(), notification.getTargetId());
            assertEquals(requested, notification.getHealAmount());
            assertEquals(actual, notification.getRealHealAmount());
        }

        private void assertHpUpdate(float hp, float change) throws Exception {
            int index = firstPropertyUpdate(FightProperty.FIGHT_PROP_CUR_HP);
            assertTrue(index >= 0);
            var update = EntityFightPropUpdateNotify.parseFrom(packets.get(index).getData());
            assertEquals(
                    hp, update.getFightPropMapOrThrow(FightProperty.FIGHT_PROP_CUR_HP.getId()));
            var reasons = new ArrayList<EntityFightPropChangeReasonNotify>();
            for (var packet : packets) {
                if (packet instanceof PacketEntityFightPropChangeReasonNotify) {
                    var reason = EntityFightPropChangeReasonNotify.parseFrom(packet.getData());
                    if (reason.getPropType() == FightProperty.FIGHT_PROP_CUR_HP.getId())
                        reasons.add(reason);
                }
            }
            assertEquals(1, reasons.size());
            assertEquals(change, reasons.get(0).getPropDelta());
            assertEquals(
                    ChangHpReason.ChangHpReason_CHANGE_HP_ADD_ABILITY,
                    reasons.get(0).getChangeHpReason());
        }

        private int firstPropertyUpdate(FightProperty property) throws Exception {
            for (int index = 0; index < packets.size(); index++) {
                var packet = packets.get(index);
                if (packet instanceof PacketEntityFightPropUpdateNotify) {
                    var update = EntityFightPropUpdateNotify.parseFrom(packet.getData());
                    if (update.containsFightPropMap(property.getId())) return index;
                }
            }
            return -1;
        }

        @Override
        public void close() throws Exception {
            ArlecchinoBurstBoL.clearEntityState(entity.getId());
            ArlecchinoBoLUtil.clearEntityState(entity.getId());
            var field = ArlecchinoBoLSync.class.getDeclaredField("BLOCK_ADD_UNTIL");
            field.setAccessible(true);
            ((Map<?, ?>) field.get(null)).remove(entity.getId());
        }
    }

    private static final class RecordingEntityAvatar extends EntityAvatar {
        private int ordinaryHealCalls;

        private RecordingEntityAvatar() {
            super((Avatar) null);
        }

        @Override
        public float heal(float amount, boolean mute) {
            ordinaryHealCalls++;
            return super.heal(amount, mute);
        }
    }

    private static final class RecordingScene extends Scene {
        private ArrayList<BasePacket> packets;
        private World world;

        private RecordingScene() {
            super(null, null);
        }

        @Override
        public World getWorld() {
            return world;
        }

        @Override
        public void broadcastPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    private static final class RecordingWorld extends World {
        private ArrayList<BasePacket> packets;

        private RecordingWorld() {
            super((Player) null);
        }

        @Override
        public void broadcastPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    private static final class RecordingPlayer extends Player {
        private ArrayList<BasePacket> packets;

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }
    }
}
