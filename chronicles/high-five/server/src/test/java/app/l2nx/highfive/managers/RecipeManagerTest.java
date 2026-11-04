/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.holders.RecipeHolder;
import app.l2nx.highfive.data.holders.RecipeStatHolder;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.data.xml.RecipeData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.recipe.ManufactureItem;
import app.l2nx.highfive.entity.item.recipe.RecipeList;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.util.StatSet;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class RecipeManagerTest {
    private static final int CRAFTER = 268_435_700;
    private static final int CUSTOMER = 268_435_701;
    private static final int RECIPE_ID = 7;
    private static final int SWORD_ID = 20_000_070;
    private static final int PRODUCT_ID = 20_000_071;
    private static final long PRICE = 100;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final EtcItem sword = new EtcItem(new StatSet(Map.of("item_id", SWORD_ID, "name", "Sword")));
    private final EtcItem product = new EtcItem(new StatSet(Map.of("item_id", PRODUCT_ID, "name", "Product")));
    private final EtcItem adena =
            new EtcItem(new StatSet(Map.of("item_id", Inventory.ADENA_ID, "name", "Adena", "is_stackable", true)));
    private final Player crafter = mock(Player.class);
    private final Player customer = mock(Player.class);
    private final PlayerInventory crafterInventory = new PlayerInventory(crafter);
    private final PlayerInventory customerInventory = new PlayerInventory(customer);
    private final RecipeList recipeList = mock(RecipeList.class);
    private MockedStatic<ItemData> itemData;
    private MockedStatic<RecipeData> recipeData;
    private boolean craftingEnabled;
    private boolean altGameCreation;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        new IdManager(registry);
        final TransactionTemplate transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
                    invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
                    return null;
                })
                .when(transactions)
                .executeWithoutResult(any());
        new ItemManager(
                mock(ItemRepository.class),
                mock(ItemAugmentationRepository.class),
                mock(ItemElementalRepository.class),
                mock(ItemVariableRepository.class),
                transactions);

        final Field field = Player.class.getDeclaredField("inventory");
        field.setAccessible(true);
        field.set(crafter, crafterInventory);
        field.set(customer, customerInventory);
        when(crafter.getObjectId()).thenReturn(CRAFTER);
        when(crafter.getInventory()).thenReturn(crafterInventory);
        when(crafter.hasRecipeList(RECIPE_ID)).thenReturn(true);
        when(crafter.getSkillLevel(anyInt())).thenReturn(5);
        when(customer.getObjectId()).thenReturn(CUSTOMER);
        when(customer.getInventory()).thenReturn(customerInventory);
        when(customer.getAdena()).thenAnswer(_ -> customerInventory.getAdena());

        when(recipeList.getId()).thenReturn(RECIPE_ID);
        when(recipeList.getLevel()).thenReturn(1);
        when(recipeList.getSuccessRate()).thenReturn(100);
        when(recipeList.getItemId()).thenReturn(PRODUCT_ID);
        when(recipeList.getCount()).thenReturn(1);
        when(recipeList.getRareItemId()).thenReturn(-1);
        when(recipeList.isDwarvenRecipe()).thenReturn(true);
        when(recipeList.getStatUse()).thenReturn(new RecipeStatHolder[0]);
        when(recipeList.getAltStatChange()).thenReturn(new RecipeStatHolder[0]);

        final ItemData items = mock(ItemData.class);
        when(items.getTemplate(SWORD_ID)).thenReturn(sword);
        when(items.getTemplate(PRODUCT_ID)).thenReturn(product);
        when(items.getTemplate(Inventory.ADENA_ID)).thenReturn(adena);
        itemData = mockStatic(ItemData.class);
        itemData.when(ItemData::getInstance).thenReturn(items);
        final RecipeData recipes = mock(RecipeData.class);
        when(recipes.getValidRecipeList(any(), eq(RECIPE_ID))).thenReturn(recipeList);
        recipeData = mockStatic(RecipeData.class);
        recipeData.when(RecipeData::getInstance).thenReturn(recipes);

        craftingEnabled = PlayerConfig.IS_CRAFTING_ENABLED;
        altGameCreation = PlayerConfig.ALT_GAME_CREATION;
        PlayerConfig.IS_CRAFTING_ENABLED = true;
        PlayerConfig.ALT_GAME_CREATION = false;
    }

    @AfterEach
    void tearDown() {
        recipeData.close();
        itemData.close();
        PlayerConfig.IS_CRAFTING_ENABLED = craftingEnabled;
        PlayerConfig.ALT_GAME_CREATION = altGameCreation;
    }

    @Nested
    class RequestMakeItem {
        @Test
        void requestMakeItem_shouldTakeNothingAndCreateNothing_whenOnlyAWornItemCompletesTheMaterials() {
            needs(2);
            final Item[] held = new Item[2];
            final List<Item> actual = WorldLoopExtension.callInPass(() -> {
                held[0] = worn(held(crafterInventory, sword, 1));
                held[1] = held(crafterInventory, sword, 1);
                RecipeManager.getInstance().requestMakeItem(crafter, RECIPE_ID);
                return crafterInventory.getItems();
            });

            assertThat(actual).containsExactly(held);
        }

        @Test
        void requestMakeItem_shouldTakeTheUnequippedMaterialsAndCreate_whenTheyCoverTheRecipe() {
            needs(2);
            final Item[] worn = new Item[1];
            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                worn[0] = worn(held(crafterInventory, sword, 1));
                held(crafterInventory, sword, 1);
                held(crafterInventory, sword, 1);
                RecipeManager.getInstance().requestMakeItem(crafter, RECIPE_ID);
                return crafterInventory.getItems().stream().map(Item::getId).toList();
            });

            assertThat(actual).containsExactly(SWORD_ID, PRODUCT_ID);
            assertThat(WorldLoopExtension.callInPass(() -> crafterInventory.getAllItemsByItemId(SWORD_ID)))
                    .containsExactly(worn[0]);
        }
    }

    @Nested
    class RequestManufactureItem {
        @Test
        void requestManufactureItem_shouldTakeNothingAndPayNothing_whenTheCustomerSpentThePriceDuringTheCreation() {
            PlayerConfig.ALT_GAME_CREATION = true;
            needs(1);
            final ManufactureItem offer = mock(ManufactureItem.class);
            when(offer.getCost()).thenReturn(PRICE);
            when(crafter.getManufactureItem(RECIPE_ID)).thenReturn(offer);
            final Item[] material = new Item[1];
            WorldLoopExtension.runInPass(() -> {
                material[0] = held(customerInventory, sword, 1);
                held(customerInventory, adena, PRICE);
                RecipeManager.getInstance().requestManufactureItem(crafter, RECIPE_ID, customer);
                customerInventory.destroyItemByItemId(ItemProcessType.NONE, Inventory.ADENA_ID, PRICE / 2, null, null);
            });

            WorldLoopExtension.passAfter(Duration.ofMillis(100));
            WorldLoopExtension.passAfter(Duration.ofMillis(1));

            final long[] actual = WorldLoopExtension.callInPass(() ->
                    new long[] {customerInventory.getAdena(), crafterInventory.getAdena(), crafterInventory.getSize()});
            assertThat(actual).containsExactly(PRICE / 2, 0, 0);
            assertThat(WorldLoopExtension.callInPass(customerInventory::getItems))
                    .contains(material[0]);
        }
    }

    private void needs(int swords) {
        when(recipeList.getRecipes()).thenReturn(new RecipeHolder[] {new RecipeHolder(SWORD_ID, swords)});
    }

    private Item held(PlayerInventory inventory, EtcItem template, long count) {
        final Item item = new Item(registry.allocateTransient(), template);
        item.setCount(count);
        inventory.addItem(ItemProcessType.NONE, item, null, null);
        return item;
    }

    private static Item worn(Item item) {
        item.setItemLocation(ItemLocation.PAPERDOLL, Inventory.PAPERDOLL_RFINGER, false);
        return item;
    }
}
