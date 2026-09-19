package emu.grasscutter.game.dps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.Utils;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 从 AttackResult 识别具体反应名。
 *
 * <p>月/星「视为反应伤害」的通用标记是能力配置里的 {@code attackTag}（如 {@code
 * MoonOvergrowDamage}），与角色无关。普通草/岩/雷技能没有该 tag，仍记元素伤害。
 */
public final class DPSReactionHelper {

    private static final Map<Integer, String> HASH_TO_REACTION = new HashMap<>();
    /** abilityName → 该能力 Damage 动作上出现过的月/星 attackTag。 */
    private static final Map<String, EnumSet<AttackTagKind>> ABILITY_ATTACK_TAGS =
            new ConcurrentHashMap<>();

    private static final AtomicBoolean ATTACK_TAG_INDEX_LOADED = new AtomicBoolean();
    private static final AtomicInteger PROBE_LEFT = new AtomicInteger(40);

    private enum AttackTagKind {
        MOON_BLOOM,
        MOON_SHOCK,
        MOON_CRYSTAL,
        STAR_SUPER,
        STAR_SWIRL
    }

    static {
        // 星
        mapHash("TeamAbility_StarSuperconductor", "星超导");
        mapHash("TeamAbility_StarSwirl_Ice", "星扩散");
        mapHash("Avatar_StarSuperconductor_Field_Checker", "星超导");
        mapHash("ElementalReaction_StarSuperconductor", "星超导");
        mapHash("ElementalReaction_StarSwirl_Ice", "星扩散");
        mapHash("Avatar_NewElementReaction_Preload_StarSuperconductor", "星超导");
        mapHash("Avatar_NewElementReaction_Preload_StarSwirl_Ice", "星扩散");
        for (int i = 1; i <= 12; i++) {
            mapHash(String.format("StarSuperconducted_Attack%02d", i), "星超导");
            mapHash(String.format("StarSuperconducted_Attack%d", i), "星超导");
            mapHash(String.format("Player_Ice_StarSuperconducted_Attack%02d", i), "星超导");
        }
        mapHash("StarSuperconducted_ExtraAttack", "星超导");
        mapHash("StarSuperconducted_PlungeAttack", "星超导");

        // 月
        mapHash("TeamAbility_MoonShock", "月感电");
        mapHash("TeamAbility_Reset_MoonOvergrow", "月绽放");
        mapHash("TeamAbility_MoonCrystal_Water", "月结晶");
        mapHash("TeamAbility_MoonPhase", "月感电");
        mapHash("ElementalReaction_MoonShock", "月感电");
        mapHash("ElementalReaction_MoonOvergrow", "月绽放");
        mapHash("ElementalReaction_MoonCrystallize_Water", "月结晶");
        mapHash("Avatar_NewElementReaction_Preload_MoonShock", "月感电");
        mapHash("Avatar_NewElementReaction_Preload_MoonOvergrow", "月绽放");
        mapHash("Avatar_NewElementReaction_Preload_MoonCrystallize_Water", "月结晶");

        // 经典转化/增幅（能力名路径）
        mapHash("ElementalReaction_Explode", "超载");
        mapHash("ElementalReaction_Superconductor", "超导");
        mapHash("ElementalReaction_Electric", "感电");
        mapHash("ElementalReaction_Stream", "感电");
        mapHash("ElementalReaction_Burning", "燃烧");
        mapHash("ElementalReaction_Swirl_Fire", "扩散");
        mapHash("ElementalReaction_Swirl_Water", "扩散");
        mapHash("ElementalReaction_Swirl_Electric", "扩散");
        mapHash("ElementalReaction_Swirl_Ice", "扩散");
        mapHash("ElementalReaction_FrozenBroken", "碎冰");
        mapHash("ElementalReaction_Overgrow", "绽放");
        mapHash("ElementalReaction_Overgrow_Mushroom_Electric", "超绽放");
        mapHash("ElementalReaction_Overgrow_Mushroom_Fire", "烈绽放");
        mapHash("ElementalReaction_Melt", "融化");
        mapHash("ElementalReaction_Steam", "蒸发");
        mapHash("ElementalReaction_Overdose_Electric", "超激化");
        mapHash("ElementalReaction_Overdose_Grass", "蔓激化");
    }

