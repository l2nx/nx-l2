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
package app.l2nx.highfive.entity.item.instance;

import static app.l2nx.highfive.entity.itemcontainer.Inventory.ADENA_ID;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.OlympiadConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.custom.TransmogConfig;
import app.l2nx.highfive.data.xml.EnchantItemOptionsData;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.data.xml.OptionData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.WorldRegion;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.item.Armor;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.enums.ShotType;
import app.l2nx.highfive.entity.item.holders.DropProtection;
import app.l2nx.highfive.entity.item.holders.Elementals;
import app.l2nx.highfive.entity.item.type.EtcItemType;
import app.l2nx.highfive.entity.item.type.ItemType;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.managers.ItemsOnGroundManager;
import app.l2nx.highfive.managers.MercTicketManager;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerAugment;
import app.l2nx.highfive.mechanics.events.holders.actor.player.inventory.OnPlayerItemDrop;
import app.l2nx.highfive.mechanics.events.holders.actor.player.inventory.OnPlayerItemPickup;
import app.l2nx.highfive.mechanics.events.holders.item.OnItemBypassEvent;
import app.l2nx.highfive.mechanics.events.holders.item.OnItemTalk;
import app.l2nx.highfive.mechanics.options.Augmentation;
import app.l2nx.highfive.mechanics.options.EnchantOptions;
import app.l2nx.highfive.mechanics.options.Options;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.mechanics.stats.functions.AbstractFunction;
import app.l2nx.highfive.mechanics.variables.ItemVariables;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.DropItem;
import app.l2nx.highfive.network.protocol.p267.serverpackets.GetItem;
import app.l2nx.highfive.network.protocol.p267.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SpawnItem;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StatusUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.item.model.ItemElemental;
import app.l2nx.highfive.repository.item.model.ItemRow;
import app.l2nx.highfive.taskmanagers.ItemLifeTimeTaskManager;
import app.l2nx.highfive.taskmanagers.ItemManaTaskManager;
import app.l2nx.highfive.util.GMAudit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

@Slf4j
@SingleWriter
public class Item extends WorldObject {

    private static final Logger ITEM_LOG = LoggerFactory.getLogger("item");

    private int ownerId;

    private Player owner;

    private int dropperObjectId = 0;

    private long count;

    private long initCount;
    /** Remaining time in milliseconds */
    private long time;

    private boolean countDecrease = false;

    private final int id;

    private final ItemTemplate template;

    private ItemLocation itemLocation;

    private int locationSlot;

    private int enchantLevel;

    private boolean wear;

    private Augmentation augmentation = null;

    private int mana = -1;

    private boolean consumingMana = false;

    private int customType1;

    private int customType2;

    private long dropTime;

    private boolean published = false;

    private boolean protectedItem;

    public static final int UNCHANGED = 0;
    public static final int ADDED = 1;
    public static final int REMOVED = 3;
    public static final int MODIFIED = 2;

    // @formatter:off
    public static final int[] DEFAULT_ENCHANT_OPTIONS = {0, 0, 0};
    // @formatter:on

    private int lastChange = 2;

    private boolean existsInDb;
    private boolean storedInDb;

    private Elementals[] elementals = null;

    private ScheduledFuture<?> itemLootShedule = null;

    private final DropProtection dropProtection = new DropProtection();

    private int shotsMask = 0;

    private final List<Options> enchantOptions = new ArrayList<>();

    public Item(int objectId, int itemId) {
        super(objectId);
        setInstanceType(InstanceType.Item);
        id = itemId;
        template = ItemData.getInstance().getTemplate(itemId);
        if ((id == 0) || (template == null)) {
            throw new IllegalArgumentException();
        }

        super.setName(template.getName());
        setCount(1);
        itemLocation = ItemLocation.VOID;
        customType1 = 0;
        customType2 = 0;
        dropTime = 0;
        mana = template.getDuration();
        time = template.getTime() == -1 ? -1 : ClockBridge.wallMillis() + (template.getTime() * 60 * 1000);
        enchantLevel = 0;
        scheduleLifeTimeTask();
    }

    public Item(int objectId, ItemTemplate itemTemplate) {
        super(objectId);
        setInstanceType(InstanceType.Item);
        id = itemTemplate.getId();
        template = itemTemplate;
        if (id == 0) {
            throw new IllegalArgumentException();
        }

        super.setName(template.getName());
        setCount(1);
        itemLocation = ItemLocation.VOID;
        mana = template.getDuration();
        time = template.getTime() == -1 ? -1 : ClockBridge.wallMillis() + (template.getTime() * 60 * 1000);
        scheduleLifeTimeTask();
    }

    public Item(int itemId) {
        this(IdManager.getInstance().getNextId(), itemId);
    }

    public int getOwnerId() {
        WorldPartition.checkOwner();
        return ownerId;
    }

    public void setDropperObjectId(int dropperObjectId) {
        WorldPartition.checkOwner();
        this.dropperObjectId = dropperObjectId;
    }

    public long getCount() {
        WorldPartition.checkOwner();
        return count;
    }

    public long getInitCount() {
        WorldPartition.checkOwner();
        return initCount;
    }

    public long getTime() {
        WorldPartition.checkOwner();
        return time;
    }

    public void setCountDecrease(boolean countDecrease) {
        WorldPartition.checkOwner();
        this.countDecrease = countDecrease;
    }

    @Override
    public int getId() {
        WorldPartition.checkOwner();
        return id;
    }

    public ItemTemplate getTemplate() {
        WorldPartition.checkOwner();
        return template;
    }

    public ItemLocation getItemLocation() {
        WorldPartition.checkOwner();
        return itemLocation;
    }

    public int getLocationSlot() {
        WorldPartition.checkOwner();
        return locationSlot;
    }

    public int getEnchantLevel() {
        WorldPartition.checkOwner();
        return enchantLevel;
    }

