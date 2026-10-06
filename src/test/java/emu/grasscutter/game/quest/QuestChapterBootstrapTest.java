package emu.grasscutter.game.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.ChapterData;
import emu.grasscutter.data.excels.quest.QuestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class QuestChapterBootstrapTest {
    private static final Gson GSON = new Gson();

    @AfterEach
    void cleanUp() {
        GameData.getMainQuestDataMap().remove(990);
        GameData.getChapterDataMap().remove(77);
        GameData.getQuestDataMap().remove(99101);
    }

    @Test
    void resolvesControllerThroughSeriesChapterAndBeginQuest() {
        var source = GSON.fromJson("{\"id\":990,\"series\":77}", MainQuestData.class);
        var chapter =
                GSON.fromJson(
                        "{\"id\":77,\"beginQuestId\":99101,\"endQuestId\":99199}",
                        ChapterData.class);
        var begin =
                GSON.fromJson(
                        """
                        {
                          "subId": 99101,
                          "mainId": 991,
                          "order": 1,
                          "acceptCond": [],
                          "finishCond": [],
                          "failCond": [],
                          "beginExec": [],
                          "finishExec": [],
                          "failExec": []
                        }
                        """,
                        QuestData.class);

        GameData.getMainQuestDataMap().put(990, source);
        GameData.getChapterDataMap().put(77, chapter);
        GameData.getQuestDataMap().put(99101, begin);

        assertEquals(991, QuestChapterBootstrap.controllerMainQuestFor(990));
    }

    @Test
    void missingResourceLinkDoesNotInventController() {
        var source = GSON.fromJson("{\"id\":990,\"series\":77}", MainQuestData.class);
        GameData.getMainQuestDataMap().put(990, source);

        assertEquals(0, QuestChapterBootstrap.controllerMainQuestFor(990));
    }
}
