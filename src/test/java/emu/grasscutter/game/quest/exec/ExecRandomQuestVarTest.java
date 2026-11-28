package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.quest.GameQuest;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.QuestSystem;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.QuestUpdateQuestVarNotifyOuterClass.QuestUpdateQuestVarNotify;
import emu.grasscutter.server.packet.send.PacketQuestUpdateQuestVarNotify;
import emu.grasscutter.utils.JsonUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class ExecRandomQuestVarTest {
    private static final int PARENT_ID = 991814;
    private MainQuestData oldParentData;
    private RecordingPlayer player;
    private RecordingMainQuest parent;
    private GameQuest quest;

    @BeforeEach
    void setUp() {
        var parentData = JsonUtils.decode("{subQuests:[]}", MainQuestData.class);
        oldParentData = GameData.getMainQuestDataMap().put(PARENT_ID, parentData);
        player = new RecordingPlayer();
        parent = new RecordingMainQuest(player);
        var data = JsonUtils.decode(
                "{subId:991815,mainId:991814,finishCond:[],failCond:[]}", QuestData.class);
        quest = new GameQuest(parent, data);
    }

    @AfterEach
    void restore() {
        var parents = GameData.getMainQuestDataMap();
        if (oldParentData == null) parents.remove(PARENT_ID);
        else parents.put(PARENT_ID, oldParentData);
    }

    @Test
    void closedActionDispatchesASingletonAtMaxIntAndSavesTheUpdatedVariable() throws Exception {
        trigger(QuestExec.QUEST_EXEC_RANDOM_CLOSED_QUEST_VAR, 1, Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, parent.getQuestVar(1));
        assertEquals(1, parent.saves);
        assertArrayEquals(parent.getQuestVars(), parent.savedVars);
        assertEquals(1, player.notifications.size());
        var update = QuestUpdateQuestVarNotify.parseFrom(player.notifications.getFirst().getData());
        assertEquals(PARENT_ID, update.getParentQuestId());
        assertEquals(Integer.MAX_VALUE, update.getQuestVar(1));
    }

    @Test
    void existingActionStillExcludesTheUpperBound() throws Exception {
        trigger(QuestExec.QUEST_EXEC_RANDOM_QUEST_VAR, 1, 1, 2);

        assertEquals(1, parent.getQuestVar(1));
        assertEquals(1, player.notifications.size());
    }

    @Test
    void rejectsMalformedParametersWithoutModifyingQuestState() {
        var handler = new ExecRandomQuestVar();
        var action = new QuestData.QuestExecParam();
        action.setType(QuestExec.QUEST_EXEC_RANDOM_CLOSED_QUEST_VAR);

        assertFalse(handler.execute(quest, action, "1", "2"));
        assertFalse(handler.execute(quest, action, "1", "not-a-number", "3"));
        assertFalse(handler.execute(quest, action, "-1", "1", "3"));
        assertFalse(handler.execute(quest, action, Integer.toString(parent.getQuestVars().length), "1", "3"));
        assertFalse(handler.execute(quest, action, "1", "3", "1"));
        assertEquals(0, parent.getQuestVar(1));
        assertEquals(0, parent.saves);
        assertTrue(player.notifications.isEmpty());
    }

    private void trigger(QuestExec type, int index, int low, int high) throws Exception {
        var action = JsonUtils.decode(
                """
                {"type":"%s","param":["%d","%d","%d"]}
                """.formatted(type.name(), index, low, high),
                QuestData.QuestExecParam.class);
        assertEquals(type, action.getType());
        new QuestSystem(null).triggerExec(quest, action, action.getParam());
        QuestManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        QuestManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
    }

    private static final class RecordingMainQuest extends GameMainQuest {
        private int saves;
        private int[] savedVars;

        private RecordingMainQuest(Player player) {
            super(player, PARENT_ID);
        }

        @Override
        public void save() {
            saves++;
            savedVars = getQuestVars().clone();
        }
    }

    private static final class RecordingPlayer extends Player {
        private final List<BasePacket> notifications = new ArrayList<>();

        @Override
        public void sendPacket(BasePacket packet) {
            if (packet instanceof PacketQuestUpdateQuestVarNotify) notifications.add(packet);
        }
    }
}