    public Augmentation getAugmentation() {
        WorldPartition.checkOwner();
        return augmentation;
    }

    public int getMana() {
        WorldPartition.checkOwner();
        return mana;
    }

    public int getCustomType1() {
        WorldPartition.checkOwner();
        return customType1;
    }

    public void setCustomType1(int customType1) {
        WorldPartition.checkOwner();
        this.customType1 = customType1;
    }

    public int getCustomType2() {
        WorldPartition.checkOwner();
        return customType2;
    }

    public void setCustomType2(int customType2) {
        WorldPartition.checkOwner();
        this.customType2 = customType2;
    }

    public long getDropTime() {
        WorldPartition.checkOwner();
        return dropTime;
    }

    public void setDropTime(long dropTime) {
        WorldPartition.checkOwner();
        this.dropTime = dropTime;
    }

    public boolean isPublished() {
        WorldPartition.checkOwner();
        return published;
    }

    public int getLastChange() {
        WorldPartition.checkOwner();
        return lastChange;
    }

    public void setLastChange(int lastChange) {
        WorldPartition.checkOwner();
        this.lastChange = lastChange;
    }

    /**
     * A copy of the array, so a caller cannot add or drop an attribute; the {@link Elementals} in it are the item's own
     * state, shared, and a value change on one writes into the item.
     */
    public Elementals[] getElementals() {
        WorldPartition.checkOwner();
        return elementals == null ? null : elementals.clone();
    }

    public int elementalCount() {
        WorldPartition.checkOwner();
        return elementals == null ? 0 : elementals.length;
    }

    public boolean hasElementals() {
        WorldPartition.checkOwner();
        return elementals != null;
    }

    public ScheduledFuture<?> getItemLootShedule() {
        WorldPartition.checkOwner();
        return itemLootShedule;
    }

    public void setItemLootShedule(ScheduledFuture<?> itemLootShedule) {
        WorldPartition.checkOwner();
        this.itemLootShedule = itemLootShedule;
    }

    public DropProtection getDropProtection() {
        WorldPartition.checkOwner();
        return dropProtection;
    }

