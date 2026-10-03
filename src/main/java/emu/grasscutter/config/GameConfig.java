package emu.grasscutter.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.JsonUtils;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Player-facing and gameplay configuration stored in {@code game.json}. */
public final class GameConfig {
    private static final int CURRENT_VERSION = 1;
    private static final Path FILE = Path.of("game.json");
    private static volatile GameConfig current;

    public int version = CURRENT_VERSION;
    public Account account = new Account();
    public InventoryLimits inventoryLimits = new InventoryLimits();
    public AvatarLimits avatarLimits = new AvatarLimits();
    public int sceneEntityLimit = 1000;
    public boolean isPreventEntityError = true;
    public boolean watchGachaConfig = false;
    public boolean enableShopItems = false;
    public ArtifactShopOptions artifactShop = new ArtifactShopOptions();
    public boolean staminaUsage = true;
    public boolean energyUsage = true;
    public boolean fishhookTeleport = true;
    public boolean trialCostumes = false;
    public int firstLoginCutscene = 0;
    public boolean disableCutscenes = false;
    public NewAccountIntro newAccountIntro = new NewAccountIntro();

    @SerializedName(value = "questing", alternate = "questOptions")
    public Questing questing = new Questing();

    public ResinOptions resinOptions = new ResinOptions();
    public Rates rates = new Rates();
    public TowerOptions tower = new TowerOptions();
    public HandbookOptions handbook = new HandbookOptions();
    public BirthdayMailOptions birthdayMail = new BirthdayMailOptions();
    public WatermarkOptions watermark = new WatermarkOptions();
    public JoinOptions joinOptions = new JoinOptions();
    public ConsoleAccount serverAccount = new ConsoleAccount();
    public ConsoleAccount dpsAccount = defaultDpsAccount();

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

        migrated.account = decodeOr(root.get("account"), Account.class, migrated.account);
        if (game != null) {
            migrated.joinOptions =
                    decodeOr(game.get("joinOptions"), JoinOptions.class, migrated.joinOptions);
            migrated.serverAccount =
                    decodeOr(game.get("serverAccount"), ConsoleAccount.class, migrated.serverAccount);
            migrated.dpsAccount =
                    decodeOr(game.get("dpsAccount"), ConsoleAccount.class, migrated.dpsAccount);
        }
        return migrated;
    }

    private void normalize() {
        version = CURRENT_VERSION;
        if (account == null) account = new Account();
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
        if (joinOptions == null) joinOptions = new JoinOptions();
        if (serverAccount == null) serverAccount = new ConsoleAccount();
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

    private static ConsoleAccount defaultDpsAccount() {
        ConsoleAccount account = new ConsoleAccount();
        account.nickName = "DPS";
        account.signature = "Send dps30 to start, dpsstop to end early";
        account.adventureRank = 60;
        return account;
    }

    public static class Account {
        public boolean autoCreate = false;
        public boolean EXPERIMENTAL_RealPassword = false;
        public boolean useIntegrationPassword = false;
        public String[] defaultPermissions = {};
        public int maxPlayer = -1;
    }

    public static class InventoryLimits {
        public int weapons = 2000;
        public int relics = 2000;
        public int materials = 2000;
        public int furniture = 2000;
        public int all = 30000;
    }

    public static class AvatarLimits {
        public int singlePlayerTeam = 4;
        public int multiplayerTeam = 4;
    }

    public static class ArtifactShopOptions {
        public boolean enabled = true;
        public int shopId = 1004;
        public int costMora = 20000;
        public int costPrimogems = 0;
        public int costItemId = 0;
        public int costItemCount = 0;
        public int buyLimit = 0;
        public int artifactLevel = 20;
        public double critWeight = 8;
        public double damageWeight = 3;
        public double highRollBias = 3;
    }

    public static class NewAccountIntro {
        public boolean enabled = false;
        public int doSetPlayerBornDataNotify = 0;
        public int setPlayerBornDataRsp = 0;
        public int fallbackSeconds = 15;
    }

    public static class Questing {
        public boolean enabled = false;
        public boolean triggerAllOnLogin = false;
    }

    public static class ResinOptions {
        public boolean resinUsage = false;
        public int cap = 1600;
        public int rechargeTime = 180;
    }

    public static class Rates {
        public float adventureExp = 1.0f;
        public float mora = 1.0f;
    }

    public static class TowerOptions {
        public int scheduleId = 0;
        public boolean rotate = false;
        public int rotationPool = 12;
        public boolean skipEntranceFloors = true;
    }

    public static class HandbookOptions {
        public boolean enable = false;
        public boolean allowCommands = true;
        public Limits limits = new Limits();
        public Server server = new Server();

        public static class Limits {
            public boolean enabled = false;
            public int interval = 3;
            public int maxRequests = 10;
            public int maxEntities = 25;
        }

        public static class Server {
            public boolean enforced = false;
            public String address = "127.0.0.1";
            public int port = 443;
            public boolean canChange = true;
        }
    }

    public static class BirthdayMailOptions {
        public boolean enabled = true;
        public int expireDays = 7;
        public GiftItem[] gifts =
                new GiftItem[] {new GiftItem(202, 10000000), new GiftItem(201, 600000)};

        public static class GiftItem {
            public int itemId;
            public int count;

            public GiftItem() {
                this(202, 1);
            }

            public GiftItem(int itemId, int count) {
                this.itemId = itemId;
                this.count = count;
            }
        }
    }

    public static class WatermarkOptions {
        public boolean enabled = true;
        public String text = "";
        public String color = "#9333EA";
        public String gradientTo = "#FF1493";
        public int cmdId = 0;
        public int payloadField = 0;
    }

    public static class JoinOptions {
        public int[] welcomeEmotes = {2007, 1002, 4010};
        public String welcomeMessage = "Welcome to the Chiori test server";
        public Mail welcomeMail = new Mail();

        public static class Mail {
            public String title = "Welcome to LunaGC 6.6.0";
            public String content = "Hi there!\r\nWelcome to LunaGC!\n";
            public String sender = "Kei-Luna and pmagixc";
            public emu.grasscutter.game.mail.Mail.MailItem[] items = {};
        }
    }

    public static class ConsoleAccount {
        public int avatarId = 10000007;
        public int nameCardId = 210001;
        public int adventureRank = 1;
        public int worldLevel = 0;
        public String nickName = "LunaGC";
        public String signature = "Welcome to LunaGC";
    }
}
