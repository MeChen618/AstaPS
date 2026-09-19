package emu.grasscutter.game.dps;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.challenge.trigger.ChallengeTrigger;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.SceneMonster;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 一次 DPS 测试。挂在场景的 challenge 槽上，靠计时器或击杀靶子结束，结束时统一出结算。 */
public class DPSChallenge extends WorldChallenge {

    /** 靶子怪所属的伪 group。不对应任何 lua 脚本，仅用于挑战的击杀判定与结束清场。 */
    public static final int GROUP_ID = 50505051;

    /** 靶子怪的 config id，需与 {@link #buildGroup} 里登记的 SceneMonster 一致。 */
    public static final int CONFIG_ID = 1;

    /** 丘丘人通用 AI，配合「不下发武器」让靶子站着不动。 */
    public static final int AI_CONFIG_ID = 12001001;

    private static final int CHALLENGE_ID = 180;
    private static final int CHALLENGE_INDEX = 180;

    private final Player targetPlayer;
    private final List<DPSEntity> targets;
    private final ScheduledExecutorService ticker;
    private final AtomicBoolean settled = new AtomicBoolean();

    /** 挂钟开测时刻。开世界常锁游戏时间，场景秒数不走，结算不能靠 sceneTime。 */
    private final long startedAtMs = System.currentTimeMillis();

    private final int configuredSeconds;

    private float reportedDamage;

