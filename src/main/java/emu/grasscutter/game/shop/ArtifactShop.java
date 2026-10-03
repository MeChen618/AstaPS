package emu.grasscutter.game.shop;

import static emu.grasscutter.config.Configuration.GAME;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.ConfigContainer.GameOptions.ArtifactShopOptions;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.reliquary.ReliquaryMainPropData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.utils.objects.WeightedList;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import lombok.Getter;

public class ArtifactShop {
    private static final int GOODS_ID_BASE = 200_000_000;
    private static final int FIVE_STAR_AFFIX_DEPOT = 501;
    private static final List<EquipType> SLOT_ORDER =
            List.of(
                    EquipType.EQUIP_BRACER,
                    EquipType.EQUIP_NECKLACE,
                    EquipType.EQUIP_SHOES,
                    EquipType.EQUIP_RING,
                    EquipType.EQUIP_DRESS);

    private static final Map<EquipType, Map<FightProperty, Double>> MAIN_STATS =
            Map.of(
                    EquipType.EQUIP_BRACER, Map.of(FightProperty.FIGHT_PROP_HP, 100d),
                    EquipType.EQUIP_NECKLACE, Map.of(FightProperty.FIGHT_PROP_ATTACK, 100d),
                    EquipType.EQUIP_SHOES,
                            Map.of(
                                    FightProperty.FIGHT_PROP_HP_PERCENT, 26.68d,
                                    FightProperty.FIGHT_PROP_ATTACK_PERCENT, 26.66d,
                                    FightProperty.FIGHT_PROP_DEFENSE_PERCENT, 26.66d,
                                    FightProperty.FIGHT_PROP_CHARGE_EFFICIENCY, 10d,
                                    FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 10d),
                    EquipType.EQUIP_RING,
                            Map.ofEntries(
                                    Map.entry(FightProperty.FIGHT_PROP_HP_PERCENT, 19.25d),
                                    Map.entry(FightProperty.FIGHT_PROP_ATTACK_PERCENT, 19.25d),
                                    Map.entry(FightProperty.FIGHT_PROP_DEFENSE_PERCENT, 19d),
                                    Map.entry(FightProperty.FIGHT_PROP_FIRE_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_ELEC_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_WATER_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_ICE_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_WIND_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_ROCK_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_GRASS_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_PHYSICAL_ADD_HURT, 5d),
                                    Map.entry(FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 2.5d)),
                    EquipType.EQUIP_DRESS,
                            Map.of(
                                    FightProperty.FIGHT_PROP_HP_PERCENT, 22d,
                                    FightProperty.FIGHT_PROP_ATTACK_PERCENT, 22d,
                                    FightProperty.FIGHT_PROP_DEFENSE_PERCENT, 22d,
                                    FightProperty.FIGHT_PROP_CRITICAL, 10d,
                                    FightProperty.FIGHT_PROP_CRITICAL_HURT, 10d,
                                    FightProperty.FIGHT_PROP_HEAL_ADD, 10d,
                                    FightProperty.FIGHT_PROP_ELEMENT_MASTERY, 4d));

    @Getter private final Int2ObjectMap<ItemData> goods = new Int2ObjectOpenHashMap<>();

    public void install(Int2ObjectMap<List<ShopInfo>> shopData) {
        var options = GAME.artifactShop;
        this.goods.clear();
        shopData.values().forEach(list -> list.removeIf(sold -> sold.getGoodsId() >= GOODS_ID_BASE));
        if (!options.enabled) return;

        var pieces = catalog();
        if (pieces.isEmpty()) return;

        var items = shopData.computeIfAbsent(options.shopId, k -> new ArrayList<ShopInfo>());
        int goodsId = GOODS_ID_BASE;
        for (ItemData piece : pieces) {
            items.add(makeGoods(goodsId, piece, options));
            this.goods.put(goodsId, piece);
            goodsId++;
        }

        Grasscutter.getLogger().info("Listed {} 5-star artifacts in shop {}.", pieces.size(), options.shopId);
    }

    public ItemData getPiece(int goodsId) {
        return this.goods.get(goodsId);
    }

