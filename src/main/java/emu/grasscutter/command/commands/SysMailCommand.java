/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.command.Command
 *  emu.grasscutter.command.Command$TargetRequirement
 *  emu.grasscutter.command.CommandHandler
 *  emu.grasscutter.database.DatabaseHelper
 *  emu.grasscutter.game.mail.Mail
 *  emu.grasscutter.game.mail.Mail$MailItem
 *  emu.grasscutter.game.player.Player
 */
package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.mail.SystemMailHelper;
import emu.grasscutter.game.player.Player;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@Command(label="sysmail", usage={"all", "update <\u6807\u9898\u5173\u952e\u8bcd> <\u65b0\u6b63\u6587>", "retitle <\u6807\u9898\u5173\u952e\u8bcd> <\u65b0\u6807\u9898>", "delete <\u6807\u9898\u5173\u952e\u8bcd>", "deleteall", "help"}, permission="server.sendmail", aliases={"systemmail", "smail"}, targetRequirement=Command.TargetRequirement.NONE)
public final class SysMailCommand
implements CommandHandler {
    private static final HashMap<Integer, Draft> drafts = new HashMap();

    private static int senderKey(Player player) {
        return player != null ? player.getUid() : -1;
    }

    public void execute(Player player, Player player2, List<String> list) {
        String string;
        if (list.isEmpty()) {
            this.sendUsageMessage(player, new String[0]);
            CommandHandler.sendMessage((Player)player, (String)"\u7cfb\u7edf\u90ae\u4ef6\uff1a\u6807\u9898\u81ea\u52a8\u5e26 [\u7cfb\u7edf\u516c\u544a] \u524d\u7f00\uff0c\u73a9\u5bb6\u4e0d\u53ef\u5220\u9664\uff0c\u53ef\u7528 update/delete \u7ba1\u7406\u3002");
            return;
        }
        switch (string = list.get(0).toLowerCase()) {
            case "help": {
                this.sendUsageMessage(player, new String[0]);
                CommandHandler.sendMessage((Player)player, (String)"\u53d1\u5168\u5458: /sysmail all -> \u6807\u9898 -> \u6b63\u6587 -> \u53d1\u4ef6\u4eba -> finish");
                CommandHandler.sendMessage((Player)player, (String)"\u6539\u6b63\u6587: /sysmail update \u4fee\u590d\u5185\u5bb9 \u65b0\u7684\u516c\u544a\u6b63\u6587...");
                CommandHandler.sendMessage((Player)player, (String)"\u6539\u6807\u9898: /sysmail retitle \u4fee\u590d\u5185\u5bb9 \u65b0\u6807\u9898");
                CommandHandler.sendMessage((Player)player, (String)"\u5220\u90ae\u4ef6: /sysmail delete \u4fee\u590d\u5185\u5bb9");
                CommandHandler.sendMessage((Player)player, (String)"\u5220\u5168\u90e8\u7cfb\u7edf\u90ae\u4ef6: /sysmail deleteall");
                break;
            }
            case "all": {
                this.startDraft(player);
                break;
            }
            case "stop": {
                drafts.remove(SysMailCommand.senderKey(player));
                CommandHandler.sendMessage((Player)player, (String)"\u5df2\u53d6\u6d88\u7cfb\u7edf\u90ae\u4ef6\u7f16\u8f91\u3002");
                break;
            }
            case "finish": {
                this.finishDraft(player);
                break;
            }
            case "update": {
                String string2;
                if (list.size() < 3) {
                    CommandHandler.sendMessage((Player)player, (String)"\u7528\u6cd5: /sysmail update <\u6807\u9898\u5173\u952e\u8bcd> <\u65b0\u6b63\u6587>");
                    return;
                }
                String string3 = list.get(1);
                int n = SystemMailHelper.updateContentByTitle(string3, string2 = String.join((CharSequence)" ", list.subList(2, list.size())));
                CommandHandler.sendMessage((Player)player, (String)(n > 0 ? "\u5df2\u66f4\u65b0 " + n + " \u5c01\u7cfb\u7edf\u90ae\u4ef6\u6b63\u6587\u3002" : "\u672a\u627e\u5230\u5339\u914d\u7684\u7cfb\u7edf\u90ae\u4ef6\u3002"));
                break;
            }
            case "retitle": {
                if (list.size() < 3) {
                    CommandHandler.sendMessage((Player)player, (String)"\u7528\u6cd5: /sysmail retitle <\u6807\u9898\u5173\u952e\u8bcd> <\u65b0\u6807\u9898>");
                    return;
                }
                int n = SystemMailHelper.updateTitle(list.get(1), String.join((CharSequence)" ", list.subList(2, list.size())));
                CommandHandler.sendMessage((Player)player, (String)(n > 0 ? "\u5df2\u66f4\u65b0 " + n + " \u5c01\u7cfb\u7edf\u90ae\u4ef6\u6807\u9898\u3002" : "\u672a\u627e\u5230\u5339\u914d\u7684\u7cfb\u7edf\u90ae\u4ef6\u3002"));
                break;
            }
            case "delete": 
            case "del": 
            case "remove": {
                if (list.size() < 2) {
                    CommandHandler.sendMessage((Player)player, (String)"\u7528\u6cd5: /sysmail delete <\u6807\u9898\u5173\u952e\u8bcd>");
                    return;
                }
                String string4 = String.join((CharSequence)" ", list.subList(1, list.size()));
                int n = SystemMailHelper.deleteByTitle(string4);
                CommandHandler.sendMessage((Player)player, (String)(n > 0 ? "\u5df2\u5220\u9664 " + n + " \u5c01\u7cfb\u7edf\u90ae\u4ef6\u3002" : "\u672a\u627e\u5230\u5339\u914d\u7684\u7cfb\u7edf\u90ae\u4ef6\u3002"));
                break;
            }
            case "deleteall": {
                int n = SystemMailHelper.deleteAllProtected();
                CommandHandler.sendMessage((Player)player, (String)(n > 0 ? "\u5df2\u5220\u9664\u5168\u90e8 " + n + " \u5c01\u7cfb\u7edf\u90ae\u4ef6\u3002" : "\u6ca1\u6709\u53ef\u5220\u9664\u7684\u7cfb\u7edf\u90ae\u4ef6\u3002"));
                break;
            }
            default: {
                this.continueDraft(player, list);
            }
        }
    }

    private void startDraft(Player player) {
        drafts.put(SysMailCommand.senderKey(player), new Draft());
        CommandHandler.sendMessage((Player)player, (String)"\u5f00\u59cb\u7f16\u5199\u7cfb\u7edf\u90ae\u4ef6\u3002\u4e0b\u4e00\u6b65: \u8f93\u5165\u6807\u9898");
    }

    private void continueDraft(Player player, List<String> list) {
        int n = SysMailCommand.senderKey(player);
        Draft draft = drafts.get(n);
        if (draft == null) {
            CommandHandler.sendMessage((Player)player, (String)"\u8bf7\u5148 /sysmail all");
            return;
        }
        String string = String.join((CharSequence)" ", list);
        switch (draft.stage) {
            case 0: {
                draft.mail.mailContent.title = SystemMailHelper.normalizeTitle(string);
                draft.stage = 1;
                CommandHandler.sendMessage((Player)player, (String)("\u6807\u9898: " + draft.mail.mailContent.title));
                CommandHandler.sendMessage((Player)player, (String)"\u4e0b\u4e00\u6b65: \u8f93\u5165\u6b63\u6587");
                break;
            }
            case 1: {
                draft.mail.mailContent.content = string;
                draft.stage = 2;
                CommandHandler.sendMessage((Player)player, (String)"\u4e0b\u4e00\u6b65: \u8f93\u5165\u53d1\u4ef6\u4eba\u540d\u79f0");
                break;
            }
            case 2: {
                draft.mail.mailContent.sender = string;
                draft.stage = 3;
                CommandHandler.sendMessage((Player)player, (String)"\u53ef\u8ffd\u52a0\u9644\u4ef6(\u7269\u54c1ID \u6570\u91cf)\uff0c\u6216\u76f4\u63a5 /sysmail finish");
                break;
            }
            case 3: {
                if (list.size() >= 2) {
                    try {
                        int n2 = Integer.parseInt(list.get(0));
                        int n3 = Integer.parseInt(list.get(1));
                        draft.mail.itemList.add(new Mail.MailItem(n2, n3));
                        CommandHandler.sendMessage((Player)player, (String)("\u5df2\u6dfb\u52a0\u9644\u4ef6 " + n2 + " x" + n3));
                    }
                    catch (NumberFormatException numberFormatException) {
                        CommandHandler.sendMessage((Player)player, (String)"\u9644\u4ef6\u683c\u5f0f: <\u7269\u54c1ID> <\u6570\u91cf>");
                    }
                    break;
                }
                CommandHandler.sendMessage((Player)player, (String)"\u9644\u4ef6\u683c\u5f0f: <\u7269\u54c1ID> <\u6570\u91cf>\uff0c\u6216 /sysmail finish");
                break;
            }
            default: {
                CommandHandler.sendMessage((Player)player, (String)"\u8bf7 /sysmail finish \u53d1\u9001");
            }
        }
    }

    private void finishDraft(Player player2) {
        int n = SysMailCommand.senderKey(player2);
        Draft draft = drafts.remove(n);
        if (draft == null || draft.stage < 2) {
            CommandHandler.sendMessage((Player)player2, (String)"\u7cfb\u7edf\u90ae\u4ef6\u672a\u5b8c\u6210\uff0c\u9700\u8981\u81f3\u5c11\u6807\u9898\u548c\u6b63\u6587\u3002");
            return;
        }
        int[] nArray = new int[]{0};
        DatabaseHelper.getByGameClass(Player.class).forEach(player -> {
            Player target = Objects.requireNonNullElse(Grasscutter.getGameServer().getPlayerByUid(player.getUid(), false), player);
            target.sendMail(SysMailCommand.cloneMail(draft.mail));
            nArray[0] = nArray[0] + 1;
        });
        CommandHandler.sendMessage((Player)player2, (String)("\u5df2\u5411 " + nArray[0] + " \u540d\u73a9\u5bb6\u53d1\u9001\u7cfb\u7edf\u90ae\u4ef6: " + draft.mail.mailContent.title));
    }

    private static Mail cloneMail(Mail mail) {
        Mail mail2 = SystemMailHelper.newSystemMail();
        mail2.mailContent.title = mail.mailContent.title;
        mail2.mailContent.content = mail.mailContent.content;
        mail2.mailContent.sender = mail.mailContent.sender;
        mail2.itemList.addAll(mail.itemList);
        return mail2;
    }

    private static final class Draft {
        Mail mail = SystemMailHelper.newSystemMail();
        int stage = 0;

        Draft() {
        }
    }
}

