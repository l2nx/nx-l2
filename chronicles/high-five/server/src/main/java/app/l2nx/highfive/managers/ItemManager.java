/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.managers;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.EventMonster;
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.item.OnItemCreate;
import app.l2nx.highfive.network.holders.MailMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.repository.item.model.ItemElemental;
import app.l2nx.highfive.repository.item.model.ItemRow;
import app.l2nx.highfive.repository.item.model.LotteryTicket;
import app.l2nx.highfive.repository.item.model.PaperdollItem;
import app.l2nx.highfive.util.GMAudit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Manages the creation and destruction of items within the game.
 * @author Mobius
 */
@Slf4j
@Component
public class ItemManager {

    private static final Logger ITEM_LOG = LoggerFactory.getLogger("item");

    private static volatile ItemManager instance;

    private final ItemRepository itemRepository;
    private final ItemAugmentationRepository augmentationRepository;
    private final ItemElementalRepository elementalRepository;
    private final ItemVariableRepository variableRepository;
    private final TransactionTemplate transactionTemplate;

    public ItemManager(
            ItemRepository itemRepository,
            ItemAugmentationRepository augmentationRepository,
            ItemElementalRepository elementalRepository,
            ItemVariableRepository variableRepository,
            TransactionTemplate transactionTemplate) {
        this.itemRepository = itemRepository;
        this.augmentationRepository = augmentationRepository;
        this.elementalRepository = elementalRepository;
        this.variableRepository = variableRepository;
        this.transactionTemplate = transactionTemplate;
        instance = this;
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static ItemManager getInstance() {
        final ItemManager current = instance;
        if (current == null) {
            throw new IllegalStateException("ItemManager is used before the Spring context created it.");
        }
        return current;
    }

    /**
     * Creates a new item based on the specified parameters and logs the creation process.<br>
     * This overloaded version does not require a reference object.
     * @param process an ItemProcessType identifier of the process triggering this action
     * @param itemId the item ID of the item to be created
     * @param count the quantity of the item for stackable items
     * @param actor the player requesting the item creation
     * @return the created item
     */
    public static Item createItem(ItemProcessType process, int itemId, long count, Player actor) {
        return createItem(process, itemId, count, actor, null);
    }

    /**
     * Creates an item with a specified ID, quantity, and logs the creation.<br>
     * This method handles loot privileges, adds the item to the world, configures logging settings and notifies scripts of the creation.
     * @param process an ItemProcessType identifier for the process triggering the action (e.g., "LOOT" or "BUY")
     * @param itemId the unique ID of the item to create
     * @param count the quantity of the item for stackable items
     * @param actor the creature (typically a player) requesting the item creation
     * @param reference an object referencing the action, such as an NPC selling an item or previous item in transformation
     * @return the created item with the specified parameters
     */
    public static Item createItem(ItemProcessType process, int itemId, long count, Creature actor, Object reference) {
        // Create and Init the Item corresponding to the Item Identifier.
        final Item item = new Item(IdManager.getInstance().getNextId(), itemId);
        if ((process == ItemProcessType.LOOT) && !PlayerConfig.AUTO_LOOT_ITEM_IDS.contains(itemId)) {
            ScheduledFuture<?> itemLootShedule;
            if ((reference instanceof Attackable) && ((Attackable) reference).isRaid()) // Loot privilege for raids.
            {
                // If in CommandChannel and was killing a World/RaidBoss.
                final Attackable raid = (Attackable) reference;
                if ((raid.getFirstCommandChannelAttacked() != null) && !PlayerConfig.AUTO_LOOT_RAIDS) {
                    item.setOwnerId(raid.getFirstCommandChannelAttacked().getLeaderObjectId());
                    itemLootShedule = WorldPartition.loop()
                            .schedule(
                                    new ResetOwner(item),
                                    PlayerConfig.LOOT_RAIDS_PRIVILEGE_INTERVAL,
                                    TimeUnit.MILLISECONDS);
                    item.setItemLootShedule(itemLootShedule);
                }
            } else if (!PlayerConfig.AUTO_LOOT
                    || ((reference instanceof EventMonster) && ((EventMonster) reference).eventDropOnGround())) {
                item.setOwnerId(actor.getObjectId());
                itemLootShedule = WorldPartition.loop().schedule(new ResetOwner(item), 15000, TimeUnit.MILLISECONDS);
                item.setItemLootShedule(itemLootShedule);
            }
        }

        // Add the Item object to the World.
        World.addObject(item);

        // Set Item parameters.
        if (item.isStackable() && (count > 1)) {
            item.setCount(count);
        }

        if ((GeneralConfig.LOG_ITEMS && ((!GeneralConfig.LOG_ITEMS_SMALL_LOG) && (!GeneralConfig.LOG_ITEMS_IDS_ONLY)))
                || (GeneralConfig.LOG_ITEMS_SMALL_LOG && (item.isEquipable() || (item.getId() == Inventory.ADENA_ID)))
                || (GeneralConfig.LOG_ITEMS_IDS_ONLY && GeneralConfig.LOG_ITEMS_IDS_LIST.contains(item.getId()))) {
            if (item.isEnchanted()) {
                ITEM_LOG.info(
                        "CREATE:{}, item {}:+{} {}({}), {}, {}",
                        process,
                        item.getObjectId(),
                        item.getEnchantLevel(),
                        item.getTemplate().getName(),
                        item.getCount(),
                        actor,
                        reference);
            } else {
                ITEM_LOG.info(
                        "CREATE:{}, item {}:{}({}), {}, {}",
                        process,
                        item.getObjectId(),
                        item.getTemplate().getName(),
                        item.getCount(),
                        actor,
                        reference);
            }
        }

        if ((actor != null) && actor.isGM() && GeneralConfig.GMAUDIT) {
            String referenceName = "no-reference";
            if (reference instanceof WorldObject) {
                referenceName =
                        (((WorldObject) reference).getName() != null ? ((WorldObject) reference).getName() : "no-name");
            } else if (reference instanceof String) {
                referenceName = (String) reference;
            }

            final String targetName =
                    (actor.getTarget() != null ? actor.getTarget().getName() : "no-target");
            GMAudit.logAction(
                    String.valueOf(actor),
                    process + "(id: " + itemId + " count: " + count + " name: " + item.getItemName() + " objId: "
                            + item.getObjectId() + ")",
                    targetName,
                    "Object referencing this action is: " + referenceName);
        }

        // Notify to scripts.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_ITEM_CREATE, item.getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnItemCreate.builder()
                                    .item(item)
                                    .activeChar(actor)
                                    .reference(reference)
                                    .build(),
                            item.getTemplate());
        }