    public GameItem roll(ItemData piece) {
        var options = GAME.artifactShop;
        var item = new GameItem(piece);

        int mainPropId = rollMainProp(piece, options);
        if (mainPropId > 0) item.setMainPropId(mainPropId);

        int level = Math.min(Math.max(options.artifactLevel, 0) + 1, piece.getMaxLevel());
        int substats = piece.getAppendPropNum();
        int totalExp = 0;
        for (int lv = 2; lv <= level; lv++) {
            totalExp += GameData.getRelicExpRequired(piece.getRankLevel(), lv - 1);
            if (piece.canAddRelicProp(lv)) substats++;
        }

        item.setLevel(level);
        item.setTotalExp(totalExp);
        item.getAppendPropIdList().clear();
        item.addAppendProps(substats, bias(options));
        return item;
    }

    private static List<ItemData> catalog() {
        var pieces = new ArrayList<ItemData>();
        for (ItemData data : GameData.getItemDataMap().values()) {
            if (data.getItemType() != ItemType.ITEM_RELIQUARY || data.getRankLevel() != 5) continue;
            if (data.getAppendPropNum() != 4) continue;
            if (data.getAppendPropDepotId() != FIVE_STAR_AFFIX_DEPOT) continue;
            if (data.getMainPropDepotId() != mainPropDepot(data.getEquipType())) continue;
            var set = GameData.getReliquarySetDataMap().get(data.getSetId());
            if (set == null || set.getEquipAffixId() <= 0) continue;
            pieces.add(data);
        }

        pieces.sort(
                Comparator.comparingInt(ItemData::getSetId)
                        .thenComparingInt(data -> SLOT_ORDER.indexOf(data.getEquipType())));
        return pieces;
    }

    private static ShopInfo makeGoods(int goodsId, ItemData piece, ArtifactShopOptions options) {
        var goods = new ShopInfo();
        goods.setGoodsId(goodsId);
        goods.setGoodsItem(new ItemParamData(piece.getId(), 1));
        goods.setScoin(options.costMora);
        goods.setHcoin(options.costPrimogems);
        goods.setBuyLimit(options.buyLimit);
        goods.setMinLevel(1);
        goods.setMaxLevel(99);
        var costs = new ArrayList<ItemParamData>(1);
        if (options.costItemId > 0 && options.costItemCount > 0) {
            costs.add(new ItemParamData(options.costItemId, options.costItemCount));
        }
        goods.setCostItemList(costs);
        return goods;
    }

    private static int rollMainProp(ItemData piece, ArtifactShopOptions options) {
        var pool = MAIN_STATS.get(piece.getEquipType());
        var candidates = GameDepot.getRelicMainPropList(piece.getMainPropDepotId());
        if (pool == null || candidates == null) return 0;

        var randomList = new WeightedList<ReliquaryMainPropData>();
        for (ReliquaryMainPropData prop : candidates) {
            double weight = pool.getOrDefault(prop.getFightProp(), 0d);
            if (weight > 0) randomList.add(weight * statWeight(prop.getFightProp(), options), prop);
        }
        return randomList.size() == 0 ? 0 : randomList.next().getId();
    }

    private static ArtifactRollBias bias(ArtifactShopOptions options) {
        return affix -> {
            double weight = statWeight(affix.getFightProp(), options);
            int tiers = GameDepot.getRelicAffixValueTierCount(affix);
            if (tiers > 1 && options.highRollBias > 0) {
                double height = GameDepot.getRelicAffixValueTier(affix) / (double) (tiers - 1);
                weight *= 1 + options.highRollBias * height;
            }
            return weight;
        };
    }

    private static double statWeight(FightProperty prop, ArtifactShopOptions options) {
        return switch (prop) {
            case FIGHT_PROP_CRITICAL, FIGHT_PROP_CRITICAL_HURT -> options.critWeight;
            case FIGHT_PROP_ATTACK_PERCENT,
                    FIGHT_PROP_ELEMENT_MASTERY,
                    FIGHT_PROP_FIRE_ADD_HURT,
                    FIGHT_PROP_ELEC_ADD_HURT,
                    FIGHT_PROP_WATER_ADD_HURT,
                    FIGHT_PROP_GRASS_ADD_HURT,
                    FIGHT_PROP_WIND_ADD_HURT,
                    FIGHT_PROP_ROCK_ADD_HURT,
                    FIGHT_PROP_ICE_ADD_HURT,
                    FIGHT_PROP_PHYSICAL_ADD_HURT -> options.damageWeight;
            default -> 1;
        };
    }

    private static int mainPropDepot(EquipType slot) {
        return switch (slot) {
            case EQUIP_SHOES -> 1000;
            case EQUIP_NECKLACE -> 2000;
            case EQUIP_DRESS -> 3000;
            case EQUIP_BRACER -> 4000;
            case EQUIP_RING -> 5000;
            default -> 0;
        };
    }
}