    private DPSReactionHelper() {}

    private static void mapHash(String abilityName, String reaction) {
        int h = Utils.abilityHash(abilityName);
        HASH_TO_REACTION.put(h, reaction);
        HASH_TO_REACTION.putIfAbsent((int) (h & 0xffffffffL), reaction);
    }

    public static String detect(AttackResult result, GameEntity attacker, ElementType element) {
        // 星超导：Field_Checker + 冰伤仍可归列。
        // 月系：只认能力 attackTag / 反应能力名，禁止把整队草/岩/雷都改成月反应。
        if (result == null) {
            String named = fromStarField(attacker, element);
            if (named == null) probeOnce(null, attacker, element, null);
            return named;
        }

        String abilityName = resolveAbilityName(attacker, result);

        String named = fromAbilityAttackTag(abilityName, element);
        if (named != null) return named;

        named = fromText(result.getAnimEventId());
        if (named != null) return upgradeBloomIfMoonTeam(named, attacker);

        named = fromText(abilityName);
        if (named != null) return upgradeBloomIfMoonTeam(named, attacker);

        named = fromHashes(collectAllInts(result));
        if (named != null) return upgradeBloomIfMoonTeam(named, attacker);

        if (result.hasAbilityIdentifier()) {
            named = fromHashes(collectAllInts(result.getAbilityIdentifier()));
            if (named != null) return upgradeBloomIfMoonTeam(named, attacker);
        }

        GameEntity owner = attacker;
        if (attacker instanceof EntityClientGadget gadget) {
            try {
                if (gadget.getScene() != null) {
                    owner = gadget.getScene().getEntityById(gadget.getOwnerEntityId());
                }
                String ownerAbility = resolveAbilityName(owner, result);
                named = fromAbilityAttackTag(ownerAbility, element);
                if (named != null) return named;
                named = fromText(ownerAbility);
                if (named != null) return upgradeBloomIfMoonTeam(named, attacker);
            } catch (Throwable ignored) {
            }
        }

        named = fromStarField(attacker, element);
        if (named != null) return named;
        if (owner != attacker) {
            named = fromStarField(owner, element);
            if (named != null) return named;
        }

        for (int candidate : collectAllInts(result)) {
            named = nameOfReactionId(candidate);
            if (named != null) return upgradeBloomIfMoonTeam(named, attacker);
        }

        Float amplifyRate = findAmplifyRate(result);
        if (amplifyRate != null && amplifyRate > 0.05f && amplifyRate < 5f) {
            named = amplifyByElement(element, amplifyRate);
            if (named != null) return named;
        }

        probeOnce(result, attacker, element, null);
        return null;
    }

    private static String fromHashes(int[] values) {
        for (int v : values) {
            String named = HASH_TO_REACTION.get(v);
            if (named != null) return named;
        }
        return null;
    }

    /** 按能力配置里的 attackTag 归列（全角色通用，不写死角色名）。 */
    private static String fromAbilityAttackTag(String abilityName, ElementType element) {
        if (abilityName == null || abilityName.isEmpty()) return null;
        ensureAttackTagIndex();
        EnumSet<AttackTagKind> tags = ABILITY_ATTACK_TAGS.get(abilityName);
        if (tags == null || tags.isEmpty()) return null;
        return pickReaction(tags, element);
    }

    private static String pickReaction(EnumSet<AttackTagKind> tags, ElementType element) {
        if (element == ElementType.Grass && tags.contains(AttackTagKind.MOON_BLOOM)) return "月绽放";
        if (element == ElementType.Electric && tags.contains(AttackTagKind.MOON_SHOCK)) return "月感电";
        if (element == ElementType.Rock && tags.contains(AttackTagKind.MOON_CRYSTAL)) return "月结晶";
        if (element == ElementType.Ice && tags.contains(AttackTagKind.STAR_SUPER)) return "星超导";
        if ((element == ElementType.Ice || element == ElementType.Wind)
                && tags.contains(AttackTagKind.STAR_SWIRL)) {
            return "星扩散";
        }

        if (tags.contains(AttackTagKind.MOON_BLOOM)) return "月绽放";
        if (tags.contains(AttackTagKind.MOON_SHOCK)) return "月感电";
        if (tags.contains(AttackTagKind.MOON_CRYSTAL)) return "月结晶";
        if (tags.contains(AttackTagKind.STAR_SUPER)) return "星超导";
        if (tags.contains(AttackTagKind.STAR_SWIRL)) return "星扩散";
        return null;
    }

