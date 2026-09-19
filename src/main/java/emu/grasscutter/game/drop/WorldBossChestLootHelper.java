package emu.grasscutter.game.drop;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.InvestigationMonsterData;
import emu.grasscutter.data.excels.RewardPreviewData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.server.packet.send.PacketGadgetAutoPickDropInfoNotify;
import java.util.ArrayList;
import java.util.List;

/** Fallback boss trounce-flower loot when DropSystem has no matching ChestDrop row. */
public final class WorldBossChestLootHelper {
    private WorldBossChestLootHelper() {}

    public static boolean grant(Player player, SceneGadget meta, int groupId) {
        if (player == null || meta == null) {
            return false;
        }

        String dropTag = meta.drop_tag;
        int monsterConfigId =
                meta.boss_chest != null ? meta.boss_chest.monster_config_id : 0;
        if (dropTag == null || dropTag.isBlank()) {
            dropTag = BossChestDropTagResolver.resolve(groupId, monsterConfigId);
        }

        if (dropTag != null && !dropTag.isBlank()) {
            try {
                if (player.getServer().getDropSystem().handleBossChestDrop(dropTag, player)) {
                    return true;
                }
            } catch (Throwable t) {
                Grasscutter.getLogger()
                        .warn(
                                "WorldBossChestLootHelper dropSystem failed tag={} uid={}",
                                dropTag,
                                player.getUid(),
                                t);
            }
        }

        RewardPreviewData preview = resolveRewardPreview(groupId, monsterConfigId);
        if (preview == null
                || preview.getPreviewItems() == null
                || preview.getPreviewItems().length == 0) {
            return false;
        }

        List<GameItem> rewards = new ArrayList<>();
        for (ItemParamData param : preview.getPreviewItems()) {
            if (param == null || param.getId() <= 0) {
                continue;
            }
            rewards.add(new GameItem(param.getId(), Math.max(param.getCount(), 1)));
        }
        if (rewards.isEmpty()) {
            return false;
        }
        try {
            BossChestInstructorFilter.apply(rewards);
        } catch (Throwable ignored) {
        }

        player.getInventory().addItems(rewards, ActionReason.OpenWorldBossChest);
        player.sendPacket(new PacketGadgetAutoPickDropInfoNotify(rewards));
        return true;
    }

    private static RewardPreviewData resolveRewardPreview(int groupId, int monsterConfigId) {
        int previewId = 0;
        for (InvestigationMonsterData investigation :
                GameData.getInvestigationMonsterDataMap().values()) {
            if (investigation == null) {
                continue;
            }
            if (monsterConfigId > 0
                    && investigation.getMonsterIdList() != null
                    && investigation.getMonsterIdList().contains(monsterConfigId)) {
                previewId = investigation.getRewardPreviewId();
                break;
            }
            if (groupId > 0
                    && investigation.getGroupIdList() != null
                    && investigation.getGroupIdList().contains(groupId)) {
                previewId = investigation.getRewardPreviewId();
                break;
            }
        }
        if (previewId <= 0) {
            return null;
        }
        RewardPreviewData preview = GameData.getRewardPreviewDataMap().get(previewId);
        if (preview != null) {
            return preview;
        }
        for (int offset = 1; offset <= 4; offset++) {
            preview = GameData.getRewardPreviewDataMap().get(previewId - offset);
            if (preview != null) {
                return preview;
            }
        }
        return null;
    }
}
