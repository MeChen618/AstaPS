package emu.grasscutter.game.dps;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.dungeons.challenge.trigger.ChallengeTrigger;
import emu.grasscutter.game.dungeons.challenge.trigger.KillMonsterTrigger;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * DPS 测试的入口。
 *
 * <p>好友「DPS」或任意聊天框发「dps30秒」即可开测；「dps停止」提前结束。
 * {@code /dps} 命令走的也是这里，两条路径行为一致。
 */
public final class DPSMeter {

    public static final int DEFAULT_SECONDS = 60;
    public static final int MIN_SECONDS = 1;
    public static final int MAX_SECONDS = 120;
    public static final int MAX_TARGETS = 10;

    /** 丘丘暴徒。不给它武器 AI 就起不来，会站在原地当靶子。 */
    private static final int TARGET_MONSTER_ID = 21020201;

    private static final int TARGET_LEVEL = 90;

    /** 免前缀触发式：dps30秒 / dps30s / dps30。数字即秒数。 */
    private static final Pattern CHAT_START =
            Pattern.compile("^dps(\\d{1,3})(?:秒|s)?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern CHAT_STOP =
            Pattern.compile("^dps(?:停|停止|结束|stop)$", Pattern.CASE_INSENSITIVE);

    private DPSMeter() {}

    /** 从好友 DPS 指令器回消息（失败则退回控制台私聊）。 */
    public static void reply(Player player, String message) {
        if (player == null) {
            CommandHandler.sendMessage(null, message);
            return;
        }
        try {
            var chat = player.getServer().getChatSystem();
            if (chat != null) {
                chat.sendPrivateMessageFromBot(
                        GameConstants.SERVER_DPS_UID, player.getUid(), message);
                return;
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("DPS reply via bot failed, fallback", t);
        }
        CommandHandler.sendMessage(player, message);
    }

    /**
     * 尝试把一条聊天内容当作 DPS 指令处理。
     *
     * @return 已被当作指令消费（此时不应再作为聊天内容广播出去）
     */
    public static boolean handleChat(Player sender, String rawMessage) {
        if (sender == null || rawMessage == null) return false;

        var normalized = normalize(rawMessage);
        if (!normalized.startsWith("dps")) return false;

        if (CHAT_STOP.matcher(normalized).matches()) {
            stop(sender);
            return true;
        }

        var matcher = CHAT_START.matcher(normalized);
        if (!matcher.matches()) return false;

        start(sender, Integer.parseInt(matcher.group(1)), 1);
        return true;
    }

    /** 去掉空白、全角数字转半角、统一小写，这样中文输入法下打出来的内容也能命中。 */
    private static String normalize(String message) {
        var builder = new StringBuilder(message.length());
        for (var i = 0; i < message.length(); i++) {
            var c = message.charAt(i);
            if (c >= '０' && c <= '９') c -= 0xFEE0; // 全角 ０-９
            if (c == '　' || Character.isWhitespace(c)) continue;
            builder.append(Character.toLowerCase(c));
        }
        return builder.toString();
    }

    /**
     * 开始一场 DPS 测试。
     *
     * @param seconds 时长，会被夹到 [{@value MIN_SECONDS}, {@value MAX_SECONDS}] 秒
     * @param targetCount 靶子数量，会被夹到 [1, {@value MAX_TARGETS}]
     */
    public static void start(Player player, int seconds, int targetCount) {
        if (player == null) {
            CommandHandler.sendMessage(null, "DPS 测试只能由玩家发起。");
            return;
        }

        var timeLimit = Math.min(MAX_SECONDS, Math.max(MIN_SECONDS, seconds));
        var count = Math.min(MAX_TARGETS, Math.max(1, targetCount));

        var scene = player.getScene();
        var running = scene.getChallenge();
        if (running != null && running.inProgress()) {
            reply(
                    player,
                    running instanceof DPSChallenge
                            ? "已有一场 DPS 测试正在进行，发送「dps停止」可提前结束。"
                            : "当前场景正在进行其它挑战，无法开始 DPS 测试。");
            return;
        }

        var monsterData = GameData.getMonsterDataMap().get(TARGET_MONSTER_ID);
        if (monsterData == null) {
            reply(player, "找不到靶子怪数据（" + TARGET_MONSTER_ID + "），请检查资源文件。");
            return;
        }

        var pos = player.getPosition();
        var rot = player.getRotation();

        List<DPSEntity> targets = new ArrayList<>(count);
        for (var i = 0; i < count; i++) {
            targets.add(new DPSEntity(scene, monsterData, pos.nearby2d(2f), rot, TARGET_LEVEL));
        }

        List<ChallengeTrigger> triggers = new ArrayList<>(2);
        triggers.add(new KillMonsterTrigger(DPSChallenge.CONFIG_ID)); // 打死靶子提前出结算
        triggers.add(new DPSTimeTrigger()); // 时间到出结算

        // 开测前把队伍状态归位，免得上一把的残血/空大影响读数。
        player.getTeamManager().getActiveTeam().forEach(DPSMeter::resetForTest);

        var challenge =
                new DPSChallenge(
                        scene, DPSChallenge.buildGroup(), triggers, player, targets, timeLimit);
        scene.setChallenge(challenge);
        challenge.start();
        targets.forEach(scene::addEntity);

        reply(
                player,
                count == 1
                        ? String.format("DPS 测试已开始：%d 秒。发「dps停止」可提前结束。", timeLimit)
                        : String.format(
                                "DPS 测试已开始：%d 秒 / %d 只靶子。发「dps停止」可提前结束。",
                                timeLimit, count));
    }

    /** 提前结束当前场景的 DPS 测试。 */
    public static void stop(Player player) {
        if (player == null) return;

        if (player.getScene().getChallenge() instanceof DPSChallenge challenge
                && challenge.inProgress()) {
            challenge.done();
        } else {
            reply(player, "当前没有正在进行的 DPS 测试。");
        }
    }

    private static void resetForTest(EntityAvatar entity) {
        var player = entity.getPlayer();
        if (!entity.isAlive() && player != null) {
            player.getTeamManager().reviveAvatar(entity.getAvatar());
        }
        entity.heal(entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP), true);
        entity.addEnergy(100f, PropChangeReason.PropChangeReason_PROP_CHANGE_ENERGY_BALL);
        entity
                .getWorld()
                .broadcastPacket(
                        new PacketAvatarFightPropUpdateNotify(
                                entity.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
    }
}
