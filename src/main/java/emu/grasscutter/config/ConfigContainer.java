package emu.grasscutter.config;

import static emu.grasscutter.Grasscutter.*;

import ch.qos.logback.classic.Level;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.*;
import java.util.*;
import lombok.NoArgsConstructor;

/** Runtime/deployment configuration stored in {@code config.json}. */
public class ConfigContainer {
    /* Version 16 moves all player-facing/gameplay settings to game.json. */
    private static int version() {
        return 16;
    }

    private static ThreadPoolOptions mergeThreadPoolDefaults(ThreadPoolOptions existing) {
        var merged = new ThreadPoolOptions();
        Map<String, ThreadPoolDefinition> pools = new LinkedHashMap<>(merged.pools);
        if (existing != null && existing.pools != null) pools.putAll(existing.pools);
        merged.enabled = existing == null || existing.enabled;
        merged.pools = pools;
        return merged;
    }

    /** Migrates gameplay configuration, then rewrites config.json in its runtime-only shape. */
    public static void updateConfig() {
        JsonObject raw = null;
        try {
            raw = JsonUtils.loadToClass(Grasscutter.configFile.toPath(), JsonObject.class);
            GameConfig.load(raw);
        } catch (Exception exception) {
            Grasscutter.getLogger().error("Failed to load configuration.", exception);
            System.exit(1);
            return;
        }

        int latest = version();
        if (config.version == latest) return;

        config.server.threadPools = mergeThreadPoolDefaults(config.server.threadPools);
        config.version = latest;
        Grasscutter.saveConfig(config);
    }

    public Structure folderStructure = new Structure();
    public Database databaseInfo = new Database();
    public Language language = new Language();
    public Server server = new Server();
    public int version = version();

    public static class Database {
        public DataStore server = new DataStore();
        public DataStore game = new DataStore();

        public static class DataStore {
            public String connectionUri = "mongodb://localhost:27017";
            public String collection = "grasscutter";
        }
    }

    public static class Structure {
        public String resources = "./resources/";
        public String data = "./data/";
        public String packets = "./packets/";
        public String scripts = "resources:Scripts/";
        public String plugins = "./plugins/";
        public String cache = "./cache/";
    }

    public static class Server {
        public Set<Integer> debugWhitelist = Set.of();
        public Set<Integer> debugBlacklist = Set.of();
        public ServerRunMode runMode = ServerRunMode.HYBRID;
        public boolean logCommands = false;
        public boolean fastRequire = true;

        public HTTP http = new HTTP();
        public Game game = new Game();
        public ThreadPoolOptions threadPools = new ThreadPoolOptions();
        public Dispatch dispatch = new Dispatch();
        public DebugMode debugMode = new DebugMode();
    }

    public static class WatchdogOptions {
        public boolean enableDatabaseMonitor = true;
        public int databaseCheckIntervalSeconds = 10;
        public boolean enableAutoRestart = false;
        public int autoRestartIntervalHours = 24;
    }

    public static class ThreadPoolOptions {
        public boolean enabled = true;
        public Map<String, ThreadPoolDefinition> pools =
                Map.ofEntries(
                        Map.entry("DATABASE_DEFAULT", new ThreadPoolDefinition()),
                        Map.entry("DATABASE_ACCOUNT", new ThreadPoolDefinition()),
                        Map.entry("DATABASE_ITEM", new ThreadPoolDefinition()),
                        Map.entry("DATABASE_GROUP", new ThreadPoolDefinition()));
    }

    @NoArgsConstructor
    public static class ThreadPoolDefinition {
        public int coreThreads = -1;
        public int maxThreads = -1;
        public int queueCapacity = -1;
        public long keepAliveSeconds = -1;

        public ThreadPoolDefinition(
                int coreThreads, int maxThreads, int queueCapacity, long keepAliveSeconds) {
            this.coreThreads = coreThreads;
            this.maxThreads = maxThreads;
            this.queueCapacity = queueCapacity;
            this.keepAliveSeconds = keepAliveSeconds;
        }
    }

    public static class Language {
        public Locale language = Locale.getDefault();
        public Locale fallback = Locale.US;
        public String document = "EN";
    }

