package emu.grasscutter.game.quest;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/** Observes events after they pass through the real quest queue. */
public final class QuestEventRecorder {
    private record Event(QuestContent type, int[] params) {}

    private final List<Event> events = new CopyOnWriteArrayList<>();
    private final QuestManager manager;

    public QuestEventRecorder(Player player) {
        manager = new QuestManager(player);
        manager.getMainQuests()
                .put(
                        991333,
                        new GameMainQuest() {
                            @Override
                            public ParentQuestState getState() {
                                return ParentQuestState.PARENT_QUEST_STATE_NONE;
                            }

                            @Override
                            public void tryFailSubQuests(
                                    QuestContent type, String spec, int... params) {
                                events.add(new Event(type, params.clone()));
                            }

                            @Override
                            public void tryFinishSubQuests(
                                    QuestContent type, String spec, int... params) {}
                        });
    }

    public QuestManager manager() {
        return manager;
    }

    private void await() {
        try {
            QuestManager.eventExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public List<QuestContent> types() {
        await();
        return events.stream().map(Event::type).toList();
    }

    public List<Integer> firstParams() {
        await();
        return events.stream().map(e -> e.params()[0]).toList();
    }
}
