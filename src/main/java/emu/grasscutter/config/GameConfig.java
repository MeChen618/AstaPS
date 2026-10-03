package emu.grasscutter.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.JsonUtils;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Player-facing and gameplay configuration stored in {@code game.json}. */
public final class GameConfig extends ConfigContainer.GameOptions {
    private static final int CURRENT_VERSION = 1;
    private static final Path FILE = Path.of("game.json");
    private static volatile GameConfig current;

    public int version = CURRENT_VERSION;
    public ConfigContainer.Account account = new ConfigContainer.Account();
    public ConfigContainer.JoinOptions joinOptions = new ConfigContainer.JoinOptions();
    public ConfigContainer.ConsoleAccount serverAccount = new ConfigContainer.ConsoleAccount();
    public ConfigContainer.ConsoleAccount dpsAccount = defaultDpsAccount();

    public static synchronized GameConfig load(JsonObject legacyRoot) {
        if (current != null) return current;

        if (Files.exists(FILE)) {
            try {
                current = JsonUtils.loadToClass(FILE, GameConfig.class);
            } catch (Exception exception) {
                Grasscutter.getLogger()
                        .error(
                                "Unable to load game.json. Fix its syntax or delete it to regenerate defaults.",
                                exception);
                throw new IllegalStateException("Invalid game.json", exception);
            }
        } else {
            current = migrate(legacyRoot);
            current.normalize();
            save(current);
            if (hasLegacyGameplay(legacyRoot)) {
                backupLegacyConfig();
                Grasscutter.getLogger()
                        .info("Moved gameplay configuration from config.json to game.json.");
            } else {
                Grasscutter.getLogger().info("Created game.json with default gameplay settings.");
            }
        }

        current.normalize();
        return current;
    }

    public static GameConfig get() {
        GameConfig loaded = current;
        if (loaded != null) return loaded;

        synchronized (GameConfig.class) {
            if (current != null) return current;

            JsonObject legacyRoot = null;
            try {
                if (Grasscutter.configFile.exists()) {
                    legacyRoot =
                            JsonUtils.loadToClass(
                                    Grasscutter.configFile.toPath(), JsonObject.class);
                }
            } catch (Exception exception) {
                Grasscutter.getLogger()
                        .warn("Could not inspect config.json while loading game.json.", exception);
            }
            return load(legacyRoot);
        }
    }

    private static GameConfig migrate(JsonObject root) {
        GameConfig migrated = new GameConfig();
        if (root == null) return migrated;

        JsonObject game = object(object(root, "server"), "game");
        JsonObject options = object(game, "gameOptions");
        if (options != null) {
            GameConfig decoded = JsonUtils.decode(options, GameConfig.class);
            if (decoded != null) migrated = decoded;
        }

        migrated.account =
                decodeOr(root.get("account"), ConfigContainer.Account.class, migrated.account);
        if (game != null) {
            migrated.joinOptions =
                    decodeOr(
                            game.get("joinOptions"),
                            ConfigContainer.JoinOptions.class,
                            migrated.joinOptions);
            migrated.serverAccount =
                    decodeOr(
                            game.get("serverAccount"),
                            ConfigContainer.ConsoleAccount.class,
                            migrated.serverAccount);
            migrated.dpsAccount =
                    decodeOr(
                            game.get("dpsAccount"),
                            ConfigContainer.ConsoleAccount.class,
                            migrated.dpsAccount);
        }
        return migrated;
    }

    private void normalize() {
        version = CURRENT_VERSION;
        if (account == null) account = new ConfigContainer.Account();
        if (inventoryLimits == null) inventoryLimits = new InventoryLimits();
        if (avatarLimits == null) avatarLimits = new AvatarLimits();
        if (artifactShop == null) artifactShop = new ArtifactShopOptions();
        if (newAccountIntro == null) newAccountIntro = new NewAccountIntro();
        if (questing == null) questing = new Questing();
        if (resinOptions == null) resinOptions = new ResinOptions();
        if (rates == null) rates = new Rates();
        if (tower == null) tower = new TowerOptions();
        if (handbook == null) handbook = new HandbookOptions();
        if (birthdayMail == null) birthdayMail = new BirthdayMailOptions();
        if (watermark == null) watermark = new WatermarkOptions();
        if (joinOptions == null) joinOptions = new ConfigContainer.JoinOptions();
        if (serverAccount == null) serverAccount = new ConfigContainer.ConsoleAccount();
        if (dpsAccount == null) dpsAccount = defaultDpsAccount();
    }

    private static void save(GameConfig value) {
        try (var writer = new FileWriter(FILE.toFile())) {
            writer.write(JsonUtils.encode(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to write game.json", exception);
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return null;
        return parent.getAsJsonObject(key);
    }

    private static <T> T decodeOr(JsonElement value, Class<T> type, T fallback) {
        if (value == null || value.isJsonNull()) return fallback;
        T decoded = JsonUtils.decode(value, type);
        return decoded == null ? fallback : decoded;
    }

    private static boolean hasLegacyGameplay(JsonObject root) {
        if (root == null) return false;
        if (root.has("account")) return true;
        JsonObject game = object(object(root, "server"), "game");
        if (game == null) return false;
        return game.has("gameOptions")
                || game.has("joinOptions")
                || game.has("serverAccount")
                || game.has("dpsAccount");
    }

    private static void backupLegacyConfig() {
        try {
            Path source = Grasscutter.configFile.toPath();
            Path backup = Path.of("config.json.bak");
            if (Files.exists(source) && !Files.exists(backup)) {
                Files.copy(source, backup, StandardCopyOption.COPY_ATTRIBUTES);
                Grasscutter.getLogger()
                        .info("Backed up the pre-split configuration to config.json.bak.");
            }
        } catch (Exception exception) {
            Grasscutter.getLogger().warn("Could not create config.json.bak.", exception);
        }
    }

    private static ConfigContainer.ConsoleAccount defaultDpsAccount() {
        ConfigContainer.ConsoleAccount account = new ConfigContainer.ConsoleAccount();
        account.nickName = "DPS";
        account.signature = "Send dps30 to start, dpsstop to end early";
        account.adventureRank = 60;
        return account;
    }
}
