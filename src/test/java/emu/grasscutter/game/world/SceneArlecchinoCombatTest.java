package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.ability.ArlecchinoBurstBoL;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ExtendWith(ServerResourceFixture.class)
public final class SceneArlecchinoCombatTest {
    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(0x01005000);
    private RecordingAvatar attacker;

    @AfterEach
    void clearPendingBurst() {
        if (attacker != null) ArlecchinoBurstBoL.clearEntityState(attacker.getId());
    }

    @Test
    void redDeathNormalAttackConsumesSevenAndAHalfPercentUnderAvatarLock() throws Exception {
        var scene = scene(10000096, 5000f);

        scene.handleAttack(hit(scene, "ATK01_Plus"));

        assertEquals(4625f, debt(), 0.001f);
        assertEquals(123f, scene.target.damage, 0.001f);
        assertTrue(attacker.lockedDebtReads > 0);
        assertTrue(attacker.lockedDebtWrites > 0);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ExtraAttack_Plus",
                "PlungeAttack",
                "Arlecchino_ElementalArt_Attack",
                "Arlecchino_ElementalBurst_Attack"
            })
    void otherAttacksPreserveBond(String animation) throws Exception {
        var scene = scene(10000096, 5000f);

        scene.handleAttack(hit(scene, animation));

        assertEquals(5000f, debt(), 0.001f);
        assertEquals(123f, scene.target.damage, 0.001f);
    }

    @Test
    void normalAttackBelowRedDeathThresholdPreservesBond() throws Exception {
        var scene = scene(10000096, 2999f);

        scene.handleAttack(hit(scene, "ATK01_Plus"));

        assertEquals(2999f, debt(), 0.001f);
    }

    @Test
    void clorindeNormalAttackDoesNotConsumeBond() throws Exception {
        var scene = scene(10000098, 5000f);

        scene.handleAttack(hit(scene, "ATK01_Plus"));

        assertEquals(5000f, debt(), 0.001f);
        assertEquals(123f, scene.target.damage, 0.001f);
        assertEquals(0, attacker.lockedDebtWrites);
    }

    @Test
    void pendingBurstProtectsBondFromNormalAttack() throws Exception {
        var scene = scene(10000096, 5000f);
        synchronized (attacker) {
            ArlecchinoBurstBoL.onBurstCast(attacker);
        }

        scene.handleAttack(hit(scene, "ATK01_Plus"));

        assertEquals(5000f, debt(), 0.001f);
        assertTrue(ArlecchinoBurstBoL.isPending(attacker.getId()));
    }

    private RecordingScene scene(int avatarId, float bond) throws Exception {
        var scene = allocate(RecordingScene.class);
        scene.entities = new ConcurrentHashMap<>();
        set(scene, Scene.class, "arlecchinoNaBoLReduceMs", new ConcurrentHashMap<>());
        var avatar = new Avatar();
        set(avatar, Avatar.class, "avatarId", avatarId);
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_MAX_HP.getId(), 10000f);
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_CUR_HP_DEBTS.getId(), bond);
        attacker = allocate(RecordingAvatar.class);
        set(attacker, EntityAvatar.class, "avatar", avatar);
        set(attacker, GameEntity.class, "scene", scene);
        set(
                attacker,
                GameEntity.class,
                "globalAbilityValues",
                new ConcurrentHashMap<String, Float>());
        attacker.setId(ENTITY_IDS.incrementAndGet());
        scene.target = new DamageTarget(scene);
        scene.target.setId(ENTITY_IDS.incrementAndGet());
        scene.entities.put(attacker.getId(), attacker);
        scene.entities.put(scene.target.getId(), scene.target);
        return scene;
    }

    private AttackResult hit(RecordingScene scene, String animation) {
        return AttackResult.newBuilder()
                .setAttackerId(attacker.getId())
                .setDefenseId(scene.target.getId())
                .setElementType(1)
                .setDamage(123f)
                .setAnimEventId(animation)
                .build();
    }

    private float debt() {
        return attacker.getAvatar()
                .getFightProperties()
                .get(FightProperty.FIGHT_PROP_CUR_HP_DEBTS.getId());
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void set(Object instance, Class<?> owner, String name, Object value)
            throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static final class RecordingScene extends Scene {
        private Map<Integer, GameEntity> entities;
        private DamageTarget target;

        private RecordingScene() {
            super(null, null);
        }

        @Override
        public GameEntity getEntityById(int id) {
            return entities.get(id);
        }

        @Override
        public void broadcastPacket(BasePacket packet) {}
    }

    private static final class RecordingAvatar extends EntityAvatar {
        private int lockedDebtReads;
        private int lockedDebtWrites;

        private RecordingAvatar() {
            super((Avatar) null);
        }

        @Override
        public float getFightProperty(FightProperty property) {
            if (property == FightProperty.FIGHT_PROP_CUR_HP_DEBTS) {
                assertTrue(Thread.holdsLock(this), "Bond consumption must hold the avatar monitor");
                lockedDebtReads++;
            }
            return super.getFightProperty(property);
        }

        @Override
        public void setFightProperty(FightProperty property, float value) {
            if (property == FightProperty.FIGHT_PROP_CUR_HP_DEBTS) {
                assertTrue(Thread.holdsLock(this), "Bond updates must hold the avatar monitor");
                lockedDebtWrites++;
            }
            super.setFightProperty(property, value);
        }
    }

    private static final class DamageTarget extends GameEntity {
        private final Int2FloatMap properties = new Int2FloatOpenHashMap();
        private float damage;

        private DamageTarget(Scene scene) {
            super(scene);
        }

        @Override
        public void damage(float amount, int killerId, ElementType attackType) {
            damage += amount;
        }

        @Override
        public void initAbilities() {}

        @Override
        public int getEntityTypeId() {
            return 0;
        }

        @Override
        public Int2FloatMap getFightProperties() {
            return properties;
        }

        @Override
        public Position getPosition() {
            return new Position();
        }

        @Override
        public Position getRotation() {
            return new Position();
        }

        @Override
        public SceneEntityInfo toProto() {
            return SceneEntityInfo.getDefaultInstance();
        }
    }
}