        return item;
    }

    /**
     * Destroys the specified item, making it unusable, removes it from the world and logs the deletion.<br>
     * This method also handles special cases, such as deleting pets associated with pet control items.
     * @param process an ItemProcessType identifier for the process triggering this action
     * @param item the item instance to be destroyed
     * @param actor the player requesting the item destruction
     * @param reference an object referencing the action, such as an NPC or another item
     */
    public static void destroyItem(ItemProcessType process, Item item, Player actor, Object reference) {
        synchronized (item) {
            final long old = item.getCount();
            item.setCount(0);
            item.setOwnerId(0);
            item.setItemLocation(ItemLocation.VOID);
            item.setLastChange(Item.REMOVED);

            World.removeObject(item);
            // The rows are deleted while the handle still resolves to the item's id.
            item.updateDatabase(true);
            IdManager.getInstance().releaseId(item.getObjectId());

            if ((process != null) && (process != ItemProcessType.NONE)) {
                if ((GeneralConfig.LOG_ITEMS
                                && ((!GeneralConfig.LOG_ITEMS_SMALL_LOG) && (!GeneralConfig.LOG_ITEMS_IDS_ONLY)))
                        || (GeneralConfig.LOG_ITEMS_SMALL_LOG
                                && (item.isEquipable() || (item.getId() == Inventory.ADENA_ID)))
                        || (GeneralConfig.LOG_ITEMS_IDS_ONLY
                                && GeneralConfig.LOG_ITEMS_IDS_LIST.contains(item.getId()))) {
                    if (item.isEnchanted()) {
                        ITEM_LOG.info(
                                "DELETE:{}, item {}:+{} {}({}), PrevCount({}), {}, {}",
                                process,
                                item.getObjectId(),
                                item.getEnchantLevel(),
                                item.getTemplate().getName(),
                                item.getCount(),
                                old,
                                actor,
                                reference);
                    } else {
                        ITEM_LOG.info(
                                "DELETE:{}, item {}:{}({}), PrevCount({}), {}, {}",
                                process,
                                item.getObjectId(),
                                item.getTemplate().getName(),
                                item.getCount(),
                                old,
                                actor,
                                reference);
                    }
                }

                if ((actor != null) && actor.isGM() && GeneralConfig.GMAUDIT) {
                    String referenceName = "no-reference";
                    if (reference instanceof WorldObject) {
                        referenceName = (((WorldObject) reference).getName() != null
                                ? ((WorldObject) reference).getName()
                                : "no-name");
                    } else if (reference instanceof String) {
                        referenceName = (String) reference;
                    }

                    final String targetName =
                            (actor.getTarget() != null ? actor.getTarget().getName() : "no-target");
                    GMAudit.logAction(
                            String.valueOf(actor),
                            process + "(id: " + item.getId() + " count: " + item.getCount() + " itemObjId: "
                                    + item.getObjectId() + ")",
                            targetName,
                            "Object referencing this action is: " + referenceName);
                }
            }
        }
    }

    /**
     * @return the items of both locations, in slot order
     */
    public List<ItemRow> findItems(int ownerId, ItemLocation baseLocation, ItemLocation equipLocation) {
        return itemRepository.findByOwner(ownerId, baseLocation, equipLocation);
    }

    public List<ItemRow> findItems(int ownerId, ItemLocation location) {
        return itemRepository.findByOwner(ownerId, location);
    }

    public List<ItemRow> findAttachments(int messageId) {
        return itemRepository.findByMail(messageId);
    }

    public List<PaperdollItem> findPaperdoll(int characterId) {
        return itemRepository.findPaperdoll(characterId);
    }

    public boolean hasPetItems(int characterId) {
        return itemRepository.hasPetItems(characterId);
    }

    /**
     * @return the owner of any stored item of the template; an item attached to a mail has none
     */
    public Optional<Integer> findAnyOwner(int itemId) {
        return itemRepository.findAnyOwner(itemId);
    }

    public List<LotteryTicket> findLotteryTickets(int ticketItemId, int lotteryId) {
        return itemRepository.findLotteryTickets(ticketItemId, lotteryId);
    }

    /**
     * Stores a new item together with the augmentation and elementals it got before its first save.
     *
     * @param augmentationId {@code null} for an item without augmentation
     */
    public void insertItem(int ownerId, ItemRow item, Integer augmentationId, Collection<ItemElemental> elementals) {
        transactionTemplate.executeWithoutResult(status -> {
            itemRepository.insert(ownerId, item);
            if (augmentationId != null) {
                augmentationRepository.save(item.objectId(), augmentationId);
            }

            if (!elementals.isEmpty()) {
                elementalRepository.replaceAll(item.objectId(), elementals);
            }
        });
    }

    public void updateItem(int ownerId, ItemRow item) {
        itemRepository.update(ownerId, item);
    }

    /**
     * Deletes the item with its augmentation, elementals, variables and, for a pet control item, the pet.
     */
    public void deleteItem(int objectId) {
        itemRepository.delete(objectId);
    }

    public int deleteItems(int ownerId, int itemId) {
        return itemRepository.deleteByOwner(ownerId, itemId);
    }

    /**
     * Rows of unloaded items are deleted after the scan; only the single writer stops a container loading them meanwhile.
     */
    public void destroyItemsOfTemplate(int itemId) {
        WorldPartition.checkOwner();
        for (Item item : loadedItems(itemId)) {
            if (!destroyQuietly(item)) {
                log.warn(
                        "Item {} stays loaded in {} of {}: it was not destroyed through a container.",
                        item,
                        item.getItemLocation(),
                        item.getOwnerId());
            }
        }

        // Deleting the row of an item still loaded would leave a copy that is used and saved again.
        final List<Integer> stillLoaded =
                loadedItems(itemId).stream().map(Item::getObjectId).toList();
        itemRepository.deleteByItemIdExcept(itemId, stillLoaded);
    }

    private static List<Item> loadedItems(int itemId) {
        final List<Item> loaded = new ArrayList<>();
        for (WorldObject object : World.getVisibleObjects()) {
            if ((object instanceof Item item) && (item.getId() == itemId)) {
                loaded.add(item);
            }
        }

        return loaded;
    }

    private static boolean destroyQuietly(Item item) {
        try {
            return destroyLoaded(item);
        } catch (RuntimeException e) {
            log.warn("Could not destroy the loaded item {}: {}", item, e.getMessage(), e);
            return false;
        }
    }

    private static boolean destroyLoaded(Item item) {
        final int ownerId = item.getOwnerId();
        final int objectId = item.getObjectId();
        final long count = item.getCount();
        return switch (item.getItemLocation()) {
            case INVENTORY, PAPERDOLL -> {
                final Player player = World.getPlayer(ownerId);
                yield (player != null) && player.destroyItem(ItemProcessType.DESTROY, objectId, count, null, true);
            }
            case PET, PET_EQUIP -> {
                final Pet pet = World.getPet(ownerId);
                yield (pet != null) && pet.destroyItem(ItemProcessType.DESTROY, objectId, count, null, true);
            }
            case VOID -> {
                if (!item.isSpawned() || !item.decayMe()) {
                    yield false;
                }

                // ItemsAutoDestroyTaskManager drops an item without a drop time instead of decaying it again,
                // which would hit the object that reuses the released id.
                item.setDropTime(0);
                destroyItem(ItemProcessType.DESTROY, item, null, null);
                yield true;
            }
            default -> {
                final ItemContainer container = storageOf(item);
                yield (container != null)
                        && (container.destroyItem(ItemProcessType.DESTROY, objectId, count, null, null) != null);
            }
        };
    }

    private static ItemContainer storageOf(Item item) {
        return switch (item.getItemLocation()) {
            case WAREHOUSE -> ofPlayer(item, Player::getWarehouse);
            case FREIGHT -> ofPlayer(item, Player::getFreight);
            case REFUND -> ofPlayer(item, Player::getRefund);
            case CLANWH -> {
                final Clan clan = ClanTable.getInstance().getClan(item.getOwnerId());
                yield clan == null ? null : clan.getWarehouse();
            }
            case MAIL -> {
                final MailMessage message = MailManager.getInstance().getMessage(item.getLocationSlot());
                yield message == null ? null : message.getAttachments();
            }
            default -> null;
        };
    }

    private static ItemContainer ofPlayer(Item item, Function<Player, ItemContainer> container) {
        final Player player = World.getPlayer(item.getOwnerId());
        return player == null ? null : container.apply(player);
    }

    public Optional<Integer> findAugmentation(int objectId) {
        return augmentationRepository.find(objectId);
    }

    public void saveAugmentation(int objectId, int augmentationId) {
        augmentationRepository.save(objectId, augmentationId);
    }

    public void deleteAugmentation(int objectId) {
        augmentationRepository.delete(objectId);
    }

    public List<ItemElemental> findElementals(int objectId) {
        return elementalRepository.findAll(objectId);
    }

    public void saveElementals(int objectId, Collection<ItemElemental> elementals) {
        elementalRepository.replaceAll(objectId, elementals);
    }

    public void deleteElemental(int objectId, byte element) {
        elementalRepository.delete(objectId, element);
    }

    public void deleteElementals(int objectId) {
        elementalRepository.deleteAll(objectId);
    }

    public Map<String, String> findVariables(int objectId) {
        return variableRepository.findAll(objectId);
    }

    public boolean hasVariables(int objectId) {
        return variableRepository.exists(objectId);
    }

    public void saveVariables(int objectId, Collection<String> deleted, Map<String, String> changed) {
        variableRepository.save(objectId, deleted, changed);
    }

    public void deleteVariables(int objectId) {
        variableRepository.deleteAll(objectId);
    }

    /**
     * Runnable class that resets the owner of an item to 0 after a specified time interval. <br>
     * This is typically used for loot items that need a temporary owner before becoming free to collect.
     */
    protected static class ResetOwner implements Runnable {
        Item item;

        public ResetOwner(Item item) {
            this.item = item;
        }

        @Override
        public void run() {
            // Set owner id to 0 only when location is VOID.
            if (item.getItemLocation() == ItemLocation.VOID) {
                item.setOwnerId(0);
            }

            item.setItemLootShedule(null);
        }
    }
}