    // Does not remove the item from the World all-objects registry.
    public void pickupMe(Creature creature) {
        WorldPartition.checkOwner();
        final WorldRegion oldregion = getWorldRegion();

        creature.broadcastPacket(new GetItem(this, creature.getObjectId()));

        setSpawned(false);

        if (MercTicketManager.getInstance().getTicketCastleId(id) > 0) {
            MercTicketManager.getInstance().removeTicket(this);
            ItemsOnGroundManager.getInstance().removeObject(this);
        }

        World.removeVisibleObject(this, oldregion);
        setWorldRegion(null);

        if (creature.isPlayer()) {
            if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_ITEM_PICKUP, getTemplate())) {
                EventDispatcher.getInstance()
                        .notifyEventAsync(new OnPlayerItemPickup(creature.asPlayer(), this), getTemplate());
            } else if (EventDispatcher.getInstance()
                    .hasListener(EventType.ON_PLAYER_ITEM_PICKUP, creature.asPlayer())) {
                EventDispatcher.getInstance()
                        .notifyEventAsync(new OnPlayerItemPickup(creature.asPlayer(), this), creature.asPlayer());
            }
        }
    }

    public void setOwnerId(ItemProcessType process, int ownerId, Player creator, Object reference) {
        WorldPartition.checkOwner();
        setOwnerId(ownerId);

        if ((GeneralConfig.LOG_ITEMS && ((!GeneralConfig.LOG_ITEMS_SMALL_LOG) && (!GeneralConfig.LOG_ITEMS_IDS_ONLY)))
                || (GeneralConfig.LOG_ITEMS_SMALL_LOG && (template.isEquipable() || (template.getId() == ADENA_ID)))
                || (GeneralConfig.LOG_ITEMS_IDS_ONLY && GeneralConfig.LOG_ITEMS_IDS_LIST.contains(template.getId()))) {
            if (enchantLevel > 0) {
                ITEM_LOG.info(
                        "SETOWNER:{}, item {}:+{} {}({}), {}, {}",
                        process,
                        getObjectId(),
                        enchantLevel,
                        template.getName(),
                        count,
                        creator,
                        reference);
            } else {
                ITEM_LOG.info(
                        "SETOWNER:{}, item {}:{}({}), {}, {}",
                        process,
                        getObjectId(),
                        template.getName(),
                        count,
                        creator,
                        reference);
            }
        }

        if ((creator != null) && creator.isGM() && GeneralConfig.GMAUDIT) {
            final String targetName =
                    (creator.getTarget() != null ? creator.getTarget().getName() : "no-target");
            String referenceName = "no-reference";
            if (reference instanceof WorldObject) {
                referenceName =
                        ((WorldObject) reference).getName() != null ? ((WorldObject) reference).getName() : "no-name";
            } else if (reference instanceof String) {
                referenceName = (String) reference;
            }

            GMAudit.logAction(
                    creator.toString(),
                    process + "(id: " + id + " name: " + getName() + ")",
                    targetName,
                    "Object referencing this action is: " + referenceName);
        }
    }

    public void setOwnerId(int ownerId) {
        WorldPartition.checkOwner();
        if (ownerId == this.ownerId) {
            return;
        }

        removeSkillsFromOwner();

        owner = null;
        this.ownerId = ownerId;
        storedInDb = false;

        giveSkillsToOwner();
    }

    public void setItemLocation(ItemLocation loc) {
        WorldPartition.checkOwner();
        setItemLocation(loc, 0, true);
    }

    public void setItemLocation(ItemLocation loc, int locData) {
        WorldPartition.checkOwner();
        setItemLocation(loc, locData, true);
    }

    public void setItemLocation(ItemLocation loc, int locData, boolean checkSkills) {
        WorldPartition.checkOwner();
        if ((loc == itemLocation) && (locData == locationSlot)) {
            return;
        }

        if (checkSkills) {
            removeSkillsFromOwner();
        }

        itemLocation = loc;
        locationSlot = locData;
        storedInDb = false;

        if (checkSkills) {
            giveSkillsToOwner();
        }
    }

    public void setCount(long count) {
        WorldPartition.checkOwner();
        if (this.count == count) {
            return;
        }

        this.count = count >= -1 ? count : 0;
        storedInDb = false;
    }

    public void changeCount(ItemProcessType process, long count, Player creator, Object reference) {
        WorldPartition.checkOwner();
        if (count == 0) {
            return;
        }

        final long old = this.count;
        final long max = id == ADENA_ID ? PlayerConfig.MAX_ADENA : Long.MAX_VALUE;
        if ((count > 0) && (this.count > (max - count))) {
            setCount(max);
        } else {
            setCount(this.count + count);
        }

        if (this.count < 0) {
            setCount(0);
        }

        storedInDb = false;

        if ((process != null) && (process != ItemProcessType.NONE)) {
            if ((GeneralConfig.LOG_ITEMS
                            && ((!GeneralConfig.LOG_ITEMS_SMALL_LOG) && (!GeneralConfig.LOG_ITEMS_IDS_ONLY)))
                    || (GeneralConfig.LOG_ITEMS_SMALL_LOG && (template.isEquipable() || (template.getId() == ADENA_ID)))
                    || (GeneralConfig.LOG_ITEMS_IDS_ONLY
                            && GeneralConfig.LOG_ITEMS_IDS_LIST.contains(template.getId()))) {
                if (enchantLevel > 0) {
                    ITEM_LOG.info(
                            "CHANGE:{}, item {}:+{} {}({}), PrevCount({}), {}, {}",
                            process,
                            getObjectId(),
                            enchantLevel,
                            template.getName(),
                            this.count,
                            old,
                            creator,
                            reference);
                } else {
                    ITEM_LOG.info(
                            "CHANGE:{}, item {}:{}({}), PrevCount({}), {}, {}",
                            process,
                            getObjectId(),
                            template.getName(),
                            this.count,
                            old,
                            creator,
                            reference);
                }
            }

            if ((creator != null) && creator.isGM() && GeneralConfig.GMAUDIT) {
                final String targetName =
                        (creator.getTarget() != null ? creator.getTarget().getName() : "no-target");
                String referenceName = "no-reference";
                if (reference instanceof WorldObject) {
                    referenceName = ((WorldObject) reference).getName() != null
                            ? ((WorldObject) reference).getName()
                            : "no-name";
                } else if (reference instanceof String) {
                    referenceName = (String) reference;
                }

                GMAudit.logAction(
                        creator.toString(),
                        process + "(id: " + id + " objId: " + getObjectId() + " name: " + getName() + " count: " + count
                                + ")",
                        targetName,
                        "Object referencing this action is: " + referenceName);
            }
        }
    }

    public boolean isEnchantable() {
        WorldPartition.checkOwner();
        if ((itemLocation == ItemLocation.INVENTORY) || (itemLocation == ItemLocation.PAPERDOLL)) {
            return template.isEnchantable();
        }

        return false;
    }

    public boolean isEquipable() {
        WorldPartition.checkOwner();
        return (template.getBodyPart() != BodyPart.NONE)
                && (template.getItemType() != EtcItemType.ARROW)
                && (template.getItemType() != EtcItemType.BOLT)
                && (template.getItemType() != EtcItemType.LURE);
    }

    public boolean isEquipped() {
        WorldPartition.checkOwner();
        return (itemLocation == ItemLocation.PAPERDOLL) || (itemLocation == ItemLocation.PET_EQUIP);
    }

    public ItemType getItemType() {
        WorldPartition.checkOwner();
        return template.getItemType();
    }

    public int getDisplayId() {
        WorldPartition.checkOwner();
        return template.getDisplayId();
    }

    public boolean isEtcItem() {
        WorldPartition.checkOwner();
        return template instanceof EtcItem;
    }

    public boolean isWeapon() {
        WorldPartition.checkOwner();
        return template instanceof Weapon;
    }

    public boolean isArmor() {
        WorldPartition.checkOwner();
        return template instanceof Armor;
    }

    public EtcItem getEtcItem() {
        WorldPartition.checkOwner();
        if (template instanceof EtcItem) {
            return (EtcItem) template;
        }

        return null;
    }

    public Weapon getWeaponItem() {
        WorldPartition.checkOwner();
        if (template instanceof Weapon) {
            return (Weapon) template;
        }

        return null;
    }

    public Armor getArmorItem() {
        WorldPartition.checkOwner();
        if (template instanceof Armor) {
            return (Armor) template;
        }

        return null;
    }

    public int getCrystalCount() {
        WorldPartition.checkOwner();
        return template.getCrystalCount(enchantLevel);
    }

    public int getReferencePrice() {
        WorldPartition.checkOwner();
        return template.getReferencePrice();
    }

    public String getItemName() {
        WorldPartition.checkOwner();
        return template.getName();
    }

    public int getReuseDelay() {
        WorldPartition.checkOwner();
        return template.getReuseDelay();
    }

    public int getSharedReuseGroup() {
        WorldPartition.checkOwner();
        return template.getSharedReuseGroup();
    }

    public boolean isStackable() {
        WorldPartition.checkOwner();
        return template.isStackable();
    }

    public boolean isDropable() {
        WorldPartition.checkOwner();
        if (!template.isDropable()) {
            return false;
        }

        if (isEquipable() && (getTransmogId() > 0)) {
            return false;
        }

        if (isAugmented()) {
            return PlayerConfig.ALT_ALLOW_AUGMENT_TRADE;
        }

        return true;
    }

    public boolean isDestroyable() {
        WorldPartition.checkOwner();
        if (!template.isDestroyable()) {
            return false;
        }

        if (isAugmented()) {
            return PlayerConfig.ALT_ALLOW_AUGMENT_DESTROY;
        }

        return true;
    }

    public boolean isTradeable() {
        WorldPartition.checkOwner();
        if (!template.isTradeable()) {
            return false;
        }

        if (isEquipable() && (getTransmogId() > 0)) {
            return false;
        }

        if (isAugmented()) {
            return PlayerConfig.ALT_ALLOW_AUGMENT_TRADE;
        }

        return true;
    }

    public boolean isSellable() {
        WorldPartition.checkOwner();
        if (!template.isSellable()) {
            return false;
        }

        if (isEquipable() && (getTransmogId() > 0)) {
            return false;
        }

        if (isAugmented()) {
            return PlayerConfig.ALT_ALLOW_AUGMENT_TRADE;
        }

        return true;
    }

    public boolean isDepositable(boolean isPrivateWareHouse) {
        WorldPartition.checkOwner();
        if (!template.isDepositable() || isEquipped()) {
            return false;
        }

        if (!isPrivateWareHouse && (!isTradeable() || isShadowItem())) {
            return false;
        }

        return true;
    }

    public boolean isPotion() {
        WorldPartition.checkOwner();
        return template.isPotion();
    }

    public boolean isElixir() {
        WorldPartition.checkOwner();
        return template.isElixir();
    }

    public boolean isScroll() {
        WorldPartition.checkOwner();
        return template.isScroll();
    }

    public boolean isHeroItem() {
        WorldPartition.checkOwner();
        return template.isHeroItem();
    }

    public boolean isCommonItem() {
        WorldPartition.checkOwner();
        return template.isCommon();
    }

    public boolean isPvp() {
        WorldPartition.checkOwner();
        return template.isPvpItem();
    }

    public boolean isOlyRestrictedItem() {
        WorldPartition.checkOwner();
        return template.isOlyRestrictedItem();
    }

    public boolean isAvailable(Player player, boolean allowAdena, boolean allowNonTradeable) {
        WorldPartition.checkOwner();
        return ((!isEquipped())
                && (template.getType2() != ItemTemplate.TYPE2_QUEST)
                && ((template.getType2() != ItemTemplate.TYPE2_MONEY)
                        || (template.getType1() != ItemTemplate.TYPE1_SHIELD_ARMOR))
                && (!player.hasSummon() || (getObjectId() != player.getSummon().getControlObjectId()))
                && (player.getActiveEnchantItemId() != getObjectId())
                && (player.getActiveEnchantSupportItemId() != getObjectId())
                && (player.getActiveEnchantAttrItemId() != getObjectId())
                && (allowAdena || (id != ADENA_ID))
                && ((player.getCurrentSkill() == null)
                        || (player.getCurrentSkill().getSkill().getItemConsumeId() != id))
                && (!player.isCastingSimultaneouslyNow()
                        || (player.getLastSimultaneousSkillCast() == null)
                        || (player.getLastSimultaneousSkillCast().getItemConsumeId() != id))
                && (allowNonTradeable
                        || (isTradeable()
                                && (!((template.getItemType() == EtcItemType.PET_COLLAR)
                                        && player.havePetInvItems())))));
    }

    public boolean isEnchanted() {
        WorldPartition.checkOwner();
        return enchantLevel > 0;
    }

    public void setEnchantLevel(int level) {
        WorldPartition.checkOwner();
        final int newLevel = Math.max(0, level);
        if (enchantLevel == newLevel) {
            return;
        }

        clearEnchantStats();
        enchantLevel = newLevel;
        applyEnchantStats();
        storedInDb = false;
    }

    public boolean isAugmented() {
        WorldPartition.checkOwner();
        return augmentation != null;
    }

    public boolean setAugmentation(Augmentation augmentation) {
        WorldPartition.checkOwner();
        if (this.augmentation != null) {
            log.info("Warning: Augment set for ({}) {} owner: {}", getObjectId(), getName(), ownerId);
            return false;
        }

        this.augmentation = augmentation;
        if (existsInDb) {
            try {
                ItemManager.getInstance().saveAugmentation(getObjectId(), augmentation.getAugmentationId());
            } catch (DataAccessException e) {
                log.error("Could not update atributes for item: {} from DB:", this, e);
            }
        }

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_AUGMENT, getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(new OnPlayerAugment(asPlayer(), this, augmentation, true), getTemplate());
        }

        return true;
    }

    public void removeAugmentation() {
        WorldPartition.checkOwner();
        if (augmentation == null) {
            return;
        }

        final Augmentation augment = augmentation;
        augmentation = null;

        if (existsInDb) {
            try {
                ItemManager.getInstance().deleteAugmentation(getObjectId());
            } catch (DataAccessException e) {
                log.error("Could not remove augmentation for item: {} from DB:", this, e);
            }
        }

        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_AUGMENT, getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(new OnPlayerAugment(asPlayer(), this, augment, false), getTemplate());
        }
    }

    public void restoreAttributes() {
        WorldPartition.checkOwner();
        try {
            ItemManager.getInstance()
                    .findAugmentation(getObjectId())
                    .ifPresent(augmentationId -> augmentation = new Augmentation(augmentationId));
            for (ItemElemental elemental : ItemManager.getInstance().findElementals(getObjectId())) {
                applyAttribute(elemental.element(), elemental.value());
            }
        } catch (DataAccessException e) {
            log.error("Could not restore augmentation and elemental data for item {} from DB:", this, e);
        }
    }

    private List<ItemElemental> elementalRows() {
        final List<ItemElemental> rows = new ArrayList<>();
        if (elementals != null) {
            for (Elementals elemental : elementals) {
                rows.add(ItemElemental.builder()
                        .element(elemental.getElement())
                        .value(elemental.getValue())
                        .build());
            }
        }

        return rows;
    }

    public Elementals getElemental(byte attribute) {
        WorldPartition.checkOwner();
        if (elementals == null) {
            return null;
        }

        for (Elementals elm : elementals) {
            if (elm.getElement() == attribute) {
                return elm;
            }
        }

        return null;
    }

    public byte getAttackElementType() {
        WorldPartition.checkOwner();
        if (!isWeapon()) {
            return -2;
        } else if (template.getElementals() != null) {
            return template.getElementals()[0].getElement();
        } else if (elementals != null) {
            return elementals[0].getElement();
        }

        return -2;
    }

    public int getAttackElementPower() {
        WorldPartition.checkOwner();
        if (!isWeapon()) {
            return 0;
        } else if (template.getElementals() != null) {
            return template.getElementals()[0].getValue();
        } else if (elementals != null) {
            return elementals[0].getValue();
        }

        return 0;
    }

    public int getElementDefAttr(byte element) {
        WorldPartition.checkOwner();
        if (!isArmor()) {
            return 0;
        } else if (template.getElementals() != null) {
            final Elementals elm = template.getElemental(element);
            if (elm != null) {
                return elm.getValue();
            }
        } else if (elementals != null) {
            final Elementals elm = getElemental(element);
            if (elm != null) {
                return elm.getValue();
            }
        }

        return 0;
    }

    private void applyAttribute(byte element, int value) {
        if (elementals == null) {
            elementals = new Elementals[1];
            elementals[0] = new Elementals(element, value);
        } else {
            Elementals elm = getElemental(element);
            if (elm != null) {
                elm.setValue(value);
            } else {
                elm = new Elementals(element, value);
                final Elementals[] array = new Elementals[elementals.length + 1];
                System.arraycopy(elementals, 0, array, 0, elementals.length);
                array[elementals.length] = elm;
                elementals = array;
            }
        }
    }

    public void setElementAttr(byte element, int value) {
        WorldPartition.checkOwner();
        applyAttribute(element, value);
        if (existsInDb) {
            try {
                ItemManager.getInstance().saveElementals(getObjectId(), elementalRows());
            } catch (DataAccessException e) {
                log.error("Could not update elementals for item: {} from DB:", this, e);
            }
        }
    }

    public void clearElementAttr(byte element) {
        WorldPartition.checkOwner();
        if ((getElemental(element) == null) && (element != -1)) {
            return;
        }

        Elementals[] array = null;
        if ((element != -1) && (elementals != null) && (elementals.length > 1)) {
            array = new Elementals[elementals.length - 1];
            int i = 0;
            for (Elementals elm : elementals) {
                if (elm.getElement() != element) {
                    array[i++] = elm;
                }
            }
        }

        elementals = array;

        if (!existsInDb) {
            return;
        }

        try {
            if (element != -1) {
                ItemManager.getInstance().deleteElemental(getObjectId(), element);
            } else {
                ItemManager.getInstance().deleteElementals(getObjectId());
            }
        } catch (DataAccessException e) {
            log.error("Could not remove elemental enchant for item: {} from DB:", this, e);
        }
    }

    public boolean isShadowItem() {
        WorldPartition.checkOwner();
        return mana >= 0;
    }

    public void decreaseMana(boolean resetConsumingMana) {
        WorldPartition.checkOwner();
        decreaseMana(resetConsumingMana, 1);
    }

    public void decreaseMana(boolean resetConsumingMana, int count) {
        WorldPartition.checkOwner();
        if (!isShadowItem()) {
            return;
        }

        if ((mana - count) >= 0) {
            mana -= count;
        } else {
            mana = 0;
        }

        if (storedInDb) {
            storedInDb = false;
        }

        if (resetConsumingMana) {
            consumingMana = false;
        }

        final Player player = asPlayer();
        if (player == null) {
            return;
        }

        SystemMessage sm;
        switch (mana) {
            case 10: {
                sm = new SystemMessage(SystemMessageId.S1_S_REMAINING_MANA_IS_NOW_10);
                sm.addItemName(template);
                player.sendPacket(sm);
                break;
            }
            case 5: {
                sm = new SystemMessage(SystemMessageId.S1_S_REMAINING_MANA_IS_NOW_5);
                sm.addItemName(template);
                player.sendPacket(sm);
                break;
            }
            case 1: {
                sm = new SystemMessage(SystemMessageId.S1_S_REMAINING_MANA_IS_NOW_1_IT_WILL_DISAPPEAR_SOON);
                sm.addItemName(template);
                player.sendPacket(sm);
                break;
            }
        }

        if (mana == 0) {
            sm = new SystemMessage(SystemMessageId.S1_S_REMAINING_MANA_IS_NOW_0_AND_THE_ITEM_HAS_DISAPPEARED);
            sm.addItemName(template);
            player.sendPacket(sm);

            if (isEquipped()) {
                final InventoryUpdate iu = new InventoryUpdate();
                for (Item item : player.getInventory().unEquipItemInSlotAndRecord(getLocationSlot())) {
                    item.unChargeAllShots();
                    iu.addModifiedItem(item);
                }

                player.sendInventoryUpdate(iu);
                player.broadcastUserInfo();
            }

            if (itemLocation != ItemLocation.WAREHOUSE) {
                player.getInventory().destroyItem(ItemProcessType.DESTROY, this, player, null);

                final InventoryUpdate iu = new InventoryUpdate();
                iu.addRemovedItem(this);
                player.sendInventoryUpdate(iu);

                final StatusUpdate su = new StatusUpdate(player);
                su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
                player.sendPacket(su);
            } else {
                player.getWarehouse().destroyItem(ItemProcessType.DESTROY, this, player, null);
            }

        } else {
            if (!consumingMana && isEquipped()) {
                scheduleConsumeManaTask();
            }

            if (itemLocation != ItemLocation.WAREHOUSE) {
                final InventoryUpdate iu = new InventoryUpdate();
                iu.addModifiedItem(this);
                player.sendInventoryUpdate(iu);
            }
        }
    }

    public void scheduleConsumeManaTask() {
        WorldPartition.checkOwner();
        if (consumingMana) {
            return;
        }

        consumingMana = true;
        ItemManaTaskManager.getInstance().add(this);
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        return false;
    }

    public List<AbstractFunction> getStatFuncs(Creature creature) {
        WorldPartition.checkOwner();
        return template.getStatFuncs(this, creature);
    }

    public void updateDatabase() {
        WorldPartition.checkOwner();
        updateDatabase(false);
    }

    public void updateDatabase(boolean force) {
        WorldPartition.checkOwner();
        if (isPending()) {
            return;
        }

        if (existsInDb) {
            if ((ownerId == 0)
                    || (itemLocation == ItemLocation.VOID)
                    || (itemLocation == ItemLocation.REFUND)
                    || ((count == 0) && (itemLocation != ItemLocation.LEASE))) {
                removeFromDb();
            } else if (!GeneralConfig.LAZY_ITEMS_UPDATE || force) {
                updateInDb();
            }
        } else {
            if ((ownerId == 0)
                    || (itemLocation == ItemLocation.VOID)
                    || (itemLocation == ItemLocation.REFUND)
                    || ((count == 0) && (itemLocation != ItemLocation.LEASE))) {
                return;
            }

            insertIntoDb();
        }
    }

    /**
     * Whether a value transaction in flight reserved the item or awaits a new stackable row; see
     * {@link ItemContainer} for how pending items are treated.
     */
    public boolean isPending() {
        WorldPartition.checkOwner();
        return WorldPartition.pendingItems().isPending(getObjectId())
                || (!existsInDb && WorldPartition.awaitedStacks().isAwaited(ownerId, id));
    }

    public boolean hasRow() {
        WorldPartition.checkOwner();
        return existsInDb;
    }

    public void markRowStored() {
        WorldPartition.checkOwner();
        existsInDb = true;
        storedInDb = false;
    }

    /**
     * @return the stored item, or {@code null} when its template is unknown
     */
    public static Item restoreFromDb(int ownerId, ItemRow row) {
        WorldPartition.checkOwner();
        final Item inst = fromRow(ownerId, row);
        if ((inst != null) && inst.isEquipable()) {
            inst.restoreAttributes();
        }

        return inst;
    }

    public static Item restoreCommitted(
            int ownerId, ItemRow row, Integer augmentationId, List<ItemElemental> elementals) {
        WorldPartition.checkOwner();
        final Item inst = fromRow(ownerId, row);
        if (inst == null) {
            return null;
        }

        if (augmentationId != null) {
            inst.augmentation = new Augmentation(augmentationId);
        }

        for (ItemElemental elemental : elementals) {
            inst.applyAttribute(elemental.element(), elemental.value());
        }

        return inst;
    }

    private static Item fromRow(int ownerId, ItemRow row) {
        final ItemTemplate item = ItemData.getInstance().getTemplate(row.itemId());
        if (item == null) {
            log.error("Item item_id={} not known, object_id={}", row.itemId(), row.objectId());
            return null;
        }

        final Item inst = new Item(row.objectId(), item);
        inst.ownerId = ownerId;
        inst.setCount(row.count());
        inst.enchantLevel = row.enchantLevel();
        inst.customType1 = row.customType1();
        inst.customType2 = row.customType2();
        inst.itemLocation = row.location();
        inst.locationSlot = row.locationSlot();
        inst.existsInDb = true;
        inst.storedInDb = true;

        inst.mana = row.mana();
        inst.time = row.expiresAt() == null ? -1 : row.expiresAt().toEpochMilli();
        return inst;
    }

    // Does not add the item to the World all-objects registry.
    public void dropMe(Creature dropper, int locX, int locY, int locZ) {
        WorldPartition.checkOwner();
        int x = locX;
        int y = locY;
        int z = locZ;

        if (dropper != null) {
            final Location dropDest = GeoEngine.getInstance()
                    .getValidLocation(dropper.getX(), dropper.getY(), dropper.getZ(), x, y, z, dropper.getInstanceId());
            x = dropDest.getX();
            y = dropDest.getY();
            z = dropDest.getZ();
        }

        if (dropper != null) {
            setInstanceId(dropper.getInstanceId());
        } else {
            setInstanceId(0);
        }

        setSpawned(true);
        setXYZ(x, y, z);

        setDropTime(ClockBridge.wallMillis());
        setDropperObjectId(dropper != null ? dropper.getObjectId() : 0);

        final WorldRegion region = getWorldRegion();
        region.addVisibleObject(this);
        World.addVisibleObject(this, region);
        if (GeneralConfig.SAVE_DROPPED_ITEM) {
            ItemsOnGroundManager.getInstance().save(this);
        }

        setDropperObjectId(0); // Set the dropper Id back to 0 so it no longer shows the drop packet.

        if ((dropper != null) && dropper.isPlayer()) {
            owner = null;

            if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_ITEM_DROP, getTemplate())) {
                EventDispatcher.getInstance()
                        .notifyEventAsync(
                                OnPlayerItemDrop.builder()
                                        .player(dropper.asPlayer())
                                        .item(this)
                                        .location(new Location(x, y, z))
                                        .build(),
                                getTemplate());
            }
        }
    }

    private ItemRow toRow() {
        return ItemRow.builder()
                .objectId(getObjectId())
                .itemId(id)
                .count(count)
                .location(itemLocation)
                .locationSlot(locationSlot)
                .enchantLevel(enchantLevel)
                .customType1(customType1)
                .customType2(customType2)
                .mana(mana)
                .expiresAt(instantOrNull(time))
                .build();
    }

    private void updateInDb() {
        if (!existsInDb || wear || storedInDb) {
            return;
        }

        try {
            ItemManager.getInstance().updateItem(ownerId, toRow());
            existsInDb = true;
            storedInDb = true;
        } catch (DataAccessException e) {
            log.error("Could not update item {} in DB:", this, e);
        }
    }

    private void insertIntoDb() {
        if (existsInDb || (getObjectId() == 0) || wear) {
            return;
        }

        try {
            ItemManager.getInstance()
                    .insertItem(
                            ownerId,
                            toRow(),
                            augmentation != null ? augmentation.getAugmentationId() : null,
                            elementalRows());
            existsInDb = true;
            storedInDb = true;
        } catch (DataAccessException e) {
            log.error("Could not insert item {} into DB:", this, e);
        }
    }

    private void removeFromDb() {
        if (!existsInDb || wear) {
            return;
        }

        try {
            ItemManager.getInstance().deleteItem(getObjectId());
            existsInDb = false;
            storedInDb = false;
        } catch (DataAccessException e) {
            log.error("Could not delete item {} in DB:", this, e);
        }
    }

    public void resetOwnerTimer() {
        WorldPartition.checkOwner();
        if (itemLootShedule != null) {
            itemLootShedule.cancel(true);
            itemLootShedule = null;
        }
    }

    public void setProtected(boolean isProtected) {
        WorldPartition.checkOwner();
        protectedItem = isProtected;
    }

    public boolean isProtected() {
        WorldPartition.checkOwner();
        return protectedItem;
    }

    public boolean isNightLure() {
        WorldPartition.checkOwner();
        return (((id >= 8505) && (id <= 8513)) || (id == 8485));
    }

    public boolean getCountDecrease() {
        WorldPartition.checkOwner();
        return countDecrease;
    }

    public void setInitCount(int initCount) {
        WorldPartition.checkOwner();
        this.initCount = initCount;
    }

    public void restoreInitCount() {
        WorldPartition.checkOwner();
        if (countDecrease) {
            setCount(initCount);
        }
    }

    public boolean isTimeLimitedItem() {
        WorldPartition.checkOwner();
        return time > 0;
    }

    public long getRemainingTime() {
        WorldPartition.checkOwner();
        return time - ClockBridge.wallMillis();
    }

    public void endOfLife() {
        WorldPartition.checkOwner();
        final Player player = asPlayer();
        if (player == null) {
            return;
        }

        if (isEquipped()) {
            final InventoryUpdate iu = new InventoryUpdate();
            for (Item item : player.getInventory().unEquipItemInSlotAndRecord(getLocationSlot())) {
                item.unChargeAllShots();
                iu.addModifiedItem(item);
            }

            player.sendInventoryUpdate(iu);
            player.broadcastUserInfo();
        }

        if (itemLocation != ItemLocation.WAREHOUSE) {
            player.getInventory().destroyItem(ItemProcessType.DESTROY, this, player, null);

            final InventoryUpdate iu = new InventoryUpdate();
            iu.addRemovedItem(this);
            player.sendInventoryUpdate(iu);

            final StatusUpdate su = new StatusUpdate(player);
            su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
            player.sendPacket(su);
        } else {
            player.getWarehouse().destroyItem(ItemProcessType.DESTROY, this, player, null);
        }

        player.sendPacket(SystemMessageId.THE_LIMITED_TIME_ITEM_HAS_DISAPPEARED_BECAUSE_THE_REMAINING_TIME_RAN_OUT);
    }

    public void scheduleLifeTimeTask() {
        WorldPartition.checkOwner();
        if (!isTimeLimitedItem()) {
            return;
        }

        if (getRemainingTime() <= 0) {
            endOfLife();
        } else {
            ItemLifeTimeTaskManager.getInstance().add(this, getTime());
        }
    }

    // Walks of the attribute array need no copy: a bonus change touches the player's stats, never the item's
    // attributes.
    public void updateElementAttrBonus(Player player) {
        WorldPartition.checkOwner();
        if (elementals == null) {
            return;
        }

        for (Elementals elm : elementals) {
            elm.updateBonus(player, isArmor());
        }
    }

    public void removeElementAttrBonus(Player player) {
        WorldPartition.checkOwner();
        if (elementals == null) {
            return;
        }

        for (Elementals elm : elementals) {
            elm.removeBonus(player);
        }
    }

    @Override
    public void sendInfo(Player player) {
        WorldPartition.checkOwner();
        if (dropperObjectId != 0) {
            player.sendPacket(new DropItem(this, dropperObjectId));
        } else {
            player.sendPacket(new SpawnItem(this));
        }
    }

    public void publish() {
        WorldPartition.checkOwner();
        published = true;
    }

    @Override
    public boolean decayMe() {
        WorldPartition.checkOwner();
        if (GeneralConfig.SAVE_DROPPED_ITEM) {
            ItemsOnGroundManager.getInstance().removeObject(this);
        }

        return super.decayMe();
    }

    public boolean isQuestItem() {
        WorldPartition.checkOwner();
        return template.isQuestItem();
    }

    public boolean isElementable() {
        WorldPartition.checkOwner();
        return ((itemLocation == ItemLocation.INVENTORY) || (itemLocation == ItemLocation.PAPERDOLL))
                && template.isElementable();
    }

    public boolean isFreightable() {
        WorldPartition.checkOwner();
        return template.isFreightable();
    }

    public int useSkillDisTime() {
        WorldPartition.checkOwner();
        return template.useSkillDisTime();
    }

    public int getOlyEnchantLevel() {
        WorldPartition.checkOwner();
        final Player player = asPlayer();
        int enchant = enchantLevel;

        if (player == null) {
            return enchant;
        }

        if (player.isInOlympiadMode()
                && (OlympiadConfig.OLYMPIAD_ENCHANT_LIMIT >= 0)
                && (enchant > OlympiadConfig.OLYMPIAD_ENCHANT_LIMIT)) {
            enchant = OlympiadConfig.OLYMPIAD_ENCHANT_LIMIT;
        }

        return enchant;
    }

    public int getDefaultEnchantLevel() {
        WorldPartition.checkOwner();
        return template.getDefaultEnchantLevel();
    }

    public boolean hasPassiveSkills() {
        WorldPartition.checkOwner();
        return (template.getItemType() == EtcItemType.RUNE)
                && (itemLocation == ItemLocation.INVENTORY)
                && (ownerId > 0)
                && template.hasSkills();
    }

    public void giveSkillsToOwner() {
        WorldPartition.checkOwner();
        if (!template.hasSkills()) {
            return;
        }

        if (!isEquipped() && !hasPassiveSkills()) {
            return;
        }

        final Player player = asPlayer();
        if (player == null) {
            return;
        }

        for (SkillHolder holder : template.getSkills()) {
            final Skill skill = holder.getSkill();
            if (skill.isPassive()) {
                player.addSkill(skill, false);
            }
        }
    }

    public void removeSkillsFromOwner() {
        WorldPartition.checkOwner();
        if (!hasPassiveSkills()) {
            return;
        }

        final Player player = asPlayer();
        if (player != null) {
            for (SkillHolder holder : template.getSkills()) {
                final Skill skill = holder.getSkill();
                if (skill.isPassive()) {
                    player.removeSkill(skill, false, true);
                }
            }
        }
    }

    @Override
    public boolean isItem() {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public Player asPlayer() {
        WorldPartition.checkOwner();
        if ((owner == null) && (ownerId != 0)) {
            owner = World.getPlayer(ownerId);
        }

        return owner;
    }

    public int getEquipReuseDelay() {
        WorldPartition.checkOwner();
        return template.getEquipReuseDelay();
    }

    public void onBypassFeedback(Player player, String command) {
        WorldPartition.checkOwner();
        if (!command.startsWith("Script")) {
            return;
        }

        final String questName = command.substring(6);
        String event = null;
        final int idx = questName.indexOf(' ');
        if (idx > 0) {
            event = questName.substring(idx).trim();
        }

        if (event != null) {
            if (EventDispatcher.getInstance().hasListener(EventType.ON_ITEM_BYPASS_EVENT, getTemplate())) {
                EventDispatcher.getInstance()
                        .notifyEventAsync(
                                OnItemBypassEvent.builder()
                                        .item(this)
                                        .player(player)
                                        .event(event)
                                        .build(),
                                getTemplate());
            }
        } else if (EventDispatcher.getInstance().hasListener(EventType.ON_ITEM_TALK, getTemplate())) {
            EventDispatcher.getInstance().notifyEventAsync(new OnItemTalk(this, player), getTemplate());
        }
    }

    @Override
    public boolean isChargedShot(ShotType type) {
        WorldPartition.checkOwner();
        return (shotsMask & type.getMask()) == type.getMask();
    }

    @Override
    public void setChargedShot(ShotType type, boolean charged) {
        WorldPartition.checkOwner();
        if (charged) {
            shotsMask |= type.getMask();
        } else {
            shotsMask &= ~type.getMask();
        }
    }

    public void unChargeAllShots() {
        WorldPartition.checkOwner();
        shotsMask = 0;
    }

    public int[] getEnchantOptions() {
        WorldPartition.checkOwner();
        final EnchantOptions op = EnchantItemOptionsData.getInstance().getOptions(this);
        if (op != null) {
            return op.getOptions();
        }

        return DEFAULT_ENCHANT_OPTIONS;
    }

    public void clearEnchantStats() {
        WorldPartition.checkOwner();
        final Player player = asPlayer();
        if (player == null) {
            enchantOptions.clear();
            return;
        }

        try (ScratchBuffers.Snapshot<Options> applied = ScratchBuffers.snapshot(enchantOptions)) {
            for (Options op : applied) {
                op.remove(player);
            }
        }

        enchantOptions.clear();
    }

    public void applyEnchantStats() {
        WorldPartition.checkOwner();
        final Player player = asPlayer();
        if (!isEquipped() || (player == null) || (getEnchantOptions() == DEFAULT_ENCHANT_OPTIONS)) {
            return;
        }

        for (int id : getEnchantOptions()) {
            final Options options = OptionData.getInstance().getOptions(id);
            if (options != null) {
                options.apply(player);
                enchantOptions.add(options);
            } else if (id != 0) {
                log.info("Item applyEnchantStats could not find option {} {} {}", id, this, player);
            }
        }
    }

    @Override
    public void setHeading(int heading) {
        WorldPartition.checkOwner();
    }

    public void stopAllTasks() {
        WorldPartition.checkOwner();
        ItemLifeTimeTaskManager.getInstance().remove(this);
    }

    public ItemVariables getVariables() {
        WorldPartition.checkOwner();
        final ItemVariables vars = getScript(ItemVariables.class);
        return vars != null ? vars : addScript(new ItemVariables(getObjectId()));
    }

    public int getTransmogId() {
        WorldPartition.checkOwner();
        if (!TransmogConfig.ENABLE_TRANSMOG) {
            return 0;
        }

        return getVariables().getInt(ItemVariables.TRANSMOG_ID, 0);
    }

    public void setTransmogId(int transmogId) {
        WorldPartition.checkOwner();
        getVariables().set(ItemVariables.TRANSMOG_ID, transmogId);
        getVariables().storeMe();
    }

    public void removeTransmog() {
        WorldPartition.checkOwner();
        getVariables().remove(ItemVariables.TRANSMOG_ID);
        getVariables().storeMe();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(template);
        sb.append('[');
        sb.append(uncheckedObjectId());
        sb.append(']');
        return sb.toString();
    }
}
