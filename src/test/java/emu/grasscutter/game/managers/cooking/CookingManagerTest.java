package emu.grasscutter.game.managers.cooking;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.avatar.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.quest.QuestEventRecorder;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerCookReqOuterClass.PlayerCookReq;
import emu.grasscutter.net.proto.PlayerCookRspOuterClass.PlayerCookRsp;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.function.BooleanSupplier;

final class CookingManagerTest {
    private static final Gson GSON = new Gson();
    private static final int RECIPE = 991330;
    private static final int FOOD = 991331;
    private static final int ASSISTANT = 991332;
    private TestPlayer player;
    private CookRecipeData oldRecipe;
    private ItemData oldFood;
    private CookBonusData oldBonus;

    @BeforeEach
    void setup() {
        player = new TestPlayer();
        oldRecipe = GameData.getCookRecipeDataMap().put(RECIPE, recipe(FOOD, false));
        oldFood =
                GameData.getItemDataMap()
                        .put(
                                FOOD,
                                GSON.fromJson(
                                        "{\"id\":991331,\"itemType\":\"ITEM_MATERIAL\",\"rankLevel\":1,\"stackLimit\":99999}",
                                        ItemData.class));
        oldBonus = GameData.getCookBonusDataMap().remove(ASSISTANT);
        player.getUnlockedRecipies().put(RECIPE, 0);
    }

    @AfterEach
    void restore() {
        if (oldRecipe == null) GameData.getCookRecipeDataMap().remove(RECIPE);
        else GameData.getCookRecipeDataMap().put(RECIPE, oldRecipe);
        if (oldFood == null) GameData.getItemDataMap().remove(FOOD);
        else GameData.getItemDataMap().put(FOOD, oldFood);
        if (oldBonus == null) GameData.getCookBonusDataMap().remove(ASSISTANT);
        else GameData.getCookBonusDataMap().put(ASSISTANT, oldBonus);
    }

    private static CookRecipeData recipe(int output, boolean unlocked) {
        return GSON.fromJson(
                "{\"id\":991330,\"maxProficiency\":10,\"isDefaultUnlocked\":"
                        + unlocked
                        + ",\"inputVec\":[{\"id\":100001,\"count\":2}],\"qualityOutputVec\":[{\"id\":"
                        + output
                        + ",\"count\":1}]}",
                CookRecipeData.class);
    }

    private PlayerCookRsp cook(int count, int assistant) throws Exception {
        player.cooking.handlePlayerCookReq(
                PlayerCookReq.newBuilder()
                        .setRecipeId(RECIPE)
                        .setCookCount(count)
                        .setQteQuality(3)
                        .setAssistAvatar(assistant)
                        .build());
        return PlayerCookRsp.parseFrom(player.packets.getLast().getData());
    }

    @Test
    void fullBagDoesNotChargeMaterialsOrProficiencyAndCanRetry() throws Exception {
        player.inventory.refuse = true;
        assertNotEquals(0, cook(3, 0).getRetcode());
        assertEquals(20, player.inventory.stock);
        assertEquals(0, player.inventory.payments);
        assertEquals(0, player.getUnlockedRecipies().get(RECIPE));
        player.inventory.refuse = false;
        var reply = cook(3, 0);
        assertEquals(0, reply.getRetcode());
        assertEquals(14, player.inventory.stock);
        assertEquals(3, player.inventory.received);
        assertEquals(3, reply.getRecipeData().getProficiency());
        assertEquals(3, reply.getItemList(0).getCount());
    }

    @Test
    void missingOutputAndLockedRecipeAreRejectedBeforePayment() throws Exception {
        GameData.getCookRecipeDataMap().put(RECIPE, recipe(991339, false));
        assertNotEquals(0, cook(1, 0).getRetcode());
        GameData.getCookRecipeDataMap().put(RECIPE, recipe(FOOD, false));
        player.getUnlockedRecipies().remove(RECIPE);
        assertNotEquals(0, cook(1, 0).getRetcode());
        assertEquals(0, player.inventory.payments);
        assertEquals(20, player.inventory.stock);
    }

    @Test
    void malformedQuantitiesDoNotTurnIntoOneCook() throws Exception {
        for (int count : new int[] {0, -1, 100, Integer.MAX_VALUE}) {
            assertNotEquals(0, cook(count, 0).getRetcode());
        }
        assertEquals(0, player.inventory.payments);
        assertEquals(0, player.inventory.received);
    }

