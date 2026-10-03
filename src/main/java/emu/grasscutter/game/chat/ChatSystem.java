package emu.grasscutter.game.chat;

import static emu.grasscutter.config.Configuration.GAME;

import emu.grasscutter.GameConstants;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.game.dps.DPSMeter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.ChatInfoOuterClass.ChatInfo;
import emu.grasscutter.server.event.player.PlayerChatEvent;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import java.util.*;
import java.util.regex.Pattern;

public class ChatSystem implements ChatSystemHandler {
    static final String PREFIXES = "[/!]";
    static final Pattern RE_PREFIXES = Pattern.compile(PREFIXES);
    static final Pattern RE_COMMANDS = Pattern.compile("\n" + PREFIXES);

    private final Map<Integer, Map<Integer, List<ChatInfo>>> history = new HashMap<>();
    private final GameServer server;

    public ChatSystem(GameServer server) {
        this.server = server;
    }

    public GameServer getServer() {
        return server;
    }

    private boolean tryInvokeCommand(Player sender, Player target, String rawMessage) {
        if (rawMessage.isEmpty()) return false;
        if (DPSMeter.handleChat(sender, rawMessage)) return true;
        if (!RE_PREFIXES.matcher(rawMessage.substring(0, 1)).matches()) return false;
        for (String line : rawMessage.substring(1).split("\n[/!]"))
            CommandMap.getInstance().invoke(sender, target, line);
        return true;
    }

    private void putInHistory(int uid, int partnerId, ChatInfo info) {
        this.history
                .computeIfAbsent(uid, x -> new HashMap<>())
                .computeIfAbsent(partnerId, x -> new ArrayList<>())
                .add(info);
    }

    public void clearHistoryOnLogout(Player player) {
        this.history.remove(player.getUid());
    }

    public void handlePullPrivateChatReq(Player player, int partnerId) {
        var chatHistory =
                this.history
                        .computeIfAbsent(player.getUid(), x -> new HashMap<>())
                        .computeIfAbsent(partnerId, x -> new ArrayList<>());
        player.sendPacket(new PacketPullPrivateChatRsp(chatHistory));
    }

    public void handlePullRecentChatReq(Player player) {
        this.ensureServerConversation(player);
    }

    public void sendPrivateMessageFromServer(int targetUid, String message) {
        sendPrivateMessageFromBot(GameConstants.SERVER_CONSOLE_UID, targetUid, message);
    }

    public void sendPrivateMessageFromServer(int targetUid, int emote) {
        Player target = getServer().getPlayerByUid(targetUid);
        if (target == null) return;

        var packet = new PacketPrivateChatNotify(GameConstants.SERVER_CONSOLE_UID, targetUid, emote);
        putInHistory(targetUid, GameConstants.SERVER_CONSOLE_UID, packet.getChatInfo());
        target.sendPacket(packet);
    }

    public void sendPrivateMessageFromBot(int fromUid, int targetUid, String message) {
        if (message == null || message.isEmpty()) return;
        if (!GameConstants.isServerBotUid(fromUid)) fromUid = GameConstants.SERVER_CONSOLE_UID;

        Player target = getServer().getPlayerByUid(targetUid);
        if (target == null) return;

        var packet = new PacketPrivateChatNotify(fromUid, targetUid, message);
        putInHistory(targetUid, fromUid, packet.getChatInfo());
        target.sendPacket(packet);
    }