    /** Schema retained for game.json and legacy-config migration. */
    public static class Account {
        public boolean autoCreate = false;
        public boolean EXPERIMENTAL_RealPassword = false;
        public boolean useIntegrationPassword = false;
        public String[] defaultPermissions = {};
        public int maxPlayer = -1;
    }

    public static class HTTP {
        public boolean startImmediately = false;
        public String bindAddress = "0.0.0.0";
        public int bindPort = 8088;
        public String accessAddress = "127.0.0.1";
        public int accessPort = 0;
        public Encryption encryption = new Encryption();
        public Policies policies = new Policies();
        public Files files = new Files();
    }

    /** Network/process settings for the game server. Gameplay settings live in GameConfig. */
    public static class Game {
        public WatchdogOptions watchdog = new WatchdogOptions();
        public String bindAddress = "0.0.0.0";
        public int bindPort = 22101;
        public String accessAddress = "127.0.0.1";
        public int accessPort = 0;
        public boolean useUniquePacketKey = true;
        public boolean useXorEncryption = true;
        public int loadEntitiesForPlayerRange = 300;
        public boolean enableScriptInBigWorld = true;
        public boolean enableConsole = true;
        public int tickRateMs = 200;
        public int kcpInterval = 20;
        public ServerDebugMode logPackets = ServerDebugMode.NONE;
        public boolean isShowPacketPayload = false;
        public boolean isShowLoopPackets = false;
        public boolean cacheSceneEntitiesEveryRun = false;
    }

    public static class Dispatch {
        public List<Region> regions = List.of();
        public String dispatchUrl = "ws://127.0.0.1:1111";
        public byte[] encryptionKey = Crypto.createSessionKey(32);
        public String dispatchKey = Utils.base64Encode(Crypto.createSessionKey(32));
        public String defaultName = "Grasscutter";
        public ServerDebugMode logRequests = ServerDebugMode.NONE;
    }

    public static class DebugMode {
        public Level serverLoggerLevel = Level.DEBUG;
        public Level servicesLoggersLevel = Level.INFO;
        public ServerDebugMode logPackets = ServerDebugMode.ALL;
        public boolean isShowPacketPayload = false;
        public boolean isShowLoopPackets = false;
        public ServerDebugMode logRequests = ServerDebugMode.ALL;
    }

    public static class Encryption {
        public boolean useEncryption = false;
        public boolean useInRouting = false;
        public String keystore = "./keystore.p12";
        public String keystorePassword = "123456";
    }

    public static class Policies {
        public CORS cors = new CORS();

        public static class CORS {
            public boolean enabled = true;
            public String[] allowedOrigins = new String[] {"*"};
        }
    }

    /** Schema for GameConfig; there is no GameOptions instance in ConfigContainer. */
    public static class GameOptions {
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

        public static class ResinOptions {
            public boolean resinUsage = false;
            public int cap = 1600;
            public int rechargeTime = 180;
        }

        public static class Questing {
            public boolean enabled = false;
            public boolean triggerAllOnLogin = false;
        }

        public static class WatermarkOptions {
            public boolean enabled = true;
            public String text = "";
            public String color = "#9333EA";
            public String gradientTo = "#FF1493";
            public int cmdId = 0;
            public int payloadField = 0;
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
    }

    /** Schema retained for game.json. */
    public static class VisionOptions {
        public String name;
        public int visionRange;
        public int gridWidth;

        public VisionOptions() {}

        public VisionOptions(String name, int visionRange, int gridWidth) {
            this.name = name;
            this.visionRange = visionRange;
            this.gridWidth = gridWidth;
        }
    }

    /** Schema retained for game.json. */
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

    /** Schema retained for game.json. */
    public static class ConsoleAccount {
        public int avatarId = 10000007;
        public int nameCardId = 210001;
        public int adventureRank = 1;
        public int worldLevel = 0;
        public String nickName = "LunaGC";
        public String signature = "Welcome to LunaGC";
    }

    public static class Files {
        public String indexFile = "./index.html";
    }

    @NoArgsConstructor
    public static class Region {
        public String Name = "os_usa";
        public String Title = "Grasscutter";
        public String Ip = "127.0.0.1";
        public int Port = 22102;

        public Region(String name, String title, String address, int port) {
            this.Name = name;
            this.Title = title;
            this.Ip = address;
            this.Port = port;
        }
    }
}
