package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.InventoryAddPolicy;
import emu.grasscutter.game.inventory.InventoryAddResult;
import emu.grasscutter.game.inventory.InventoryGrantBuilder;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.tps.TpsAvatarSystem;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GadgetChainLevelChangeNotifyOuterClass.GadgetChainLevelChangeNotify;
import emu.grasscutter.net.proto.GadgetChainLevelUpdateNotifyOuterClass.GadgetChainLevelUpdateNotify;
import emu.grasscutter.net.proto.OfferingInfoOuterClass.OfferingInfo;
import emu.grasscutter.net.proto.PlayerOfferingDataNotifyOuterClass.PlayerOfferingDataNotify;
import emu.grasscutter.net.proto.PlayerOfferingDataOuterClass.PlayerOfferingData;
import emu.grasscutter.net.proto.PlayerOfferingReqOuterClass.PlayerOfferingReq;
import emu.grasscutter.net.proto.PlayerOfferingRspOuterClass.PlayerOfferingRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SceneOwnerGadgetChainLevelNotifyOuterClass.SceneOwnerGadgetChainLevelNotify;
import emu.grasscutter.net.proto.TakeOfferingLevelRewardReqOuterClass.TakeOfferingLevelRewardReq;
import emu.grasscutter.net.proto.TakeOfferingLevelRewardRspOuterClass.TakeOfferingLevelRewardRsp;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.packet.send.PacketPlayerPropNotify;
import emu.grasscutter.utils.ProtoWire;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper for offering objects (Sacred Sakura, Frostbearing Tree, Tree of Dreams and the like):
 * gadget to offeringId mapping, the login notify and the interaction responses.
 *
 * <p>The client shows the F prompt based on SceneGadgetInfo.offering_info, and opens the offering
 * page based on PlayerOfferingDataNotify.
 */
public final class OfferingHelper {
    /** Sacred Sakura OfferingGadget. */
    public static final int GADGET_SACRED_SAKURA = 70290094;
    /** Sacred Sakura offeringId (OPEN_STATE_ORAIONOKAMI). */
    public static final int OFFERING_ORAIONOKAMI = 2;
    /** OPEN_STATE_ORAIONOKAMI */
    public static final int OPEN_STATE_ORAIONOKAMI = 2000;

    /** Known OfferingGadget to offeringId, covering the main open-world offerings. */
    private static final Map<Integer, Integer> GADGET_TO_OFFERING = new LinkedHashMap<>();

    static {
        GADGET_TO_OFFERING.put(70290032, 1); // Frostbearing Tree
        GADGET_TO_OFFERING.put(GADGET_SACRED_SAKURA, OFFERING_ORAIONOKAMI); // Sacred Sakura
        GADGET_TO_OFFERING.put(70290511, 5); // Tree of Dreams
        GADGET_TO_OFFERING.put(70290803, 5); // Sumeru Lake of Rebirth interaction, same tree family
        GADGET_TO_OFFERING.put(70330637, 7); // wishing pool
        GADGET_TO_OFFERING.put(70292163, 8); // saurian cub
        GADGET_TO_OFFERING.put(73002033, 9); // Natlan tribal offering
        GADGET_TO_OFFERING.put(73065005, 10);
        GADGET_TO_OFFERING.put(73065006, 11);
        GADGET_TO_OFFERING.put(73065007, 12);
        GADGET_TO_OFFERING.put(73074085, 13);
        GADGET_TO_OFFERING.put(73074086, 14);
        GADGET_TO_OFFERING.put(73074087, 15);
        GADGET_TO_OFFERING.put(73077192, 16); // firmament
    }

    /**
     * In-process cache. The source of truth is the player questGlobalVariables / JSON on disk, so a
     * restart cannot swallow sigils without recording the level.
     */
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Integer>> LEVELS =
            new ConcurrentHashMap<>();

    /** Claimed level rewards: uid to offeringId to levels. */
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, java.util.Set<Integer>>>
            TAKEN = new ConcurrentHashMap<>();

    /** questGlobalVariables key: 910000 + offeringId. */
    private static final int QGV_OFFERING_BASE = 910000;

    private static final java.nio.file.Path LEVELS_FILE =
            java.nio.file.Path.of("data", "offering_levels.json");

    private OfferingHelper() {}

    private static int qgvKey(int offeringId) {
        return QGV_OFFERING_BASE + offeringId;
    }

