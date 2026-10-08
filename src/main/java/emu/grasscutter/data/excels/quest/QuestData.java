package emu.grasscutter.data.excels.quest;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.quest.enums.*;
import java.util.*;
import javax.annotation.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@ResourceType(name = "QuestExcelConfigData.json")
@Getter
@ToString
public class QuestData extends GameResource {
    @Getter private int subId;
    @Getter private int mainId;
    @Getter private int order;
    @Getter private long descTextMapHash;

    @Getter private boolean finishParent;
    @Getter private boolean isRewind;

    @Getter private LogicType acceptCondComb;
    @Getter private LogicType finishCondComb;
    @Getter private LogicType failCondComb;

    @Getter private List<QuestAcceptCondition> acceptCond;
    @Getter private List<QuestContentCondition> finishCond;
    @Getter private List<QuestContentCondition> failCond;
    @Getter private List<QuestExecParam> beginExec;
    @Getter private List<QuestExecParam> finishExec;
    @Getter private List<QuestExecParam> failExec;
    @Getter private Guide guide;

    @Getter private List<Integer> trialAvatarList;
    @Getter private List<ItemParamData> gainItems;

    public static String questConditionKey(
            @Nonnull Enum<?> type, int firstParam, @Nullable String paramsStr) {
        return type.name() + firstParam + (paramsStr != null ? paramsStr : "");
    }

    // ResourceLoader not happy if you remove getId() ~~
    public int getId() {
        return subId;
    }

    public void onLoad() {
        this.acceptCond = acceptCond.stream().filter(p -> p.getType() != null).toList();
        this.finishCond = finishCond.stream().filter(p -> p.getType() != null).toList();
        this.failCond = failCond.stream().filter(p -> p.getType() != null).toList();

        this.beginExec = beginExec.stream().filter(p -> p.type != null).toList();
        this.finishExec = finishExec.stream().filter(p -> p.type != null).toList();
        this.failExec = failExec.stream().filter(p -> p.type != null).toList();

        if (this.acceptCondComb == null) this.acceptCondComb = LogicType.LOGIC_NONE;

        if (this.finishCondComb == null) this.finishCondComb = LogicType.LOGIC_NONE;

        if (this.failCondComb == null) this.failCondComb = LogicType.LOGIC_NONE;

        if (this.gainItems == null) this.gainItems = Collections.emptyList();

        this.addToCache();
    }

    /**
     * Keep QuestExcel actions authoritative when present, but use the per-subquest BinOutput
     * actions when the Excel export has no usable entries. Quest 35302 requires its beginExec
     * group-suite refresh to spawn the combat-training slime.
     */
    public void applyFrom(MainQuestData.SubQuestData additionalData) {
        this.isRewind = additionalData.isRewind();
        this.finishParent = additionalData.isFinishParent();
        this.beginExec = effectiveExecList(this.beginExec, additionalData.getBeginExec());
        this.finishExec = effectiveExecList(this.finishExec, additionalData.getFinishExec());
        this.failExec = effectiveExecList(this.failExec, additionalData.getFailExec());

        if (this.subId == 35106 || this.subId == 35205
                || this.subId == 35301 || this.subId == 35302) {
            Grasscutter.getLogger()
                    .info(
                            "[quest-resource] main={} sub={} beginExec={} finishExec={} failExec={}",
                            this.mainId, this.subId,
                            this.beginExec.size(), this.finishExec.size(), this.failExec.size());
        }
    }


    /**
     * Fill incomplete resource packs from the bundled native 7.1 intro baseline.
     * Only absent actions are restored; an existing QuestExcel/BinOutput action wins.
     */
    public int fillMissingExecutions(QuestExecFallbackRow baseline) {
        if (baseline == null || baseline.getSubId() != this.subId) return 0;
        int previous =
                this.beginExec.size() + this.finishExec.size() + this.failExec.size();
        this.beginExec = effectiveExecList(this.beginExec, baseline.getBeginExec());
        this.finishExec = effectiveExecList(this.finishExec, baseline.getFinishExec());
        this.failExec = effectiveExecList(this.failExec, baseline.getFailExec());
        return this.beginExec.size() + this.finishExec.size() + this.failExec.size() - previous;
    }

    /** Actions extracted from the complete 7.1 native BinOutput/Quest files. */
    @Data
    public static class QuestExecFallbackRow {
        private int subId;
        private List<QuestExecParam> beginExec;
        private List<QuestExecParam> finishExec;
        private List<QuestExecParam> failExec;
    }

    static List<QuestExecParam> effectiveExecList(
            List<QuestExecParam> excel, List<QuestExecParam> bin) {
        var validExcel = validExecs(excel);
        return validExcel.isEmpty() ? validExecs(bin) : validExcel;
    }

    private static List<QuestExecParam> validExecs(List<QuestExecParam> execs) {
        if (execs == null || execs.isEmpty()) return Collections.emptyList();
        return execs.stream()
                .filter(Objects::nonNull)
                .filter(exec -> exec.getType() != null)
                .toList();
    }

    private void addToCache() {
        if (this.acceptCond == null) {
            Grasscutter.getLogger().warn("missing AcceptConditions for quest {}", getSubId());
            return;
        }

        var cacheMap = GameData.getBeginCondQuestMap();
        if (getAcceptCond().isEmpty()) {
            var list =
                    cacheMap.computeIfAbsent(
                            QuestData.questConditionKey(QuestCond.QUEST_COND_NONE, 0, null),
                            e -> new ArrayList<>());
            list.add(this);
        } else {
            this.getAcceptCond()
                    .forEach(
                            questCondition -> {
                                if (questCondition.getType() == null) {
                                    Grasscutter.getLogger().warn("null accept type for quest {}", getSubId());
                                    return;
                                }

                                var key = questCondition.asKey();
                                var list = cacheMap.computeIfAbsent(key, e -> new ArrayList<>());
                                list.add(this);
                            });
        }
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class QuestExecParam {
        @SerializedName(
                value = "_type",
                alternate = {"type"})
        QuestExec type;

        @SerializedName(
                value = "_param",
                alternate = {"param"})
        String[] param;

        @SerializedName(
                value = "_count",
                alternate = {"count"})
        String count;
    }

    public static class QuestAcceptCondition extends QuestCondition<QuestCond> {}

    public static class QuestContentCondition extends QuestCondition<QuestContent> {}

    @Data
    public static class QuestCondition<TYPE extends Enum<?> & QuestTrigger> {
        @SerializedName(
                value = "_type",
                alternate = {"type"})
        private TYPE type;

        @SerializedName(
                value = "_param",
                alternate = {"param"})
        private int[] param;

        @SerializedName(
                value = "_param_str",
                alternate = {"param_str"})
        private String paramStr = "";

        @SerializedName(
                value = "_count",
                alternate = {"count"})
        private int count;

        public String asKey() {
            return questConditionKey(getType(), getParam()[0], getParamStr());
        }
    }

    @Data
    public static class Guide {
        private String type;
        private List<String> param;
        private int guideScene;
    }
}
