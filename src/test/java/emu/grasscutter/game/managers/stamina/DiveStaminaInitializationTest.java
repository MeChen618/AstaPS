package emu.grasscutter.game.managers.stamina;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import static emu.grasscutter.game.props.PlayerProperty.*;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.PlayerDataNotifyOuterClass.PlayerDataNotify;
import emu.grasscutter.server.packet.send.PacketPlayerDataNotify;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class DiveStaminaInitializationTest {
    private boolean oldQuesting;

    @BeforeEach
    void setup() {
        oldQuesting = GAME_OPTIONS.questing.enabled;
    }

    @AfterEach
    void restore() {
        GAME_OPTIONS.questing.enabled = oldQuesting;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void newPlayersGetPositiveDiveStaminaRegardlessOfQuesting(boolean questing) throws Exception {
        GAME_OPTIONS.questing.enabled = questing;
        var player = new RecordingPlayer();
        var initialize = Player.class.getDeclaredMethod("applyProperties");
        initialize.setAccessible(true);
        initialize.invoke(player);
        assertEquals(12000, player.getProperty(PROP_DIVE_MAX_STAMINA));
        assertEquals(12000, player.getProperty(PROP_DIVE_CUR_STAMINA));
        assertEquals(questing ? 0 : 1, player.getProperty(PROP_PLAYER_CAN_DIVE));
    }

    @Test
    void legacyZeroStaminaIsRepairedInTheLoginSnapshotWithoutUnlockingDiving() throws Exception {
        var player = new RecordingPlayer();
        player.getProperties().put(PROP_DIVE_MAX_STAMINA.getId(), 0);
        player.getProperties().put(PROP_DIVE_CUR_STAMINA.getId(), 0);
        player.getStaminaManager().onPlayerLogin();
        var snapshot = PlayerDataNotify.parseFrom(new PacketPlayerDataNotify(player).getData());
        assertEquals(12000, snapshot.getPropMapOrThrow(10049).getVal());
        assertEquals(12000, snapshot.getPropMapOrThrow(10050).getVal());
        assertEquals(0, player.getProperty(PROP_PLAYER_CAN_DIVE));
    }

    @Test
    void validSavedCapacityIsPreservedAndCurrentStaminaCannotExceedIt() {
        var player = new RecordingPlayer();
        player.setProperty(PROP_DIVE_MAX_STAMINA, 8000, false);
        player.setProperty(PROP_DIVE_CUR_STAMINA, 0, false);
        player.getStaminaManager().onPlayerLogin();
        assertEquals(8000, player.getProperty(PROP_DIVE_MAX_STAMINA));
        assertEquals(8000, player.getProperty(PROP_DIVE_CUR_STAMINA));
        assertFalse(player.setProperty(PROP_DIVE_CUR_STAMINA, 8001, false));
        assertEquals(8000, player.getProperty(PROP_DIVE_CUR_STAMINA));
        assertFalse(player.setProperty(PROP_DIVE_CUR_STAMINA, -1, false));
    }

    private static final class RecordingPlayer extends Player {
        @Override
        public String getNickname() {
            return "Diver";
        }

        @Override
        public void sendPacket(BasePacket packet) {}
    }
}