    public void sendPrivateMessage(Player player, int targetUid, String message) {
        if (message == null || message.length() == 0) return;

        var target = getServer().getPlayerByUid(targetUid);
        if (target == null && !GameConstants.isServerBotUid(targetUid)) return;

        var event = new PlayerChatEvent(player, message, target);
        event.call();
        if (event.isCanceled()) return;

        if (!GameConstants.isServerBotUid(targetUid)) {
            targetUid = event.getTargetUid();
            if (targetUid == -1) return;
        }

        message = event.getMessage();
        if (message == null || message.length() == 0) return;

        var packet = new PacketPrivateChatNotify(player.getUid(), targetUid, message);
        player.sendPacket(packet);
        putInHistory(player.getUid(), targetUid, packet.getChatInfo());

        if (targetUid == GameConstants.SERVER_DPS_UID) {
            if (!DPSMeter.handleChat(player, message)) {
                sendPrivateMessageFromBot(
                        GameConstants.SERVER_DPS_UID,
                        player.getUid(),
                        "Usage: send dps30 (or dps60) to start a run; send dpsstop to end it early.");
            }
            return;
        }

        var isCommand = tryInvokeCommand(player, target, message);
        if (target != null && !isCommand) {
            target.sendPacket(packet);
            this.putInHistory(targetUid, player.getUid(), packet.getChatInfo());
        }
    }

    public void sendPrivateMessage(Player player, int targetUid, int emote) {
        var target = getServer().getPlayerByUid(targetUid);
        if (target == null && !GameConstants.isServerBotUid(targetUid)) return;

        var event = new PlayerChatEvent(player, emote, target);
        event.call();
        if (event.isCanceled()) return;

        if (!GameConstants.isServerBotUid(targetUid)) {
            targetUid = event.getTargetUid();
            if (targetUid == -1) return;
        }
        emote = event.getMessageAsInt();
        if (emote == -1) return;
        targetUid = event.getChannel() == -1 ? targetUid : targetUid;

        var packet = new PacketPrivateChatNotify(player.getUid(), targetUid, emote);
        player.sendPacket(packet);
        this.putInHistory(player.getUid(), targetUid, packet.getChatInfo());

        if (target != null) {
            target.sendPacket(packet);
            this.putInHistory(targetUid, player.getUid(), packet.getChatInfo());
        }
    }

    public void sendTeamMessage(Player player, int channel, String message) {
        if (message == null || message.length() == 0) return;
        if (this.tryInvokeCommand(player, null, message)) return;

        var event = new PlayerChatEvent(player, message, channel);
        event.call();
        if (event.isCanceled()) return;

        message = event.getMessage();
        if (message == null || message.length() == 0) return;
        channel = event.getChannel();
        if (channel == -1) return;
        player.getWorld().broadcastPacket(new PacketPlayerChatNotify(player, channel, message));
    }

    public void sendTeamMessage(Player player, int channel, int icon) {
        var event = new PlayerChatEvent(player, icon, channel);
        event.call();
        if (event.isCanceled()) return;

        icon = event.getMessageAsInt();
        if (icon == -1) return;
        channel = event.getChannel();
        if (channel == -1) return;
        player.getWorld().broadcastPacket(new PacketPlayerChatNotify(player, channel, icon));
    }

    private void sendServerWelcomeMessages(Player player) {
        var joinOptions = GAME.joinOptions;

        if (joinOptions.welcomeEmotes != null && joinOptions.welcomeEmotes.length > 0) {
            this.sendPrivateMessageFromServer(
                    player.getUid(),
                    joinOptions.welcomeEmotes[Utils.randomRange(0, joinOptions.welcomeEmotes.length - 1)]);
        }

        if (joinOptions.welcomeMessage != null && joinOptions.welcomeMessage.length() > 0) {
            this.sendPrivateMessageFromServer(player.getUid(), joinOptions.welcomeMessage);
        }
    }

    public void ensureServerConversation(Player player) {
        var playerHistory = this.history.computeIfAbsent(player.getUid(), x -> new HashMap<>());
        var serverHistory = playerHistory.get(GameConstants.SERVER_CONSOLE_UID);
        if (serverHistory == null || serverHistory.isEmpty()) {
            this.sendServerWelcomeMessages(player);
            serverHistory = playerHistory.get(GameConstants.SERVER_CONSOLE_UID);
        }

        if (serverHistory == null || serverHistory.isEmpty()) return;

        int historyLength = serverHistory.size();
        var recentMessages = serverHistory.subList(Math.max(historyLength - 3, 0), historyLength);
        player.sendPacket(new PacketPullRecentChatRsp(recentMessages));
        player.sendPacket(new PacketPullPrivateChatRsp(serverHistory));
    }
}