    private static void ensureAttackTagIndex() {
        if (!ATTACK_TAG_INDEX_LOADED.compareAndSet(false, true)) return;
        try {
            Path root = FileUtils.getResourcePath("BinOutput/Ability");
            if (!Files.isDirectory(root)) {
                Grasscutter.getLogger().warn("[DPS-REACT] Ability dir missing: {}", root);
                return;
            }
            int files = 0;
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path path : walk.filter(p -> p.toString().endsWith(".json")).toList()) {
                    files++;
                    indexAbilityFile(path);
                }
            }
            propagateMoonTagsToDamageAliases();
            Grasscutter.getLogger()
                    .info(
                            "[DPS-REACT] attackTag index: {} abilities from {} files",
                            ABILITY_ATTACK_TAGS.size(),
                            files);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("[DPS-REACT] failed to build attackTag index", t);
        }
    }

    /**
     * 月绽放常见：真正带 {@code MoonOvergrowDamage} 的是 MoonLight Gadget，命中却记在
     * {@code *_Damage_Handler} / {@code *_ExtraAttack} 上（奈芙尔幻戏等）。按角色前缀回填，不写死角色。
     */
    private static void propagateMoonTagsToDamageAliases() {
        var snapshot = Map.copyOf(ABILITY_ATTACK_TAGS);
        for (var entry : snapshot.entrySet()) {
            String name = entry.getKey();
            EnumSet<AttackTagKind> tags = entry.getValue();
            if (tags == null || tags.isEmpty()) continue;

            String prefix = characterPrefix(name);
            if (prefix == null) continue;

            EnumSet<AttackTagKind> moonOnly = EnumSet.noneOf(AttackTagKind.class);
            if (tags.contains(AttackTagKind.MOON_BLOOM)) moonOnly.add(AttackTagKind.MOON_BLOOM);
            if (tags.contains(AttackTagKind.MOON_SHOCK)) moonOnly.add(AttackTagKind.MOON_SHOCK);
            if (tags.contains(AttackTagKind.MOON_CRYSTAL)) moonOnly.add(AttackTagKind.MOON_CRYSTAL);
            if (moonOnly.isEmpty()) continue;

            // Damage_Handler 常转发月反应段伤害
            mergeAbilityTags(prefix + "_Damage_Handler", moonOnly);

            // 幻戏/月光重击：源能力名带 ExtraAttack+MoonLight / MoonExtraAttack
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("extraattack_moonlight")
                    || lower.contains("moonlight_gadget")
                    || lower.contains("moonextraattack")
                    || (lower.contains("extraattack")
                            && lower.contains("moon")
                            && (lower.contains("overgrow") || lower.contains("light")))) {
                mergeAbilityTags(prefix + "_ExtraAttack", EnumSet.of(AttackTagKind.MOON_BLOOM));
            }
        }
    }

    private static String characterPrefix(String abilityName) {
        if (abilityName == null || abilityName.isEmpty()) return null;
        if (abilityName.startsWith("Avatar_")) {
            int first = abilityName.indexOf('_');
            int second = abilityName.indexOf('_', first + 1);
            if (second > first) return abilityName.substring(0, second);
            return null;
        }
        int u = abilityName.indexOf('_');
        return u > 0 ? abilityName.substring(0, u) : null;
    }

    private static void mergeAbilityTags(String abilityName, EnumSet<AttackTagKind> add) {
        if (abilityName == null || add == null || add.isEmpty()) return;
        ABILITY_ATTACK_TAGS.merge(
                abilityName,
                EnumSet.copyOf(add),
                (a, b) -> {
                    EnumSet<AttackTagKind> m = EnumSet.copyOf(a);
                    m.addAll(b);
                    return m;
                });
    }

    private static void indexAbilityFile(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            if (!text.contains("attackTag")) return;
            JsonElement root = JsonParser.parseString(text);
            if (root.isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray()) {
                    indexAbilityNode(el);
                }
            } else {
                indexAbilityNode(root);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void indexAbilityNode(JsonElement el) {
        if (el == null || !el.isJsonObject()) return;
        JsonObject obj = el.getAsJsonObject();
        JsonObject body =
                obj.has("Default") && obj.get("Default").isJsonObject()
                        ? obj.getAsJsonObject("Default")
                        : obj;
        if (!body.has("abilityName") || !body.get("abilityName").isJsonPrimitive()) return;
        String name = body.get("abilityName").getAsString();
        if (name == null || name.isEmpty()) return;

        EnumSet<AttackTagKind> found = EnumSet.noneOf(AttackTagKind.class);
        collectAttackTags(body, found);
        if (!found.isEmpty()) {
            ABILITY_ATTACK_TAGS.merge(
                    name,
                    found,
                    (a, b) -> {
                        EnumSet<AttackTagKind> m = EnumSet.copyOf(a);
                        m.addAll(b);
                        return m;
                    });
        }
    }

    private static void collectAttackTags(JsonElement el, EnumSet<AttackTagKind> out) {
        if (el == null || out == null) return;
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("attackTag") && obj.get("attackTag").isJsonPrimitive()) {
                AttackTagKind kind = kindOfAttackTag(obj.get("attackTag").getAsString());
                if (kind != null) out.add(kind);
            }
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                collectAttackTags(e.getValue(), out);
            }
        } else if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement child : arr) {
                collectAttackTags(child, out);
            }
        }
    }

    private static AttackTagKind kindOfAttackTag(String tag) {
        if (tag == null || tag.isEmpty()) return null;
        return switch (tag) {
            case "MoonOvergrowDamage" -> AttackTagKind.MOON_BLOOM;
            case "MoonShockDamage" -> AttackTagKind.MOON_SHOCK;
            case "MoonCrystallizeWaterDamage" -> AttackTagKind.MOON_CRYSTAL;
            case "StarSuperconductorIceDamage", "StarSuperconductorElectricDamage" ->
                    AttackTagKind.STAR_SUPER;
            case "StarSwirlIceDamage", "StarSwirlWindDamage" -> AttackTagKind.STAR_SWIRL;
            default -> null;
        };
    }

    /** 星反应场地：冰伤可归星超导/星扩散。月系不再按元素整类改判。 */
    private static String fromStarField(GameEntity entity, ElementType element) {
        if (element == null) return null;
        FieldFlags flags = scanTeamFields(entity);
        if (flags.starSuper && element == ElementType.Ice) return "星超导";
        if (flags.starSwirl && (element == ElementType.Ice || element == ElementType.Wind))
            return "星扩散";
        return null;
    }

    /** 队伍开着月绽放时，普通「绽放」核按月绽放计（超绽/烈绽不动）。 */
    private static String upgradeBloomIfMoonTeam(String named, GameEntity entity) {
        if (!"绽放".equals(named)) return named;
        return scanTeamFields(entity).moonBloom ? "月绽放" : named;
    }

    private static FieldFlags scanTeamFields(GameEntity entity) {
        FieldFlags flags = new FieldFlags();
        scanEntityFields(entity, flags);
        Player player = resolvePlayer(entity);
        if (player != null) {
            try {
                for (EntityAvatar avatar : player.getTeamManager().getActiveTeam()) {
                    scanEntityFields(avatar, flags);
                }
            } catch (Throwable ignored) {
            }
        }
        return flags;
    }

    private static final class FieldFlags {
        boolean starSuper;
        boolean starSwirl;
        boolean moonBloom;
    }

    private static void scanEntityFields(GameEntity entity, FieldFlags flags) {
        if (entity == null || flags == null) return;
        try {
            var abilities = entity.getInstancedAbilities();
            if (abilities == null) return;
            for (var ability : abilities) {
                if (ability == null || ability.getData() == null) continue;
                String n = ability.getData().abilityName;
                if (n == null) continue;
                String lower = n.toLowerCase(Locale.ROOT);
                if (lower.contains("starsuperconductor")
                        || lower.contains("starsuperconducted")
                        || lower.contains("starsupport")
                        || (lower.contains("relic")
                                && lower.contains("star")
                                && lower.contains("super"))) {
                    flags.starSuper = true;
                }
                if (lower.contains("starswirl")) {
                    flags.starSwirl = true;
                }
                if (lower.contains("moonovergrow")
                        || lower.contains("moonbloom")
                        || (lower.contains("moon") && lower.contains("overgrow"))
                        || lower.contains("preload_moonovergrow")
                        || lower.contains("reset_moonovergrow")) {
                    flags.moonBloom = true;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Player resolvePlayer(GameEntity entity) {
        if (entity == null) return null;
        try {
            if (entity instanceof EntityAvatar avatar) {
                return avatar.getPlayer();
            }
            if (entity instanceof EntityClientGadget gadget) {
                if (gadget.getOwner() != null) return gadget.getOwner();
                if (gadget.getScene() != null) {
                    GameEntity owner = gadget.getScene().getEntityById(gadget.getOwnerEntityId());
                    if (owner instanceof EntityAvatar av) return av.getPlayer();
                    if (owner instanceof EntityClientGadget og && og.getOwner() != null) {
                        return og.getOwner();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String amplifyByElement(ElementType element, float rate) {
        if (element == null) return null;
        return switch (element) {
            case Ice -> "融化";
            case Water -> "蒸发";
            case Fire -> rate >= 1.75f ? "融化" : "蒸发";
            default -> null;
        };
    }

    private static Float findAmplifyRate(AttackResult result) {
        try {
            for (Method method : result.getClass().getMethods()) {
                if (method.getParameterCount() != 0 || method.getReturnType() != float.class) continue;
                String name = method.getName();
                if (!name.startsWith("get") || name.equals("getDamage")) continue;
                float v = (Float) method.invoke(result);
                if (!Float.isFinite(v)) continue;
                if (v >= 0.4f && v <= 2.5f && Math.abs(v - result.getDamage()) > 1f) return v;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int[] collectAllInts(Object message) {
        if (message == null) return new int[0];
        try {
            Method[] methods = message.getClass().getMethods();
            int[] buf = new int[methods.length];
            int n = 0;
            for (Method method : methods) {
                if (method.getParameterCount() != 0 || method.getReturnType() != int.class) continue;
                String name = method.getName();
                if (!name.startsWith("get")) continue;
                if (name.equals("getSerializedSize")
                        || name.equals("hashCode")
                        || name.equals("getElementType")
                        || name.equals("getAttackerId")
                        || name.equals("getDefenseId")
                        || name.equals("getHitRetreatAngleCompat")) {
                    continue;
                }
                Object value = method.invoke(message);
                if (value instanceof Integer i && i != 0) {
                    buf[n++] = i;
                }
            }
            int[] out = new int[n];
            System.arraycopy(buf, 0, out, 0, n);
            return out;
        } catch (Throwable ignored) {
            return new int[0];
        }
    }

    private static String resolveAbilityName(GameEntity attacker, AttackResult result) {
        if (attacker == null || result == null || !result.hasAbilityIdentifier()) return null;
        try {
            int instanced = result.getAbilityIdentifier().getInstancedAbilityId();
            if (instanced <= 0) return null;
            var abilities = attacker.getInstancedAbilities();
            if (abilities == null || instanced > abilities.size()) return null;
            var ability = abilities.get(instanced - 1);
            if (ability == null || ability.getData() == null) return null;
            return ability.getData().abilityName;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String fromText(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String s = raw.toLowerCase(Locale.ROOT);

        if (s.contains("starsuperconduct")
                || s.contains("star_superconduct")
                || s.contains("starsuperconductor")
                || (s.contains("star") && s.contains("superconduct"))) {
            return "星超导";
        }
        if (s.contains("starswirl") || (s.contains("star") && s.contains("swirl"))) {
            return "星扩散";
        }
        if (s.contains("moonshock")
                || (s.contains("moon") && (s.contains("shock") || s.contains("感电")))) {
            return "月感电";
        }
        if (s.contains("moonovergrow")
                || s.contains("moonbloom")
                || (s.contains("moon") && (s.contains("overgrow") || s.contains("bloom")))) {
            return "月绽放";
        }
        if (s.contains("mooncrystal")
                || s.contains("mooncrystall")
                || (s.contains("moon") && (s.contains("crystal") || s.contains("结晶")))) {
            return "月结晶";
        }

        if (s.contains("vaporize")
                || s.contains("蒸发")
                || (s.contains("steam") && s.contains("reaction"))) return "蒸发";
        if (s.contains("melt") || s.contains("融化")) return "融化";
        if (s.contains("overload") || s.contains("explode") || s.contains("超载")) return "超载";
        if (s.contains("superconduct") || s.contains("超导")) return "超导";
        if (s.contains("electrocharged")
                || s.contains("electro_charged")
                || (s.contains("stream") && s.contains("reaction"))
                || s.contains("感电")) return "感电";
        if (s.contains("burning") || s.contains("燃烧")) return "燃烧";
        if (s.contains("shatter") || s.contains("frozenbroken") || s.contains("碎冰")) return "碎冰";
        if (s.contains("swirl") || s.contains("扩散")) return "扩散";
        if (s.contains("hyperbloom") || s.contains("超绽")) return "超绽放";
        if (s.contains("burgeon") || s.contains("烈绽")) return "烈绽放";
        if (s.contains("bloom") || s.contains("overgrow") || s.contains("绽放")) return "绽放";
        if (s.contains("aggravate") || s.contains("超激")) return "超激化";
        if (s.contains("spread") || s.contains("蔓激")) return "蔓激化";
        return null;
    }

    private static String nameOfReactionId(int type) {
        boolean beyond = type >= 4700 && type <= 4800;
        if (beyond) type -= 4700;
        else if (type >= 1 && type <= 5) return null;
        return switch (type) {
            case 1 -> "超载";
            case 2 -> "感电";
            case 3, 4 -> "燃烧";
            case 6 -> "绽放";
            case 7 -> "融化";
            case 12 -> "感电";
            case 16 -> "超导";
            case 17, 18, 19, 20, 21, 22, 23, 24 -> "扩散";
            case 31 -> "碎冰";
            case 34 -> "超激化";
            case 35 -> "蔓激化";
            case 40 -> "月感电";
            case 41 -> "月绽放";
            case 42 -> "月结晶";
            case 43 -> "星超导";
            case 44 -> "星扩散";
            default -> null;
        };
    }

    private static void probeOnce(
            AttackResult result, GameEntity attacker, ElementType element, String resolved) {
        if (PROBE_LEFT.getAndDecrement() <= 0) return;
        try {
            if (result == null) {
                Grasscutter.getLogger()
                        .warn(
                                "[DPS-REACT] miss result=null elem={} atk={}",
                                element,
                                attacker != null ? attacker.getClass().getSimpleName() : null);
                return;
            }
            String ability = resolveAbilityName(attacker, result);
            StringBuilder ints = new StringBuilder();
            for (int v : collectAllInts(result)) {
                ints.append(v).append(',');
            }
            ensureAttackTagIndex();
            EnumSet<AttackTagKind> tags =
                    ability != null
                            ? ABILITY_ATTACK_TAGS.getOrDefault(
                                    ability, EnumSet.noneOf(AttackTagKind.class))
                            : EnumSet.noneOf(AttackTagKind.class);
            Grasscutter.getLogger()
                    .warn(
                            "[DPS-REACT] miss dmg={} elem={} anim={} ability={} atk={} hashes=[{}] attackTags={}",
                            result.getDamage(),
                            element,
                            result.getAnimEventId(),
                            ability,
                            attacker != null ? attacker.getClass().getSimpleName() : null,
                            ints,
                            tags);
        } catch (Throwable ignored) {
        }
    }
}