    public DPSChallenge(
            Scene scene,
            SceneGroup group,
            List<ChallengeTrigger> challengeTriggers,
            Player targetPlayer,
            List<DPSEntity> targets,
            int timeLimit) {
        super(
                scene,
                group,
                CHALLENGE_ID,
                CHALLENGE_INDEX,
                List.of(targets.size(), timeLimit),
                timeLimit,
                targets.size(),
                challengeTriggers);
        this.targetPlayer = targetPlayer;
        this.targets = targets;
        this.configuredSeconds = timeLimit;
        this.ticker =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            var thread = new Thread(runnable, "DPSMeter-" + targetPlayer.getUid());
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    /** 构造靶子怪所在的伪 group。{@code WorldChallenge#finish} 用它来清场。 */
    public static SceneGroup buildGroup() {
        var monster = new SceneMonster();
        monster.config_id = CONFIG_ID;

        var group = new SceneGroup();
        group.id = GROUP_ID;
        group.monsters = Map.of(CONFIG_ID, monster);
        return group;
    }

    /** 已过去的真实秒数（不受游戏时间锁定影响）。 */
    public int elapsedSeconds() {
        return (int) Math.max(0L, (System.currentTimeMillis() - this.startedAtMs) / 1000L);
    }

    /** 剩余真实秒数。 */
    public int remainingSeconds() {
        return Math.max(0, this.configuredSeconds - this.elapsedSeconds());
    }

    /**
     * 不再每秒推 ChallengeDataNotify：视频里会反复闪「-1s」，还会把 17 秒跳回 21。
     * 客户端用 BeginNotify 的 paramList 时长自行倒数；真正收尾靠挂钟。
     */
    public void refreshClientTimer() {
        // intentionally empty — see DPSTimeTrigger / tick()
    }

    @Override
    public void start() {
        super.start();
        if (!this.inProgress()) return;
        this.ticker.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public void done() {
        super.done();
        this.settle(true);
    }

    @Override
    public void fail() {
        super.fail();
        this.settle(false);
    }

    /** 每秒推一次当前 DPS，并刷新客户端倒计时。 */
    private void tick() {
        try {
            if (!this.inProgress()) {
                this.settle(false);
                return;
            }
            if (!this.targetPlayer.isOnline()) {
                this.fail();
                return;
            }
            // 挂钟到时主动收尾（场景时间锁定时 sceneTime 判据会失效）
            if (this.elapsedSeconds() >= this.configuredSeconds) {
                this.done();
                return;
            }
            var total = this.totalDamage();
            int remain = this.remainingSeconds();
            DPSMeter.reply(
                    this.targetPlayer,
                    String.format(
                            "当前 DPS - %.0f（剩余 %d 秒）", total - this.reportedDamage, remain));
            this.reportedDamage = total;
        } catch (Throwable throwable) {
            Grasscutter.getLogger().warn("DPS 测试计时任务异常", throwable);
        }
    }

    private float totalDamage() {
        float total = 0;
        for (var target : this.targets) {
            total += target.getTotalDamage();
        }
        return total;
    }

    /**
     * 收尾:停计时、清靶子、交还场景的 challenge 槽。只会执行一次。
     *
     * @param report 是否推送结算信息。中途被打断（玩家掉线、挑战失败）时不推送。
     */
    private void settle(boolean report) {
        if (!this.settled.compareAndSet(false, true)) return;
        this.ticker.shutdown();

        int seconds = Math.min(this.configuredSeconds, Math.max(this.elapsedSeconds(), 0));
        // 若刚好超时收尾，显示配置时长更直观
        if (this.elapsedSeconds() >= this.configuredSeconds) {
            seconds = this.configuredSeconds;
        }

        float total = 0;
        float maxHit = 0;
        int hitCount = 0;
        Map<ElementType, Float> byElement = new EnumMap<>(ElementType.class);
        Map<String, Float> byReaction = new HashMap<>();
        for (var target : this.targets) {
            total += target.getTotalDamage();
            hitCount += target.getHitCount();
            maxHit = Math.max(maxHit, target.getMaxHit());
            target.getDamageByElement()
                    .forEach((element, value) -> byElement.merge(element, value, Float::sum));
            target.getDamageByReaction()
                    .forEach((reaction, value) -> byReaction.merge(reaction, value, Float::sum));
        }

        this.targets.forEach(target -> this.getScene().removeEntity(target));
        this.targets.clear();

        if (this.getScene().getChallenge() == this) {
            this.getScene().setChallenge(null);
        }

        if (!report) return;
        DPSMeter.reply(
                this.targetPlayer, summary(seconds, total, maxHit, hitCount, byElement, byReaction));
    }

    private static String summary(
            int seconds,
            float total,
            float maxHit,
            int hitCount,
            Map<ElementType, Float> byElement,
            Map<String, Float> byReaction) {
        var elapsed = Math.max(seconds, 1);
        var message = new StringBuilder("DPS 测试结束，本次结果：");
        message.append(String.format("\n用时 %d 秒", seconds));
        message.append(String.format("\n总伤害 %.0f", total));
        message.append(String.format("\n平均 DPS %.2f", total / elapsed));
        message.append(String.format("\n命中次数 %d", hitCount));
        message.append(String.format("\n单次最高 %.0f", maxHit));
        if (!byElement.isEmpty()) {
            message.append("\n元素伤害：");
            byElement.entrySet().stream()
                    .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                    .forEach(
                            entry ->
                                    message.append(
                                            String.format(
                                                    "\n  %s %.0f (%.1f%%)",
                                                    elementName(entry.getKey()),
                                                    entry.getValue(),
                                                    total <= 0
                                                            ? 0f
                                                            : entry.getValue() * 100f / total)));
        }
        if (!byReaction.isEmpty()) {
            message.append("\n反应伤害：");
            byReaction.entrySet().stream()
                    .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                    .forEach(
                            entry ->
                                    message.append(
                                            String.format(
                                                    "\n  %s %.0f (%.1f%%)",
                                                    entry.getKey(),
                                                    entry.getValue(),
                                                    total <= 0
                                                            ? 0f
                                                            : entry.getValue() * 100f / total)));
        }
        return message.toString();
    }

    private static String elementName(ElementType element) {
        return switch (element) {
            case Fire -> "火";
            case Water -> "水";
            case Grass -> "草";
            case Electric -> "雷";
            case Ice -> "冰";
            case Frozen -> "冻";
            case Wind -> "风";
            case Rock -> "岩";
            case AntiFire -> "灭火";
            case None -> "物理";
            default -> element.name();
        };
    }
}
