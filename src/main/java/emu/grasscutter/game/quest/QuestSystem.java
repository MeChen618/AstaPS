package emu.grasscutter.game.quest;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.data.excels.quest.QuestData.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.conditions.BaseCondition;
import emu.grasscutter.game.quest.content.BaseContent;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.handlers.QuestExecHandler;
import emu.grasscutter.server.game.*;
import it.unimi.dsi.fastutil.ints.*;

public final class QuestSystem extends BaseGameSystem {
    /** Condition kinds whose evaluation is always written to the log (see triggerContent). */
    private static final java.util.Set<QuestContent> LOGGED_CONTENT_TYPES =
            java.util.Set.of(
                    QuestContent.QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL,
                    QuestContent.QUEST_CONTENT_QUEST_GLOBAL_VAR_NOT_EQUAL,
                    QuestContent.QUEST_CONTENT_QUEST_GLOBAL_VAR_GREATER,
                    QuestContent.QUEST_CONTENT_ITEM_NUM_EQUAL,
                    QuestContent.QUEST_CONTENT_ITEM_NUM_GREATER,
                    QuestContent.QUEST_CONTENT_PRESENT_AT_SPECIFIC_SCENE,
                    QuestContent.QUEST_CONTENT_LEAVE_SCENE_RANGE);

    private final Int2ObjectMap<BaseCondition> condHandlers;
    private final Int2ObjectMap<BaseContent> contHandlers;
    private final Int2ObjectMap<QuestExecHandler> execHandlers;

    public QuestSystem(GameServer server) {
        super(server);

        this.condHandlers = new Int2ObjectOpenHashMap<>();
        this.contHandlers = new Int2ObjectOpenHashMap<>();
        this.execHandlers = new Int2ObjectOpenHashMap<>();

        this.registerHandlers();
    }

    public void registerHandlers() {
        this.registerHandlers(this.condHandlers, BaseCondition.class);
        this.registerHandlers(this.contHandlers, BaseContent.class);
        this.registerHandlers(this.execHandlers, QuestExecHandler.class);
    }

    public <T> void registerHandlers(Int2ObjectMap<T> map, Class<T> clazz) {
        var handlerClasses = Grasscutter.reflector.getSubTypesOf(clazz);
        for (var obj : handlerClasses) {
            this.registerHandler(map, obj);
        }
    }

    public <T> void registerHandler(Int2ObjectMap<T> map, Class<? extends T> handlerClass) {
        try {
            int value;
            if (handlerClass.isAnnotationPresent(QuestValueExec.class)) {
                QuestValueExec opcode = handlerClass.getAnnotation(QuestValueExec.class);
                value = opcode.value().getValue();
            } else if (handlerClass.isAnnotationPresent(QuestValueContent.class)) {
                QuestValueContent opcode = handlerClass.getAnnotation(QuestValueContent.class);
                value = opcode.value().getValue();
            } else if (handlerClass.isAnnotationPresent(QuestValueCond.class)) {
                QuestValueCond opcode = handlerClass.getAnnotation(QuestValueCond.class);
                value = opcode.value().getValue();
            } else {
                return;
            }

            if (value <= 0) {
                return;
            }

            map.put(value, handlerClass.getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .warn("Unable to register handler {}.", handlerClass.getSimpleName(), e);
        }
    }

    // TODO make cleaner

    public boolean triggerCondition(
            Player owner,
            QuestData questData,
            QuestAcceptCondition condition,
            String paramStr,
            int... params) {
        BaseCondition handler = condHandlers.get(condition.getType().getValue());

        if (handler == null || questData == null) {
            Grasscutter.getLogger()
                    .debug("Could not trigger condition {} at {}", condition.getType().getValue(), questData);
            return false;
        }

        return handler.execute(owner, questData, condition, paramStr, params);
    }

    public boolean triggerContent(
            GameQuest quest, QuestContentCondition condition, String paramStr, int... params) {
        BaseContent handler = contHandlers.get(condition.getType().getValue());

        if (handler == null || quest.getQuestData() == null) {
            Grasscutter.getLogger()
                    .debug(
                            "Could not trigger content {} at {}",
                            condition.getType().getValue(),
                            quest.getQuestData());
            return false;
        }

        var result = handler.execute(quest, condition, paramStr, params);
        // Log-as-arbiter for the 7.x condition kinds added later. A match is logged at INFO so it
        // is visible without enabling debug (that is how a step completion is verified); a miss
        // stays at debug to keep the log quiet.
        if (LOGGED_CONTENT_TYPES.contains(condition.getType())) {
            var logger = Grasscutter.getLogger();
            if (result) {
                logger.info(
                        "[QuestContent][HIT] sub={} {} param={}",
                        quest.getSubQuestId(),
                        condition.getType().name(),
                        java.util.Arrays.toString(condition.getParam()));
            } else {
                logger.debug(
                        "[QuestContent] sub={} {} param={} -> false",
                        quest.getSubQuestId(),
                        condition.getType().name(),
                        java.util.Arrays.toString(condition.getParam()));
            }
        }
        return result;
    }

    /**
     * One-line log evidence for the condition kinds that {@link QuestContent} used to be missing.
     *
     * <p>Before those enum constants existed, the JSON type string resolved to
     * {@code QUEST_CONTENT_NONE} and the condition was dropped while loading, so the count was 0.
     * A non-zero count after load proves the data is now kept (and that a handler can ever run).
     */
    public static void logConditionCoverage() {
        var interesting =
                java.util.List.of(
                        QuestContent.QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL,
                        QuestContent.QUEST_CONTENT_QUEST_GLOBAL_VAR_NOT_EQUAL,
                        QuestContent.QUEST_CONTENT_QUEST_GLOBAL_VAR_GREATER,
                        QuestContent.QUEST_CONTENT_ITEM_NUM_EQUAL,
                        QuestContent.QUEST_CONTENT_ITEM_NUM_GREATER,
                        QuestContent.QUEST_CONTENT_PRESENT_AT_SPECIFIC_SCENE,
                        QuestContent.QUEST_CONTENT_LEAVE_SCENE_RANGE);
        var counts = new java.util.EnumMap<QuestContent, Integer>(QuestContent.class);
        for (var quest : GameData.getQuestDataMap().values()) {
            countContents(counts, quest.getFinishCond());
            countContents(counts, quest.getFailCond());
        }
        var sb = new StringBuilder();
        for (var type : interesting) {
            sb.append(' ').append(type.name()).append('=').append(counts.getOrDefault(type, 0));
        }
        Grasscutter.getLogger().info("[QuestContent] loaded condition coverage:{}", sb);
    }

    private static void countContents(
            java.util.EnumMap<QuestContent, Integer> counts,
            java.util.List<QuestData.QuestContentCondition> list) {
        if (list == null) return;
        for (var cond : list) {
            if (cond == null) continue;
            var type = cond.getType();
            if (type == null) continue;
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }
    }

    public void triggerExec(GameQuest quest, QuestExecParam execParam, String... params) {
        QuestExecHandler handler = execHandlers.get(execParam.getType().getValue());

        if (handler == null || quest.getQuestData() == null) {
            Grasscutter.getLogger()
                    .debug(
                            "Could not trigger exec {} at {}",
                            execParam.getType().getValue(),
                            quest.getQuestData());
            return;
        }

        QuestManager.eventExecutor.submit(
                () -> {
                    if (!handler.execute(quest, execParam, params)) {
                        Grasscutter.getLogger()
                                .debug(
                                        "Execute trigger failed for {} at {}.",
                                        execParam.getType().name(),
                                        quest.getQuestData());
                    }
                });
    }
}
