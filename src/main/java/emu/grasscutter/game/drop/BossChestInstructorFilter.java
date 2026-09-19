/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.drop;

import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.EquipType;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import java.util.List;

/**
 * Boss chest 5★ relics are rewritten to Instructor (set 10007) piece IDs {@code 575xx}.
 *
 * <p>Also rewrites compressed Instructor templates {@code 23511-23515} (append depots 961-965).
 * Those depots store one already-stacked value per substat (e.g. HP 746.88), so a +0 piece looks
 * fully upgraded. Standard {@code 575xx} pieces use depot 501 with normal single rolls.
 */
public final class BossChestInstructorFilter {
    private static final int INSTRUCTOR_SET = 10007;

    private BossChestInstructorFilter() {
    }

    public static void apply(List<GameItem> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (int i = 0; i < list.size(); ++i) {
            GameItem gameItem = BossChestInstructorFilter.replaceIfNeeded(list.get(i));
            if (gameItem == null) continue;
            list.set(i, gameItem);
        }
    }

    private static GameItem replaceIfNeeded(GameItem gameItem) {
        if (gameItem == null || gameItem.getItemType() != ItemType.ITEM_RELIQUARY) {
            return null;
        }
        ItemData itemData = gameItem.getItemData();
        if (itemData == null || itemData.getRankLevel() != 5) {
            return null;
        }
        boolean instructor = itemData.getSetId() == INSTRUCTOR_SET;
        boolean compressed = isCompressedAppendDepot(itemData.getAppendPropDepotId());
        // Keep good Instructor pieces (575xx / depot 501). Rewrite everything else that is 5★.
        if (instructor && !compressed) {
            return null;
        }
        int variant = resolveAppendVariant(gameItem, itemData, compressed);
        int n = BossChestInstructorFilter.instructorItemId(itemData.getEquipType(), variant);
        if (n <= 0) {
            return null;
        }
        return new GameItem(n, gameItem.getCount());
    }

    /**
     * Resources ship "compressed" affix depots where each group has a single inflated value equal
     * to ~2.5× a max 5★ roll — used by template item ids 23511-23515 and many newer set ids.
     */
    private static boolean isCompressedAppendDepot(int depotId) {
        return (depotId >= 961 && depotId <= 965) || (depotId >= 941 && depotId <= 947);
    }

    private static int resolveAppendVariant(GameItem gameItem, ItemData itemData, boolean compressed) {
        // Compressed templates (23511-23515) encode slot in the id suffix, not appendPropNum.
        // Prefer the excel appendPropNum (usually 4) so +0 pieces get a normal 4-liner.
        if (compressed) {
            int num = itemData.getAppendPropNum();
            if (num < 0) {
                num = 0;
            }
            if (num > 4) {
                num = 4;
            }
            return num > 0 ? num : 4;
        }
        int n = gameItem.getItemId() % 10;
        if (n < 0 || n > 4) {
            n = Math.min(4, Math.max(0, itemData.getAppendPropNum()));
        }
        return n;
    }

    /**
     * Map equip slot → Instructor 575xx base id. Must match ReliquaryExcelConfigData:
     * <ul>
     *   <li>5751x RING (goblet)
     *   <li>5752x NECKLACE (plume)
     *   <li>5753x DRESS (circlet)
     *   <li>5754x BRACER (flower)
     *   <li>5755x SHOES (sands)
     * </ul>
     */
    private static int instructorItemId(EquipType equipType, int n) {
        if (equipType == null) {
            return -1;
        }
        int base;
        if (equipType == EquipType.EQUIP_BRACER) {
            base = 57540;
        } else if (equipType == EquipType.EQUIP_NECKLACE) {
            base = 57520;
        } else if (equipType == EquipType.EQUIP_SHOES) {
            base = 57550;
        } else if (equipType == EquipType.EQUIP_RING) {
            base = 57510;
        } else if (equipType == EquipType.EQUIP_DRESS) {
            base = 57530;
        } else {
            return -1;
        }
        if (n < 0 || n > 4) {
            n = 0;
        }
        return base + n;
    }
}