    public static int resolveOfferingId(EntityGadget gadget) {
        if (gadget == null) {
            return 0;
        }
        Integer mapped = GADGET_TO_OFFERING.get(gadget.getGadgetId());
        if (mapped != null) {
            return mapped;
        }
        try {
            var data = gadget.getGadgetData();
            if (data != null && data.getJsonName() != null) {
                String j = data.getJsonName();
                if (j.contains("ThunderSeedOffer")) {
                    return OFFERING_ORAIONOKAMI;
                }
                if (j.contains("AncientBloodTree")) {
                    return 1;
                }
                if (j.contains("XMOfferingTree") || j.contains("RebornLake")) {
                    return 5;
                }
                if (j.contains("WishingPond")) {
                    return 7;
                }
                if (j.contains("Silong")) {
                    return 8;
                }
                if (j.contains("NatlanOffering")) {
                    return 9;
                }
                if (j.contains("NodkraiOffering") || j.contains("NodKraiOffering")) {
                    return 10;
                }
                if (j.contains("FirmamentOffering")) {
                    return 16;
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public static boolean isOfferingGadget(EntityGadget gadget) {
        return resolveOfferingId(gadget) > 0
                || (gadget != null
                        && gadget.getGadgetData() != null
                        && gadget.getGadgetData().getType()
                                == emu.grasscutter.game.props.EntityType.OfferingGadget);
    }

    public static int getLevel(Player player, int offeringId) {
        if (player == null || offeringId <= 0) {
            return 0;
        }
        ConcurrentHashMap<Integer, Integer> map =
                LEVELS.computeIfAbsent(player.getUid(), u -> loadPlayerLevels(player));
        return map.getOrDefault(offeringId, 0);
    }

    public static void setLevel(Player player, int offeringId, int level) {
        if (player == null || offeringId <= 0) {
            return;
        }
        int lv = Math.max(0, level);
        LEVELS.computeIfAbsent(player.getUid(), u -> new ConcurrentHashMap<>()).put(offeringId, lv);
        try {
            player.getQuestGlobalVariables().put(qgvKey(offeringId), lv);
            player.save();
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("OfferingHelper qgv save: {}", t.toString());
        }
        savePlayerProgress(player.getUid());
    }

    private static java.util.Set<Integer> getTakenSet(Player player, int offeringId) {
        ensureProgressLoaded(player);
        return TAKEN.computeIfAbsent(player.getUid(), u -> new ConcurrentHashMap<>())
                .computeIfAbsent(offeringId, o -> java.util.concurrent.ConcurrentHashMap.newKeySet());
    }

    private static void markTaken(Player player, int offeringId, int level) {
        getTakenSet(player, offeringId).add(level);
        savePlayerProgress(player.getUid());
    }

    private static void ensureProgressLoaded(Player player) {
        if (player == null) {
            return;
        }
        LEVELS.computeIfAbsent(player.getUid(), u -> loadPlayerLevels(player));
        TAKEN.computeIfAbsent(player.getUid(), u -> loadPlayerTaken(player));
    }

    private static synchronized ConcurrentHashMap<Integer, Integer> loadPlayerLevels(Player player) {
        ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
        try {
            var offeringIds = new java.util.HashSet<>(GADGET_TO_OFFERING.values());
            LEVEL_ROWS.values().forEach(row -> offeringIds.add(row.offeringId));
            for (int oid : offeringIds) {
                Integer v = player.getQuestGlobalVariables().get(qgvKey(oid));
                if (v != null && v > 0) {
                    map.put(oid, v);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            ensureLevelsFile();
            if (java.nio.file.Files.exists(LEVELS_FILE)) {
                String json = java.nio.file.Files.readString(LEVELS_FILE);
                var root = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
                String uidKey = String.valueOf(player.getUid());
                if (root.has(uidKey) && root.get(uidKey).isJsonObject()) {
                    var o = root.getAsJsonObject(uidKey);
                    for (var e : o.entrySet()) {
                        if (!e.getKey().matches("\\d+") || !e.getValue().isJsonPrimitive()) {
                            continue;
                        }
                        int oid = Integer.parseInt(e.getKey());
                        int lv = e.getValue().getAsInt();
                        map.merge(oid, lv, Math::max);
                    }
                }
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("OfferingHelper load json: {}", t.toString());
        }
        try {
            for (var e : map.entrySet()) {
                player.getQuestGlobalVariables().put(qgvKey(e.getKey()), e.getValue());
            }
        } catch (Throwable ignored) {
        }
        return map;
    }

    private static synchronized ConcurrentHashMap<Integer, java.util.Set<Integer>> loadPlayerTaken(Player player) {
        ConcurrentHashMap<Integer, java.util.Set<Integer>> map = new ConcurrentHashMap<>();
        try {
            ensureLevelsFile();
            if (!java.nio.file.Files.exists(LEVELS_FILE)) {
                return map;
            }
            String json = java.nio.file.Files.readString(LEVELS_FILE);
            var root = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
            String uidKey = String.valueOf(player.getUid());
            if (!root.has(uidKey) || !root.get(uidKey).isJsonObject()) {
                return map;
            }
            var o = root.getAsJsonObject(uidKey);
            if (!o.has("taken") || !o.get("taken").isJsonObject()) {
                return map;
            }
            for (var e : o.getAsJsonObject("taken").entrySet()) {
                int oid = Integer.parseInt(e.getKey());
                var set = java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
                if (e.getValue().isJsonArray()) {
                    for (var el : e.getValue().getAsJsonArray()) {
                        set.add(el.getAsInt());
                    }
                }
                map.put(oid, set);
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("OfferingHelper load taken: {}", t.toString());
        }
        return map;
    }

    private static synchronized void savePlayerProgress(int uid) {
        try {
            ensureLevelsFile();
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            if (java.nio.file.Files.exists(LEVELS_FILE)) {
                String json = java.nio.file.Files.readString(LEVELS_FILE);
                if (!json.isBlank()) {
                    root = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
                }
            }
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            ConcurrentHashMap<Integer, Integer> map = LEVELS.get(uid);
            if (map != null) {
                for (var e : map.entrySet()) {
                    o.addProperty(String.valueOf(e.getKey()), e.getValue());
                }
            }
            ConcurrentHashMap<Integer, java.util.Set<Integer>> takenMap = TAKEN.get(uid);
            if (takenMap != null && !takenMap.isEmpty()) {
                com.google.gson.JsonObject takenObj = new com.google.gson.JsonObject();
                for (var e : takenMap.entrySet()) {
                    com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                    e.getValue().stream().sorted().forEach(arr::add);
                    takenObj.add(String.valueOf(e.getKey()), arr);
                }
                o.add("taken", takenObj);
            }
            root.add(String.valueOf(uid), o);
            java.nio.file.Files.writeString(
                    LEVELS_FILE, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("OfferingHelper save json failed: {}", t.toString());
        }
    }

    private static void ensureLevelsFile() throws java.io.IOException {
        java.nio.file.Path dir = LEVELS_FILE.getParent();
        if (dir != null && !java.nio.file.Files.exists(dir)) {
            java.nio.file.Files.createDirectories(dir);
        }
        if (!java.nio.file.Files.exists(LEVELS_FILE)) {
            java.nio.file.Files.writeString(LEVELS_FILE, "{}");
        }
    }

    public static OfferingInfo buildOfferingInfo(int offeringId) {
        return OfferingInfo.newBuilder().setOfferingId(offeringId).build();
    }

    /**
     * The offering packets are still assembled field by field (so retcode 0 is always on the wire),
     * but every field number comes from the current generated classes; they used to be pinned to a
     * 7.0 capture, which a 7.1 client reads as different fields.
     */
    private static byte[] wireOfferingData(Player player, int offeringId, boolean isNewMaxLevel) {
        ensureProgressLoaded(player);
        int level = getLevel(player, offeringId);
        List<Integer> taken = new ArrayList<>(getTakenSet(player, offeringId));
        taken.sort(Integer::compareTo);
        return PlayerOfferingData.newBuilder()
                .setIsNewMaxLevel(isNewMaxLevel)
                .addAllTakenLevelRewardList(taken)
                .setIsFirstInteract(level <= 0)
                .setOfferingId(offeringId)
                .setLevel(level)
                .build()
                .toByteArray();
    }

    private static byte[] wireItemParam(int itemId, int count) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProtoWire.writeUint32(out, 1, itemId);
        ProtoWire.writeUint32(out, 2, count);
        return out.toByteArray();
    }

    /**
     * Sends unlocked offering progress on login; without it the client does not consider the
     * offering available and never shows the interaction UI.
     */
    public static void onPlayerLogin(Player player) {
        if (player == null || player.getSession() == null) {
            return;
        }
        try {
            synchronized (player) {
                if (player.getProgressManager().getOpenState(OPEN_STATE_ORAIONOKAMI) == 0) {
                    player.getProgressManager().forceSetOpenState(OPEN_STATE_ORAIONOKAMI, 1);
                }
                ensureLevelConfigs();
                LEVELS.put(player.getUid(), loadPlayerLevels(player));
                if (levelsLoaded) {
                    writeTpsStamina(player, getTpsStaminaLimit(player, 0, 0));
                    sendTpsStamina(player);
                }
                syncOfferingNotify(player);
                sendChainLevelSnapshot(player);
                Grasscutter.getLogger()
                        .debug(
                                "OfferingHelper login notify uid={} sakuraLv={} notifyOp={}",
                                player.getUid(),
                                getLevel(player, OFFERING_ORAIONOKAMI),
                                PacketOpcodes.PlayerOfferingDataNotify);
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("OfferingHelper onPlayerLogin failed: {}", t.toString());
        }
    }

    private static boolean isOfferingOpen(Player player, int offeringId) {
        int openStateId =
                switch (offeringId) {
                    case 1 -> 1406; // OPEN_STATE_SNOW_MOUNTAIN_ELDER_TREE
                    case OFFERING_ORAIONOKAMI -> OPEN_STATE_ORAIONOKAMI;
                    default -> 0;
                };
        if (openStateId == 0) {
            // Remaining offerings: open by default for exploration on a private server.
            return true;
        }
        return player.getProgressManager().getOpenState(openStateId) > 0;
    }

    /**
     * The player interacts with an offering: reply with GadgetInteractRsp plus PlayerOfferingRsp to
     * open the offering page.
     *
     * @return true when handled
     */
    public static boolean tryInteract(Player player, EntityGadget gadget) {
        if (player == null || gadget == null) {
            return false;
        }
        int offeringId = resolveOfferingId(gadget);
        if (offeringId <= 0 && !isOfferingGadget(gadget)) {
            return false;
        }
        if (offeringId <= 0) {
            offeringId = OFFERING_ORAIONOKAMI;
        }

        try {
            if (offeringId == OFFERING_ORAIONOKAMI
                    && player.getProgressManager().getOpenState(OPEN_STATE_ORAIONOKAMI) == 0) {
                player.getProgressManager().forceSetOpenState(OPEN_STATE_ORAIONOKAMI, 1);
            }
        } catch (Throwable ignored) {
        }

        try {
            player.sendPacket(
                    new emu.grasscutter.server.packet.send.PacketGadgetInteractRsp(
                            gadget,
                            emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType
                                    .InteractType_INTERACT_UI_INTERACT));
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("OfferingHelper interact rsp: {}", t.toString());
        }

        sendOfferingRsp(player, offeringId, Retcode.RET_SUCC_VALUE, null, null);
        syncOfferingNotify(player);

        Grasscutter.getLogger()
                .debug(
                        "OfferingHelper interact uid={} gadget={} offeringId={} lv={}",
                        player.getUid(),
                        gadget.getGadgetId(),
                        offeringId,
                        getLevel(player, offeringId));
        return true;
    }

    /** 按配置升级，自动奖励先过背包检查再扣材料。 */
    public static boolean tryLevelUp(Player player, int offeringId) {
        return tryLevelUp(player, offeringId, null);
    }

    public static boolean handleOfferingRequest(
            Player player, byte[] payload, byte[] requestHeader) {
        if (player == null) return false;
        int offeringId;
        try {
            offeringId =
                    PlayerOfferingReq.parseFrom(payload == null ? new byte[0] : payload)
                            .getOfferingId();
        } catch (com.google.protobuf.InvalidProtocolBufferException invalid) {
            sendOfferingRsp(player, 0, Retcode.RET_SVR_ERROR_VALUE, null, requestHeader);
            return true;
        }
        return tryLevelUp(player, offeringId, requestHeader);
    }

    public static boolean tryLevelUp(Player player, int offeringId, byte[] requestHeader) {
        if (player == null) return false;
        synchronized (player) {
            ensureLevelConfigs();
            if (!levelsLoaded
                    || !isKnownOfferingId(offeringId)
                    || !isOfferingOpen(player, offeringId)) {
                sendOfferingRsp(
                        player, 0, Retcode.RET_OFFERING_NOT_OPEN_VALUE, null, requestHeader);
                return true;
            }
            ensureProgressLoaded(player);
            UpgradePlan plan;
            var inventory = player.getInventory();
            try {
                synchronized (inventory) {
                    plan = planUpgrade(player, offeringId);
                    if (plan.rows.isEmpty()) {
                        sendOfferingRsp(player, offeringId, plan.failure, null, requestHeader);
                        return true;
                    }
                    Runnable commit = () -> commitUpgrade(player, offeringId, plan);
                    List<GameItem> items = materializeRewards(plan.bonuses);
                    if (items.isEmpty()) {
                        if (!inventory.payItems(plan.costs, 1, ActionReason.OfferingItem)) {
                            sendOfferingRsp(
                                    player,
                                    offeringId,
                                    Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE,
                                    null,
                                    requestHeader);
                            return true;
                        }
                        commit.run();
                    } else {
                        // 奖励先过背包检查，再扣材料并记录本次升级。
                        var result =
                                inventory.addItems(
                                        items,
                                        ActionReason.OfferingItem,
                                        InventoryAddPolicy.ALL_OR_NOTHING,
                                        () ->
                                                inventory.payItems(
                                                        plan.costs, 1, ActionReason.OfferingItem),
                                        commit);
                        if (!result.allAccepted()) {
                            int error =
                                    result.entries().stream()
                                                    .anyMatch(
                                                            entry ->
                                                                    entry.status()
                                                                            == InventoryAddResult
                                                                                    .Status
                                                                                    .AUTHORIZATION_FAILED)
                                            ? Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE
                                            : Retcode.RET_ITEM_EXCEED_LIMIT_VALUE;
                            sendOfferingRsp(player, offeringId, error, null, requestHeader);
                            return true;
                        }
                    }
                }
            } catch (RuntimeException failure) {
                Grasscutter.getLogger()
                        .warn(
                                "Offering upgrade failed uid={} offering={}",
                                player.getUid(),
                                offeringId,
                                failure);
                sendOfferingRsp(
                        player, offeringId, Retcode.RET_SVR_ERROR_VALUE, null, requestHeader);
                return true;
            }
            try {
                applyUpgradeActions(player, offeringId, plan);
            } catch (RuntimeException failure) {
                Grasscutter.getLogger()
                        .warn(
                                "Offering action notification failed uid={} offering={}",
                                player.getUid(),
                                offeringId,
                                failure);
            }
            sendOfferingRsp(
                    player, offeringId, Retcode.RET_SUCC_VALUE, plan.bonuses, requestHeader, true);
            syncOfferingNotify(player);
            return true;
        }
    }

    private record UpgradePlan(
            int target,
            int failure,
            List<ItemParamData> costs,
            List<ItemParamData> bonuses,
            List<LevelRow> rows,
            int tpsStamina) {}

    private static UpgradePlan planUpgrade(Player player, int offeringId) {
        int target = getLevel(player, offeringId);
        int failure = Retcode.RET_OFFERING_LEVEL_LIMIT_VALUE;
        List<ItemParamData> costs = new ArrayList<>();
        Map<Integer, Integer> bonuses = new LinkedHashMap<>();
        List<LevelRow> rows = new ArrayList<>();
        int tpsStamina = 0;
        while (target < Integer.MAX_VALUE) {
            var row = LEVEL_ROWS.get(levelKey(offeringId, target + 1));
            if (row == null) break;
            if (!row.supported) {
                failure = Retcode.RET_SVR_ERROR_VALUE;
                break;
            }
            var candidateCosts = new ArrayList<>(costs);
            candidateCosts.addAll(row.costs);
            if (!player.getInventory().canPayItems(candidateCosts, 1)) {
                failure = Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE;
                break;
            }
            var candidateBonuses = new LinkedHashMap<>(bonuses);
            int candidateTpsStamina = tpsStamina;
            try {
                if (row.automatic && row.rewardId > 0) {
                    var reward = GameData.getRewardDataMap().get(row.rewardId);
                    if (reward == null || reward.getAllRewardItems().isEmpty())
                        throw new IllegalArgumentException("Missing automatic offering reward");
                    for (var item : reward.getAllRewardItems())
                        addBonus(candidateBonuses, item.getId(), item.getCount());
                }
                for (var action : row.actions) {
                    if (action.type == OfferingActionType.IMPROVE_TPS_STAMINA) {
                        candidateTpsStamina = getTpsStaminaLimit(player, offeringId, row.level);
                    } else if (action.type == OfferingActionType.SET_GADGET_CHAIN_LEVEL) {
                        var config = gadgetChains.get(action.first);
                        if (config == null || action.second > config.maximum)
                            throw new IllegalArgumentException("Missing gadget chain config");
                    } else if (action.type == OfferingActionType.GRANT_UNIQUE_ITEM
                            && player.getInventory().getItemCountById(action.first) == 0
                            && !candidateBonuses.containsKey(action.first)) {
                        addBonus(candidateBonuses, action.first, 1);
                    }
                }
            } catch (IllegalArgumentException | ArithmeticException invalid) {
                failure = Retcode.RET_SVR_ERROR_VALUE;
                break;
            }
            costs = candidateCosts;
            bonuses = candidateBonuses;
            tpsStamina = candidateTpsStamina;
            rows.add(row);
            target++;
        }
        var rewards =
                bonuses.entrySet().stream()
                        .map(entry -> new ItemParamData(entry.getKey(), entry.getValue()))
                        .toList();
        return new UpgradePlan(target, failure, costs, rewards, rows, tpsStamina);
    }

    private static int getTpsStaminaLimit(Player player, int offeringId, int level) {
        int stamina = TpsAvatarSystem.BASE_STAMINA;
        for (var row : LEVEL_ROWS.values()) {
            int completed = row.offeringId == offeringId ? level : getLevel(player, row.offeringId);
            if (!row.supported || row.level > completed) continue;
            for (var action : row.actions) {
                if (action.type == OfferingActionType.IMPROVE_TPS_STAMINA)
                    stamina = Math.addExact(stamina, Math.multiplyExact(action.first, 100));
            }
        }
        if (stamina > PlayerProperty.PROP_MAX_TPS_STAMINA.getMax())
            throw new IllegalArgumentException("Invalid offering TPS stamina limit");
        return stamina;
    }

    private static void writeTpsStamina(Player player, int maximum) {
        var properties = player.getProperties();
        int previous = properties.getOrDefault(PlayerProperty.PROP_MAX_TPS_STAMINA.getId(), 0);
        int current =
                properties.getOrDefault(PlayerProperty.PROP_CUR_PERSIST_TPS_STAMINA.getId(), 0);
        // 老存档首次补齐；重复同步和供奉升级不会额外回满体力。
        properties.put(PlayerProperty.PROP_MAX_TPS_STAMINA.getId(), maximum);
        properties.put(
                PlayerProperty.PROP_CUR_PERSIST_TPS_STAMINA.getId(),
                previous <= 0
                        ? TpsAvatarSystem.BASE_STAMINA
                        : Math.max(0, Math.min(current, maximum)));
    }

    private static void sendTpsStamina(Player player) {
        player.sendPacket(new PacketPlayerPropNotify(player, PlayerProperty.PROP_MAX_TPS_STAMINA));
        player.sendPacket(
                new PacketPlayerPropNotify(player, PlayerProperty.PROP_CUR_PERSIST_TPS_STAMINA));
    }

    private static void addBonus(Map<Integer, Integer> bonuses, int itemId, int count) {
        if (itemId <= 0 || count <= 0 || GameData.getItemDataMap().get(itemId) == null)
            throw new IllegalArgumentException("Invalid offering bonus");
        bonuses.merge(itemId, count, Math::addExact);
    }

    private static List<GameItem> materializeRewards(List<ItemParamData> rewards) {
        List<GameItem> items = new ArrayList<>();
        for (var item : rewards)
            items.addAll(
                    InventoryGrantBuilder.create(
                            GameData.getItemDataMap().get(item.getId()), item.getCount(), 1));
        return items;
    }

    private static void commitUpgrade(Player player, int offeringId, UpgradePlan plan) {
        var taken = getTakenSet(player, offeringId);
        for (var row : plan.rows) {
            if (row.automatic) taken.add(row.level);
            for (var action : row.actions) {
                if (action.type == OfferingActionType.SET_QUEST_GLOBAL_VAR)
                    player.getQuestGlobalVariables().put(action.first, action.second);
            }
        }
        if (plan.tpsStamina > 0) writeTpsStamina(player, plan.tpsStamina);
        // 自动奖励领取标记和任务变量随等级一起保存。
        setLevel(player, offeringId, plan.target);
    }

    private static void applyUpgradeActions(Player player, int offeringId, UpgradePlan plan) {
        if (plan.tpsStamina > 0) sendTpsStamina(player);
        Map<Integer, Integer> variables = new LinkedHashMap<>();
        Map<Integer, Integer> chains = new LinkedHashMap<>();
        var scene = player.getScene();
        var world = player.getWorld();
        boolean ownsScene =
                scene != null
                        && world != null
                        && world.getHost() != null
                        && world.getHost().getUid() == player.getUid();
        for (var row : plan.rows) {
            for (var action : row.actions) {
                switch (action.type) {
                    case NOTIFY_GROUP -> {
                        if (ownsScene)
                            scene.getScriptManager()
                                    .callEvent(
                                            new ScriptArgs(
                                                    action.first,
                                                    EventType.EVENT_OFFERING_LEVELUP,
                                                    offeringId,
                                                    row.level));
                    }
                    case SET_GADGET_CHAIN_LEVEL -> chains.put(action.first, action.second);
                    case SET_QUEST_GLOBAL_VAR -> variables.put(action.first, action.second);
                    case GRANT_UNIQUE_ITEM -> {} // 道具已通过同一批背包发放。
                    case IMPROVE_TPS_STAMINA -> {} // 属性已随供奉等级保存。
                }
            }
        }
        variables.forEach(
                (id, value) -> player.getQuestManager().triggerQuestGlobalVarAction(id, value));
        if (!chains.isEmpty()) {
            var change =
                    GadgetChainLevelChangeNotify.newBuilder().putAllGadgetChainLevelMap(chains);
            sendMessage(player, PacketOpcodes.GadgetChainLevelChangeNotify, change.build());
            if (ownsScene) {
                var packet = new BasePacket(PacketOpcodes._SceneOwnerGadgetChainLevelNotify);
                packet.setData(
                        SceneOwnerGadgetChainLevelNotify.newBuilder()
                                .putAllGadgetChainLevelMap(chains));
                world.broadcastPacket(packet);
            }
        }
    }

    public static Map<Integer, Integer> getGadgetChainLevels(Player player) {
        ensureLevelConfigs();
        Map<Integer, Integer> levels = new LinkedHashMap<>();
        if (player == null || !levelsLoaded) return levels;
        LEVEL_ROWS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry -> {
                            var row = entry.getValue();
                            for (var action : row.actions) {
                                if (action.type != OfferingActionType.SET_GADGET_CHAIN_LEVEL)
                                    continue;
                                var config = gadgetChains.get(action.first);
                                if (config == null || action.second > config.maximum) continue;
                                levels.putIfAbsent(action.first, config.initial);
                                if (row.level <= getLevel(player, row.offeringId))
                                    levels.put(action.first, action.second);
                            }
                        });
        return levels;
    }

    private static void sendChainLevelSnapshot(Player player) {
        var levels = getGadgetChainLevels(player);
        if (!levels.isEmpty())
            sendMessage(
                    player,
                    PacketOpcodes.GadgetChainLevelUpdateNotify,
                    GadgetChainLevelUpdateNotify.newBuilder()
                            .putAllGadgetChainLevelMap(levels)
                            .build());
    }

    public static void sendSceneOwnerChainLevels(Player player, boolean entering) {
        if (player == null || player.getWorld() == null || player.getWorld().getHost() == null)
            return;
        sendMessage(
                player,
                PacketOpcodes._SceneOwnerGadgetChainLevelNotify,
                SceneOwnerGadgetChainLevelNotify.newBuilder()
                        .setIsEnterWorld(entering)
                        .putAllGadgetChainLevelMap(
                                getGadgetChainLevels(player.getWorld().getHost()))
                        .build());
    }

    private static void sendMessage(
            Player player, int opcode, com.google.protobuf.MessageLite message) {
        var packet = new BasePacket(opcode);
        packet.setData(message);
        player.sendPacket(packet);
    }

    /**
     * Claims one tier from the reward screen. TakeOfferingLevelRewardReq: offering_id=1, level=10.
     */
    public static boolean handleRewardRequest(Player player, byte[] payload, byte[] requestHeader) {
        if (player == null) return false;
        try {
            var request =
                    TakeOfferingLevelRewardReq.parseFrom(payload == null ? new byte[0] : payload);
            return tryTakeLevelReward(
                    player, request.getOfferingId(), request.getLevel(), requestHeader);
        } catch (com.google.protobuf.InvalidProtocolBufferException invalid) {
            sendTakeRewardRsp(player, 0, 0, Retcode.RET_SVR_ERROR_VALUE, null, requestHeader);
            return true;
        }
    }

    public static boolean tryTakeLevelReward(
            Player player, int offeringId, int takeLevel, byte[] requestHeader) {
        if (player == null) return false;
        synchronized (player) {
            ensureLevelConfigs();
            if (!levelsLoaded
                    || !isKnownOfferingId(offeringId)
                    || !isOfferingOpen(player, offeringId)) {
                sendTakeRewardRsp(
                        player,
                        offeringId,
                        takeLevel,
                        Retcode.RET_OFFERING_NOT_OPEN_VALUE,
                        null,
                        requestHeader);
                return true;
            }
            ensureProgressLoaded(player);
            if (takeLevel <= 0 || takeLevel > getLevel(player, offeringId)) {
                sendTakeRewardRsp(
                        player,
                        offeringId,
                        takeLevel,
                        Retcode.RET_OFFERING_LEVEL_NOT_REACH_VALUE,
                        null,
                        requestHeader);
                return true;
            }
            if (getTakenSet(player, offeringId).contains(takeLevel)) {
                sendTakeRewardRsp(
                        player,
                        offeringId,
                        takeLevel,
                        Retcode.RET_OFFERING_LEVEL_HAS_TAKEN_VALUE,
                        null,
                        requestHeader);
                return true;
            }
            var row = LEVEL_ROWS.get(levelKey(offeringId, takeLevel));
            var reward = row == null ? null : GameData.getRewardDataMap().get(row.rewardId);
            List<ItemParamData> granted = reward == null ? List.of() : reward.getAllRewardItems();
            List<GameItem> items = new ArrayList<>();
            try {
                if (granted.isEmpty())
                    throw new IllegalArgumentException("Missing offering reward");
                for (var item : granted) {
                    if (item == null || item.getId() <= 0 || item.getCount() <= 0)
                        throw new IllegalArgumentException("Invalid offering reward");
                    items.addAll(
                            InventoryGrantBuilder.create(
                                    GameData.getItemDataMap().get(item.getId()),
                                    item.getCount(),
                                    1));
                }
            } catch (IllegalArgumentException invalid) {
                sendTakeRewardRsp(
                        player,
                        offeringId,
                        takeLevel,
                        Retcode.RET_SVR_ERROR_VALUE,
                        null,
                        requestHeader);
                return true;
            }
            // 背包拒收时保留未领取状态，允许玩家整理后重试。
            if (!player.getInventory().addItems(items, ActionReason.OfferingItem).allAccepted()) {
                sendTakeRewardRsp(
                        player,
                        offeringId,
                        takeLevel,
                        Retcode.RET_ITEM_EXCEED_LIMIT_VALUE,
                        null,
                        requestHeader);
                return true;
            }
            markTaken(player, offeringId, takeLevel);
            sendTakeRewardRsp(
                    player, offeringId, takeLevel, Retcode.RET_SUCC_VALUE, granted, requestHeader);
            syncOfferingNotify(player);
            return true;
        }
    }

    private static void sendTakeRewardRsp(
            Player player,
            int offeringId,
            int takeLevel,
            int retcode,
            java.util.List<emu.grasscutter.data.common.ItemParamData> items,
            byte[] requestHeader) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ProtoWire.writeUint32(out, TakeOfferingLevelRewardRsp.OFFERING_ID_FIELD_NUMBER, offeringId);
            ProtoWire.writeUint32(out, TakeOfferingLevelRewardRsp.TAKE_LEVEL_FIELD_NUMBER, takeLevel);
            ProtoWire.writeUint32Force(out, TakeOfferingLevelRewardRsp.RETCODE_FIELD_NUMBER, retcode);
            if (items != null) {
                for (var it : items) {
                    if (it == null || it.getId() <= 0 || it.getCount() <= 0) {
                        continue;
                    }
                    ProtoWire.writeBytes(
                            out,
                            TakeOfferingLevelRewardRsp.ITEM_LIST_FIELD_NUMBER,
                            wireItemParam(it.getId(), it.getCount()));
                }
            }
            sendRaw(player, PacketOpcodes.TakeOfferingLevelRewardRsp, out.toByteArray(), requestHeader);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("OfferingHelper sendTakeRewardRsp: {}", t.toString());
        }
    }

    /**
     * Fallback for when no handler is registered: accept only the known PlayerOfferingReq opcode so
     * other packets are not intercepted by mistake.
     */
    public static boolean tryHandleUnknownOfferingReq(Player player, int opcode, byte[] payload) {
        return tryHandleUnknownOfferingReq(player, opcode, payload, null);
    }

    public static boolean tryHandleUnknownOfferingReq(
            Player player, int opcode, byte[] payload, byte[] requestHeader) {
        if (player == null || opcode <= 0) return false;
        if (opcode == PacketOpcodes.TakeOfferingLevelRewardReq)
            return handleRewardRequest(player, payload, requestHeader);
        if (opcode == PacketOpcodes.PlayerOfferingReq)
            return handleOfferingRequest(player, payload, requestHeader);
        return false;
    }

    private static boolean isKnownOfferingId(int id) {
        return id > 0 && LEVEL_ROWS.values().stream().anyMatch(row -> row.offeringId == id);
    }

    /** Full sync of offering progress. */
    public static void syncOfferingNotify(Player player) {
        if (player == null || player.getSession() == null) {
            return;
        }
        try {
            ensureLevelConfigs();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean hasSakura = false;
            var offeringIds = new java.util.TreeSet<>(GADGET_TO_OFFERING.values());
            LEVEL_ROWS.values().forEach(row -> offeringIds.add(row.offeringId));
            for (int offeringId : offeringIds) {
                if (!isOfferingOpen(player, offeringId)) {
                    continue;
                }
                if (offeringId == OFFERING_ORAIONOKAMI) {
                    hasSakura = true;
                }
                ProtoWire.writeBytes(
                        out,
                        PlayerOfferingDataNotify.OFFERING_DATA_LIST_FIELD_NUMBER,
                        wireOfferingData(player, offeringId, false));
            }
            if (!hasSakura) {
                ProtoWire.writeBytes(
                        out,
                        PlayerOfferingDataNotify.OFFERING_DATA_LIST_FIELD_NUMBER,
                        wireOfferingData(player, OFFERING_ORAIONOKAMI, false));
            }
            sendRaw(player, PacketOpcodes.PlayerOfferingDataNotify, out.toByteArray(), null);
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("OfferingHelper syncOfferingNotify: {}", t.toString());
        }
    }

    private static void sendRaw(Player player, int opcode, byte[] payload, byte[] requestHeader) {
        BasePacket pkt = new BasePacket(opcode);
        if (requestHeader != null && requestHeader.length > 0) {
            pkt.setHeader(requestHeader);
        }
        pkt.setData(payload);
        player.sendPacket(pkt);
    }

    private static void sendOfferingRsp(
            Player player,
            int offeringId,
            int retcode,
            java.util.List<emu.grasscutter.data.common.ItemParamData> items,
            byte[] requestHeader) {
        sendOfferingRsp(player, offeringId, retcode, items, requestHeader, false);
    }

    private static void sendOfferingRsp(
            Player player,
            int offeringId,
            int retcode,
            java.util.List<emu.grasscutter.data.common.ItemParamData> items,
            byte[] requestHeader,
            boolean isNewMaxLevel) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (items != null) {
                for (var it : items) {
                    if (it == null || it.getId() <= 0 || it.getCount() <= 0) {
                        continue;
                    }
                    ProtoWire.writeBytes(
                            out,
                            PlayerOfferingRsp.ITEM_LIST_FIELD_NUMBER,
                            wireItemParam(it.getId(), it.getCount()));
                }
            }
            if (offeringId > 0) {
                ProtoWire.writeBytes(
                        out,
                        PlayerOfferingRsp.OFFERING_DATA_FIELD_NUMBER,
                        wireOfferingData(player, offeringId, isNewMaxLevel));
            }
            // retcode is always written out, including 0
            ProtoWire.writeUint32Force(out, PlayerOfferingRsp.RETCODE_FIELD_NUMBER, retcode);
            sendRaw(player, PacketOpcodes.PlayerOfferingRsp, out.toByteArray(), requestHeader);
            Grasscutter.getLogger()
                    .debug(
                            "OfferingHelper Rsp uid={} offeringId={} level={} ret={} items={}"
                                + " op={}",
                            player.getUid(),
                            offeringId,
                            getLevel(player, offeringId),
                            retcode,
                            items == null ? 0 : items.size(),
                            PacketOpcodes.PlayerOfferingRsp);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("OfferingHelper sendOfferingRsp: {}", t.toString());
        }
    }

    private static void sendOfferingRsp(
            Player player,
            int offeringId,
            int retcode,
            java.util.List<emu.grasscutter.data.common.ItemParamData> items) {
        sendOfferingRsp(player, offeringId, retcode, items, null, false);
    }

    private static final ConcurrentHashMap<Long, LevelRow> LEVEL_ROWS = new ConcurrentHashMap<>();
    private static volatile boolean levelsLoaded = false;

    private static long levelKey(int offeringId, int level) {
        return ((long) offeringId << 32) | Integer.toUnsignedLong(level);
    }

    private enum OfferingActionType {
        NOTIFY_GROUP,
        SET_GADGET_CHAIN_LEVEL,
        GRANT_UNIQUE_ITEM,
        SET_QUEST_GLOBAL_VAR,
        IMPROVE_TPS_STAMINA
    }

    private record OfferingAction(OfferingActionType type, int first, int second) {}

    private record ChainConfig(int initial, int maximum) {}

    private static volatile Map<Integer, ChainConfig> gadgetChains = Map.of();

    private static final class LevelRow {
        final int offeringId;
        final int level;
        final int rewardId;
        final List<ItemParamData> costs;
        final boolean supported;
        final boolean automatic;
        final List<OfferingAction> actions;

        LevelRow(
                int offeringId,
                int level,
                int rewardId,
                List<ItemParamData> costs,
                boolean supported,
                boolean automatic,
                List<OfferingAction> actions) {
            this.offeringId = offeringId;
            this.level = level;
            this.rewardId = rewardId;
            this.costs = List.copyOf(costs);
            this.supported = supported;
            this.automatic = automatic;
            this.actions = List.copyOf(actions);
        }
    }

    private static OfferingAction parseAction(com.google.gson.JsonObject entry) {
        var type =
                OfferingActionType.valueOf(
                        entry.get("actionType")
                                .getAsString()
                                .replaceFirst("^OFFERING_ACTION_", ""));
        String[] parts =
                entry.has("param")
                        ? entry.get("param").getAsString().split(",", -1)
                        : new String[0];
        int expected =
                type == OfferingActionType.SET_GADGET_CHAIN_LEVEL
                                || type == OfferingActionType.SET_QUEST_GLOBAL_VAR
                        ? 2
                        : 1;
        if (parts.length != expected)
            throw new IllegalArgumentException("Invalid offering action arguments");
        int first = Integer.parseInt(parts[0].trim());
        int second = expected == 2 ? Integer.parseInt(parts[1].trim()) : 0;
        if (first <= 0 || (type == OfferingActionType.SET_GADGET_CHAIN_LEVEL && second < 0))
            throw new IllegalArgumentException("Invalid offering action values");
        return new OfferingAction(type, first, second);
    }

    private static Map<Integer, ChainConfig> parseGadgetChains(com.google.gson.JsonArray array) {
        Map<Integer, ChainConfig> chains = new LinkedHashMap<>();
        for (var element : array) {
            var row = element.getAsJsonObject();
            int id = row.get("chainId").getAsInt();
            int initial = row.has("initLevel") ? row.get("initLevel").getAsInt() : 0;
            int maximum = row.has("maxLevel") ? row.get("maxLevel").getAsInt() : 0;
            if (id <= 0
                    || initial < 0
                    || maximum < initial
                    || maximum <= 0
                    || chains.putIfAbsent(id, new ChainConfig(initial, maximum)) != null)
                throw new IllegalArgumentException("Invalid gadget chain config");
        }
        return Map.copyOf(chains);
    }

    private static Map<Long, LevelRow> parseLevelConfigs(com.google.gson.JsonArray array) {
        Map<Long, LevelRow> rows = new LinkedHashMap<>();
        for (var element : array) {
            var object = element.getAsJsonObject();
            int id = object.has("offeringId") ? object.get("offeringId").getAsInt() : 0;
            int level = object.has("level") ? object.get("level").getAsInt() : 0;
            if (id <= 0 || level <= 0) continue;
            int rewardId = object.has("rewardId") ? object.get("rewardId").getAsInt() : 0;
            List<ItemParamData> costs = new ArrayList<>();
            boolean supported =
                    rewardId >= 0
                            && object.has("consumeItemConfigVec")
                            && object.get("consumeItemConfigVec").isJsonArray();
            if (supported) {
                for (var cost : object.getAsJsonArray("consumeItemConfigVec")) {
                    if (!cost.isJsonObject()) {
                        supported = false;
                        break;
                    }
                    var entry = cost.getAsJsonObject();
                    int itemId = entry.has("id") ? entry.get("id").getAsInt() : 0;
                    int count = entry.has("count") ? entry.get("count").getAsInt() : 0;
                    if (itemId == 0 && count == 0) continue;
                    if (itemId <= 0 || count <= 0) {
                        supported = false;
                        break;
                    }
                    costs.add(new ItemParamData(itemId, count));
                }
            }
            // 忍冬之树初始等级免费；其余缺少消耗的配置不能直接升级。
            if (costs.isEmpty() && !(id == 1 && level == 1)) supported = false;
            boolean automatic =
                    object.has("isAutoTakeReward") && object.get("isAutoTakeReward").getAsBoolean();
            List<OfferingAction> actions = new ArrayList<>();
            if (object.has("condVec")) {
                for (var action : object.getAsJsonArray("condVec")) {
                    if (!action.isJsonObject()) {
                        supported = false;
                        break;
                    }
                    var entry = action.getAsJsonObject();
                    if (entry.has("actionType")
                            && !entry.get("actionType").getAsString().isBlank()) {
                        try {
                            actions.add(parseAction(entry));
                        } catch (IllegalArgumentException invalid) {
                            supported = false;
                        }
                    }
                }
            }
            if (rows.putIfAbsent(
                            levelKey(id, level),
                            new LevelRow(id, level, rewardId, costs, supported, automatic, actions))
                    != null)
                throw new IllegalArgumentException("Duplicate offering level " + id + ":" + level);
        }
        return rows;
    }

    private static void ensureLevelConfigs() {
        if (levelsLoaded) return;
        synchronized (LEVEL_ROWS) {
            if (levelsLoaded) return;
            try {
                var path =
                        emu.grasscutter.utils.FileUtils.getExcelPath(
                                "OfferingLevelUpExcelConfigData.json");
                var rows =
                        parseLevelConfigs(
                                com.google.gson.JsonParser.parseString(
                                                java.nio.file.Files.readString(path))
                                        .getAsJsonArray());
                var chainPath =
                        emu.grasscutter.utils.FileUtils.getExcelPath(
                                "GadgetChainExcelConfigData.json");
                gadgetChains =
                        java.nio.file.Files.exists(chainPath)
                                ? parseGadgetChains(
                                        com.google.gson.JsonParser.parseString(
                                                        java.nio.file.Files.readString(chainPath))
                                                .getAsJsonArray())
                                : Map.of();
                LEVEL_ROWS.clear();
                LEVEL_ROWS.putAll(rows);
                levelsLoaded = true;
            } catch (Exception exception) {
                Grasscutter.getLogger()
                        .warn("Offering levels could not be loaded: {}", exception.toString());
            }
        }
    }
}
