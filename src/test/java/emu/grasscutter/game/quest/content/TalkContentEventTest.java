package emu.grasscutter.game.quest.content;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.excels.quest.QuestData;
import org.junit.jupiter.api.Test;

final class TalkContentEventTest {
    @Test
    void completeTalkMatchesOnlyTheCurrentEvent() {
        var condition = new QuestData.QuestContentCondition();
        condition.setParam(new int[] {31141});

        var handler = new ContentCompleteTalk();
        assertTrue(handler.execute(null, condition, "", 31141));
        assertFalse(handler.execute(null, condition, "", 31142));
        assertFalse(handler.execute(null, condition, ""));
    }

    @Test
    void completeAnyTalkMatchesOnlyListedCurrentEvents() {
        var condition = new QuestData.QuestContentCondition();
        condition.setParamStr("31141, 35216,40000");

        var handler = new ContentCompleteAnyTalk();
        assertTrue(handler.execute(null, condition, "", 35216));
        assertFalse(handler.execute(null, condition, "", 99999));
        assertFalse(handler.execute(null, condition, ""));
    }
}
