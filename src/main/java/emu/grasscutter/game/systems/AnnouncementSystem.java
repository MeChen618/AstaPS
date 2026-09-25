package emu.grasscutter.game.systems;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.DataLoader;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.proto.AnnounceDataOuterClass;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import java.util.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
public class AnnouncementSystem extends BaseGameSystem {
    private final Map<Integer, AnnounceConfigItem> announceConfigItemMap;

    public AnnouncementSystem(GameServer server) {
        super(server);
        this.announceConfigItemMap = new HashMap<>();
        loadConfig();
    }

    private int loadConfig() {
        try {
            List<AnnounceConfigItem> announceConfigItems =
                    DataLoader.loadBundledList("Announcement.json", AnnounceConfigItem.class);

            announceConfigItemMap.clear();
            announceConfigItems.forEach(i -> announceConfigItemMap.put(i.getTemplateId(), i));
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load server announce config.", e);
        }

        return announceConfigItemMap.size();
    }

    public List<Player> getOnlinePlayers() {
        return getServer().getWorlds().stream()
                .map(World::getPlayers)
                .flatMap(Collection::stream)
                .toList();
    }

    public void broadcast(List<AnnounceConfigItem> tpl) {
        if (tpl == null || tpl.size() == 0) {
            return;
        }

        var list =
                tpl.stream()
                        .map(AnnounceConfigItem::toProto)
                        .map(AnnounceDataOuterClass.AnnounceData.Builder::build)
                        .toList();

        getOnlinePlayers().forEach(i -> i.sendPacket(new PacketServerAnnounceNotify(list)));
    }

    public int refresh() {
        return loadConfig();
    }

    public void revoke(int tplId) {
        getOnlinePlayers().forEach(i -> i.sendPacket(new PacketServerAnnounceRevokeNotify(tplId)));
    }

    public enum AnnounceType {
        CENTER,
        COUNTDOWN
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public class AnnounceConfigItem {
        int templateId;
        AnnounceType type;
        int frequency;
        String content;
        Date beginTime;
        Date endTime;
        boolean tick;
        int interval;

        public AnnounceDataOuterClass.AnnounceData.Builder toProto() {
            var proto = AnnounceDataOuterClass.AnnounceData.newBuilder();

            proto
                    .setConfigId(templateId)
                    // I found the time here is useless
                    .setBeginTime(Utils.getCurrentSeconds() + 1)
                    .setEndTime(Utils.getCurrentSeconds() + 10);

            // The text was never put on the wire at all: the client was handed a config id and
            // three empty strings, and went looking for content that does not exist.
            //
            // 7.0 renumbered this message and left the three string fields unnamed. Between them
            // they are the centre-screen text, the countdown text and the dungeon-entry text -
            // which is which is not recoverable from the proto dump, so all three carry the
            // content. Whichever one the client reads for this announce type, it finds the text.
            var text = content == null ? "" : content;
            proto.setDungeonConfirmText(text).setCountDownText(text).setCenterSystemText(text);

            // The two remaining uint32 fields are unnamed in 7.0 as well, and were being fed
            // `frequency` on the assumption that they are the repeat intervals. A wrong value in
            // an unidentified field is a far better explanation for a client crash than an empty
            // string, and the server does not need them: AnnouncementTask already re-broadcasts
            // on the config's own `interval`.

            return proto;
        }
    }
}