    @Test
    void insufficientIngredientsCannotAdvanceProficiency() throws Exception {
        player.inventory.stock = 1;
        assertNotEquals(0, cook(1, 0).getRetcode());
        assertEquals(1, player.inventory.stock);
        assertEquals(0, player.inventory.received);
        assertEquals(0, player.getUnlockedRecipies().get(RECIPE));
    }

    @Test
    void defaultRecipeDoesNotRequireTheLoginInitializationAndProgressCaps() throws Exception {
        GameData.getCookRecipeDataMap().put(RECIPE, recipe(FOOD, true));
        player.getUnlockedRecipies().remove(RECIPE);
        assertTrue(player.cooking.isRecipeUnlocked(RECIPE));
        assertEquals(0, cook(10, 0).getRetcode());
        assertEquals(10, player.getUnlockedRecipies().get(RECIPE));
    }

    @Test
    void badSpecialtyConfigIsRejectedBeforeIngredientsAreSpent() throws Exception {
        player.ownsAssistant = true;
        GameData.getCookBonusDataMap()
                .put(
                        ASSISTANT,
                        GSON.fromJson(
                                "{\"avatarId\":991332,\"recipeId\":991330,\"paramVec\":[]}",
                                CookBonusData.class));
        assertNotEquals(0, cook(1, ASSISTANT).getRetcode());
        assertEquals(0, player.inventory.payments);
        player.ownsAssistant = false;
        assertEquals(0, cook(1, ASSISTANT).getRetcode());
        assertEquals(1, player.inventory.received);
    }

    @Test
    void recipeLearningEmitsBothStateChecksOnlyAfterARealUnlock() {
        player.getUnlockedRecipies().remove(RECIPE);
        assertTrue(player.cooking.unlockRecipe(RECIPE));
        assertEquals(
                List.of(
                        QuestContent.QUEST_CONTENT_UNLOCKED_RECIPE,
                        QuestContent.QUEST_CONTENT_NOT_UNLOCKED_RECIPE),
                player.quests.types());
        assertFalse(player.cooking.unlockRecipe(RECIPE));
        assertFalse(player.cooking.unlockRecipe(991339));
        assertEquals(2, player.quests.types().size());
    }

    private static class TestPlayer extends Player {
        final List<BasePacket> packets = new ArrayList<>();
        final QuestEventRecorder quests = new QuestEventRecorder(this);
        final TestInventory inventory = new TestInventory(this);
        final CookingManager cooking = new CookingManager(this);
        boolean ownsAssistant;
        final AvatarStorage storage =
                new AvatarStorage(this) {
                    @Override
                    public Avatar getAvatarById(int id) {
                        return ownsAssistant && id == ASSISTANT ? new Avatar() : null;
                    }
                };

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public AvatarStorage getAvatars() {
            return storage;
        }

        @Override
        public QuestManager getQuestManager() {
            return quests.manager();
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    private static class TestInventory extends Inventory {
        int stock = 20;
        int payments;
        int received;
        boolean refuse;

        TestInventory(Player player) {
            super(player);
        }

        @Override
        public synchronized boolean payItems(
                Iterable<ItemParamData> costs, int quantity, ActionReason reason) {
            int payment = 0;
            for (var cost : costs) payment += cost.getCount() * quantity;
            if (stock < payment) return false;
            stock -= payment;
            payments++;
            return true;
        }

        @Override
        public synchronized InventoryAddResult addItems(
                Collection<GameItem> items,
                ActionReason reason,
                InventoryAddPolicy policy,
                BooleanSupplier authorize,
                Runnable confirmed) {
            assertEquals(InventoryAddPolicy.ALL_OR_NOTHING, policy);
            if (refuse)
                return InventoryAddResult.rejected(items, InventoryAddResult.Status.CAPACITY_FULL);
            if (!authorize.getAsBoolean())
                return InventoryAddResult.rejected(
                        items, InventoryAddResult.Status.AUTHORIZATION_FAILED);
            confirmed.run();
            var entries = new ArrayList<InventoryAddResult.Entry>();
            for (var item : items) {
                received += item.getCount();
                entries.add(
                        new InventoryAddResult.Entry(
                                item.getItemId(),
                                item.getCount(),
                                item.getCount(),
                                InventoryAddResult.Status.ADDED));
            }
            return new InventoryAddResult(entries);
        }
    }
}
