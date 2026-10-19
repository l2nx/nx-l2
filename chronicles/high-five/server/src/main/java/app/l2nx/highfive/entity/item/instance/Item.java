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
import static app.l2nx.highfive.entity.itemcontainer.Inventory.MAX_ADENA;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.OlympiadConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.custom.TransmogConfig;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.data.xml.EnchantItemOptionsData;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.data.xml.OptionData;
import app.l2nx.highfive.entity.Location;
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
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.managers.IdManager;
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
import app.l2nx.highfive.network.serverpackets.DropItem;
import app.l2nx.highfive.network.serverpackets.GetItem;
import app.l2nx.highfive.network.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.serverpackets.SpawnItem;
import app.l2nx.highfive.network.serverpackets.StatusUpdate;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.taskmanagers.ItemLifeTimeTaskManager;
import app.l2nx.highfive.taskmanagers.ItemManaTaskManager;
import app.l2nx.highfive.util.GMAudit;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class manages items.
 * @version $Revision: 1.4.2.1.2.11 $ $Date: 2005/03/31 16:07:50 $
 */
@Slf4j
public class Item extends WorldObject {

    private static final Logger ITEM_LOG = LoggerFactory.getLogger("item");

    /** Owner */
    @Getter
    private int ownerId;

    private Player owner;

    /** ID of who dropped the item last, used for knownlist */
    @Setter
    private int dropperObjectId = 0;

    /** Quantity of the item */
    @Getter
    private long count;
    /** Initial Quantity of the item */
    @Getter
    private long initCount;
    /** Remaining time (in miliseconds) */
    @Getter
    private long time;
    /** Quantity of the item can decrease */
    @Setter
    private boolean countDecrease = false;

    /** ID of the item */
    @Getter
    private final int id;

    /** ItemTemplate associated to the item */
    @Getter
    private final ItemTemplate template;

    /** Location of the item : Inventory, PaperDoll, WareHouse */
    @Getter
    private ItemLocation itemLocation;

    /** Slot where item is stored : Paperdoll slot, inventory order ... */
    @Getter
    private int locationSlot;

    /** Level of enchantment of the item */
    @Getter
    private int enchantLevel;

    /** Wear Item */
    private boolean wear;

    /** Augmented Item */
    @Getter
    private Augmentation augmentation = null;

    /** Shadow item */
    @Getter
    private int mana = -1;

    private boolean consumingMana = false;

    /** Custom item types (used loto, race tickets) */
    @Getter
    @Setter
    private int customType1;

    @Getter
    @Setter
    private int customType2;

    @Getter
    @Setter
    private long dropTime;

    @Getter
    private boolean published = false;

    private boolean protectedItem;

    public static final int UNCHANGED = 0;
    public static final int ADDED = 1;
    public static final int REMOVED = 3;
    public static final int MODIFIED = 2;

    // @formatter:off
    public static final int[] DEFAULT_ENCHANT_OPTIONS = {0, 0, 0};
    // @formatter:on

    @Getter
    @Setter
    private int lastChange = 2; // 1 ??, 2 modified, 3 removed

    private boolean existsInDb; // If a record exists in DB.
    private boolean storedInDb; // If DB data is up-to-date.

    private final ReentrantLock dbLock = new ReentrantLock();

    @Getter
    private Elementals[] elementals = null;

    @Getter
    @Setter
    private ScheduledFuture<?> itemLootShedule = null;

    @Getter
    private final DropProtection dropProtection = new DropProtection();

    private int shotsMask = 0;

    private final List<Options> enchantOptions = new ArrayList<>();

    /**
     * Constructor of the Item from the objectId and the itemId.
     * @param objectId : int designating the ID of the object in the world
     * @param itemId : int designating the ID of the item
     */
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
        time = template.getTime() == -1 ? -1 : System.currentTimeMillis() + (template.getTime() * 60 * 1000);
        enchantLevel = 0;
        scheduleLifeTimeTask();
    }

    /**
     * Constructor of the Item from the objetId and the description of the item given by the Item.
     * @param objectId : int designating the ID of the object in the world
     * @param itemTemplate : Item containing informations of the item
     */
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
        time = template.getTime() == -1 ? -1 : System.currentTimeMillis() + (template.getTime() * 60 * 1000);
        scheduleLifeTimeTask();
    }

    /**
     * Constructor overload.<br>
     * Sets the next free object ID in the ID factory.
     * @param itemId the item template ID
     */
    public Item(int itemId) {
        this(IdManager.getInstance().getNextId(), itemId);
    }

    /**
     * Remove a Item from the world and send server->client GetItem packets.<br>
     * <br>
     * <b><u>Actions</u>:</b><br>
     * <li>Send a Server->Client Packet GetItem to player that pick up and its _knowPlayers member</li>
     * <li>Remove the WorldObject from the world</li><br>
     * <font color=#FF0000><b><u>Caution</u>: This method DOESN'T REMOVE the object from _allObjects of World </b></font><br>
     * <br>
     * <b><u>Example of use</u>:</b><br>
     * <li>Do Pickup Item : Player and Pet</li><br>
     * @param creature Character that pick up the item
     */
    public void pickupMe(Creature creature) {
        final WorldRegion oldregion = getWorldRegion();

        // Create a server->client GetItem packet to pick up the Item.
        creature.broadcastPacket(new GetItem(this, creature.getObjectId()));

        synchronized (this) {
            setSpawned(false);
        }

        // If this item is a mercenary ticket, remove the spawns!

        if (MercTicketManager.getInstance().getTicketCastleId(id) > 0) {
            MercTicketManager.getInstance().removeTicket(this);
            ItemsOnGroundManager.getInstance().removeObject(this);
        }

        // Outside of synchronized to avoid deadlocks.
        // Remove the Item from the world.
        World.removeVisibleObject(this, oldregion);
        setWorldRegion(null);

        // Notify to scripts
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

    /**
     * Sets the ownerID of the item
     * @param process : ItemProcessType identifier of process triggering this action
     * @param ownerId : int designating the ID of the owner
     * @param creator : Player Player requesting the item creation
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     */
    public void setOwnerId(ItemProcessType process, int ownerId, Player creator, Object reference) {
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

    /**
     * Sets the ownerID of the item
     * @param ownerId : int designating the ID of the owner
     */
    public void setOwnerId(int ownerId) {
        if (ownerId == this.ownerId) {
            return;
        }

        // Remove any inventory skills from the old owner.
        removeSkillsFromOwner();

        owner = null;
        this.ownerId = ownerId;
        storedInDb = false;

        // Give any inventory skills to the new owner only if the item is in inventory.
        // else the skills will be given when location is set to inventory.
        giveSkillsToOwner();
    }

    /**
     * Sets the location of the item
     * @param loc : ItemLocation (enumeration)
     */
    public void setItemLocation(ItemLocation loc) {
        setItemLocation(loc, 0, true);
    }

    /**
     * Sets the location of the item
     * @param loc : ItemLocation (enumeration)
     * @param locData : int designating the slot where the item is stored or the village for freights
     */
    public void setItemLocation(ItemLocation loc, int locData) {
        setItemLocation(loc, locData, true);
    }

    /**
     * Sets the location of the item.<br>
     * <u><i>Remark :</i></u> If loc and loc_data different from database, say datas not up-to-date
     * @param loc : ItemLocation (enumeration)
     * @param locData : int designating the slot where the item is stored or the village for freights
     * @param checkSkills : boolean used to remove or give skills to owner.
     */
    public void setItemLocation(ItemLocation loc, int locData, boolean checkSkills) {
        if ((loc == itemLocation) && (locData == locationSlot)) {
            return;
        }

        if (checkSkills) {
            // Remove any inventory skills from the old owner.
            removeSkillsFromOwner();
        }

        itemLocation = loc;
        locationSlot = locData;
        storedInDb = false;

        if (checkSkills) {
            // Give any inventory skills to the new owner only if the item is in inventory.
            // else the skills will be given when location is set to inventory.
            giveSkillsToOwner();
        }
    }

    /**
     * Sets the quantity of the item.
     * @param count the new count to set
     */
    public void setCount(long count) {
        if (this.count == count) {
            return;
        }

        this.count = count >= -1 ? count : 0;
        storedInDb = false;
    }

    /**
     * Sets the quantity of the item.<br>
     * <u><i>Remark :</i></u> If loc and loc_data different from database, say datas not up-to-date
     * @param process : ItemProcessType identifier of process triggering this action
     * @param count : int
     * @param creator : Player Player requesting the item creation
     * @param reference : Object Object referencing current action like NPC selling item or previous item in transformation
     */
    public void changeCount(ItemProcessType process, long count, Player creator, Object reference) {
        if (count == 0) {
            return;
        }

        final long old = this.count;
        final long max = id == ADENA_ID ? MAX_ADENA : Long.MAX_VALUE;
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

    /**
     * Return true if item can be enchanted
     * @return boolean
     */
    public boolean isEnchantable() {
        if ((itemLocation == ItemLocation.INVENTORY) || (itemLocation == ItemLocation.PAPERDOLL)) {
            return template.isEnchantable();
        }

        return false;
    }

    /**
     * Returns if item is equipable
     * @return boolean
     */
    public boolean isEquipable() {
        return (template.getBodyPart() != BodyPart.NONE)
                && (template.getItemType() != EtcItemType.ARROW)
                && (template.getItemType() != EtcItemType.BOLT)
                && (template.getItemType() != EtcItemType.LURE);
    }

    /**
     * Returns if item is equipped
     * @return boolean
     */
    public boolean isEquipped() {
        return (itemLocation == ItemLocation.PAPERDOLL) || (itemLocation == ItemLocation.PET_EQUIP);
    }

    /**
     * @return the type of item.
     */
    public ItemType getItemType() {
        return template.getItemType();
    }

    /**
     * @return the display Id of the item.
     */
    public int getDisplayId() {
        return template.getDisplayId();
    }

    /**
     * @return {@code true} if item is an EtcItem, {@code false} otherwise.
     */
    public boolean isEtcItem() {
        return template instanceof EtcItem;
    }

    /**
     * @return {@code true} if item is a Weapon/Shield, {@code false} otherwise.
     */
    public boolean isWeapon() {
        return template instanceof Weapon;
    }

    /**
     * @return {@code true} if item is an Armor, {@code false} otherwise.
     */
    public boolean isArmor() {
        return template instanceof Armor;
    }

    /**
     * @return the characteristics of the EtcItem, {@code false} otherwise.
     */
    public EtcItem getEtcItem() {
        if (template instanceof EtcItem) {
            return (EtcItem) template;
        }

        return null;
    }

    /**
     * @return the characteristics of the Weapon.
     */
    public Weapon getWeaponItem() {
        if (template instanceof Weapon) {
            return (Weapon) template;
        }

        return null;
    }

    /**
     * @return the characteristics of the Armor.
     */
    public Armor getArmorItem() {
        if (template instanceof Armor) {
            return (Armor) template;
        }

        return null;
    }

    /**
     * @return the quantity of crystals for crystallization.
     */
    public int getCrystalCount() {
        return template.getCrystalCount(enchantLevel);
    }

    /**
     * @return the reference price of the item.
     */
    public int getReferencePrice() {
        return template.getReferencePrice();
    }

    /**
     * @return the name of the item.
     */
    public String getItemName() {
        return template.getName();
    }

    /**
     * @return the reuse delay of this item.
     */
    public int getReuseDelay() {
        return template.getReuseDelay();
    }

    /**
     * @return the shared reuse item group.
     */
    public int getSharedReuseGroup() {
        return template.getSharedReuseGroup();
    }

    /**
     * Returns if item is stackable
     * @return boolean
     */
    public boolean isStackable() {
        return template.isStackable();
    }

    /**
     * Returns if item is dropable
     * @return boolean
     */
    public boolean isDropable() {
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

    /**
     * Returns if item is destroyable
     * @return boolean
     */
    public boolean isDestroyable() {
        if (!template.isDestroyable()) {
            return false;
        }

        if (isAugmented()) {
            return PlayerConfig.ALT_ALLOW_AUGMENT_DESTROY;
        }

        return true;
    }

    /**
     * Returns if item is tradeable
     * @return boolean
     */
    public boolean isTradeable() {
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

    /**
     * Returns if item is sellable
     * @return boolean
     */
    public boolean isSellable() {
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

    /**
     * @param isPrivateWareHouse
     * @return if item can be deposited in warehouse or freight
     */
    public boolean isDepositable(boolean isPrivateWareHouse) {
        if (!template.isDepositable() || isEquipped()) {
            return false;
        }

        if (!isPrivateWareHouse && (!isTradeable() || isShadowItem())) {
            return false;
        }

        return true;
    }

    public boolean isPotion() {
        return template.isPotion();
    }

    public boolean isElixir() {
        return template.isElixir();
    }

    public boolean isScroll() {
        return template.isScroll();
    }

    public boolean isHeroItem() {
        return template.isHeroItem();
    }

    public boolean isCommonItem() {
        return template.isCommon();
    }

    /**
     * Returns whether this item is pvp or not
     * @return boolean
     */
    public boolean isPvp() {
        return template.isPvpItem();
    }

    public boolean isOlyRestrictedItem() {
        return template.isOlyRestrictedItem();
    }

    /**
     * @param player
     * @param allowAdena
     * @param allowNonTradeable
     * @return if item is available for manipulation
     */
    public boolean isAvailable(Player player, boolean allowAdena, boolean allowNonTradeable) {
        return ((!isEquipped()) // Not equipped
                && (template.getType2() != ItemTemplate.TYPE2_QUEST) // Not Quest Item
                && ((template.getType2() != ItemTemplate.TYPE2_MONEY)
                        || (template.getType1() != ItemTemplate.TYPE1_SHIELD_ARMOR)) // not money, not shield
                && (!player.hasSummon()
                        || (getObjectId()
                                != player.getSummon()
                                        .getControlObjectId())) // Not Control item of currently summoned pet.
                && (player.getActiveEnchantItemId() != getObjectId()) // Not momentarily used enchant scroll.
                && (player.getActiveEnchantSupportItemId()
                        != getObjectId()) // Not momentarily used enchant support item
                && (player.getActiveEnchantAttrItemId() != getObjectId()) // Not momentarily used enchant attribute item
                && (allowAdena || (id != ADENA_ID)) // Not Adena
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

    /**
     * @return {@code true} if item is enchanted, {@code false} otherwise
     */
    public boolean isEnchanted() {
        return enchantLevel > 0;
    }

    /**
     * @param level the enchant value to set
     */
    public void setEnchantLevel(int level) {
        final int newLevel = Math.max(0, level);
        if (enchantLevel == newLevel) {
            return;
        }

        clearEnchantStats();
        enchantLevel = newLevel;
        applyEnchantStats();
        storedInDb = false;
    }

    /**
     * Returns whether this item is augmented or not
     * @return true if augmented
     */
    public boolean isAugmented() {
        return augmentation != null;
    }

    /**
     * Sets a new augmentation
     * @param augmentation
     * @return return true if sucessfull
     */
    public boolean setAugmentation(Augmentation augmentation) {
        // there shall be no previous augmentation..
        if (this.augmentation != null) {
            log.info("Warning: Augment set for ({}) {} owner: {}", getObjectId(), getName(), ownerId);
            return false;
        }

        this.augmentation = augmentation;
        try (Connection con = DatabaseFactory.getConnection()) {
            updateItemAttributes(con);
        } catch (SQLException e) {
            log.error("Could not update atributes for item: {} from DB:", this, e);
        }

        // Notify to scripts.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_AUGMENT, getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(new OnPlayerAugment(asPlayer(), this, augmentation, true), getTemplate());
        }

        return true;
    }

    /**
     * Remove the augmentation
     */
    public void removeAugmentation() {
        if (augmentation == null) {
            return;
        }

        // Copy augmentation before removing it.
        final Augmentation augment = augmentation;
        augmentation = null;

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("DELETE FROM item_attributes WHERE itemId = ?")) {
            ps.setInt(1, getObjectId());
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Could not remove augmentation for item: {} from DB:", this, e);
        }

        // Notify to scripts.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_AUGMENT, getTemplate())) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(new OnPlayerAugment(asPlayer(), this, augment, false), getTemplate());
        }
    }

    public void restoreAttributes() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps1 =
                        con.prepareStatement("SELECT augAttributes FROM item_attributes WHERE itemId=?");
                PreparedStatement ps2 =
                        con.prepareStatement("SELECT elemType,elemValue FROM item_elementals WHERE itemId=?")) {
            ps1.setInt(1, getObjectId());
            try (ResultSet rs = ps1.executeQuery()) {
                if (rs.next()) {
                    final int augAttributes = rs.getInt(1);
                    if (augAttributes != -1) {
                        augmentation = new Augmentation(rs.getInt("augAttributes"));
                    }
                }
            }

            ps2.setInt(1, getObjectId());
            try (ResultSet rs = ps2.executeQuery()) {
                while (rs.next()) {
                    final byte elemType = rs.getByte(1);
                    final int elemValue = rs.getInt(2);
                    if ((elemType != -1) && (elemValue != -1)) {
                        applyAttribute(elemType, elemValue);
                    }
                }
            }
        } catch (Exception e) {
            log.error(
                    "Could not restore augmentation and elemental data for item {} from DB: {}",
                    this,
                    e.getMessage(),
                    e);
        }
    }

    private void updateItemAttributes(Connection con) {
        try (PreparedStatement ps = con.prepareStatement("REPLACE INTO item_attributes VALUES(?,?)")) {
            ps.setInt(1, getObjectId());
            ps.setInt(2, augmentation != null ? augmentation.getAugmentationId() : -1);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Could not update atributes for item: {} from DB:", this, e);
        }
    }

    private void updateItemElements(Connection con) {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM item_elementals WHERE itemId = ?")) {
            ps.setInt(1, getObjectId());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Could not update elementals for item: {} from DB:", this, e);
        }

        if (elementals == null) {
            return;
        }

        try (PreparedStatement ps = con.prepareStatement("INSERT INTO item_elementals VALUES(?,?,?)")) {
            for (Elementals elm : elementals) {
                ps.setInt(1, getObjectId());
                ps.setByte(2, elm.getElement());
                ps.setInt(3, elm.getValue());
                ps.executeUpdate();
                ps.clearParameters();
            }
        } catch (SQLException e) {
            log.error("Could not update elementals for item: {} from DB:", this, e);
        }
    }

    public Elementals getElemental(byte attribute) {
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

    /**
     * Add elemental attribute to item and save to db
     * @param element
     * @param value
     */
    public void setElementAttr(byte element, int value) {
        applyAttribute(element, value);
        try (Connection con = DatabaseFactory.getConnection()) {
            updateItemElements(con);
        } catch (SQLException e) {
            log.error("Could not update elementals for item: {} from DB:", this, e);
        }
    }

    /**
     * Remove elemental from item
     * @param element byte element to remove, -1 for all elementals remove
     */
    public void clearElementAttr(byte element) {
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

        final String query = (element != -1)
                ? "DELETE FROM item_elementals WHERE itemId = ? AND elemType = ?"
                : "DELETE FROM item_elementals WHERE itemId = ?";
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(query)) {
            if (element != -1) {
                // Item can have still others
                ps.setInt(2, element);
            }

            ps.setInt(1, getObjectId());
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Could not remove elemental enchant for item: {} from DB:", this, e);
        }
    }

    /**
     * Returns true if this item is a shadow item Shadow items have a limited life-time
     * @return
     */
    public boolean isShadowItem() {
        return mana >= 0;
    }

    /**
     * Decreases the mana of this shadow item, sends a inventory update schedules a new consumption task if non is running optionally one could force a new task
     * @param resetConsumingMana if true forces a new consumption task if item is equipped
     */
    public void decreaseMana(boolean resetConsumingMana) {
        decreaseMana(resetConsumingMana, 1);
    }

    /**
     * Decreases the mana of this shadow item, sends a inventory update schedules a new consumption task if non is running optionally one could force a new task
     * @param resetConsumingMana if forces a new consumption task if item is equipped
     * @param count how much mana decrease
     */
    public void decreaseMana(boolean resetConsumingMana, int count) {
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

        if (mana == 0) // The life time has expired.
        {
            sm = new SystemMessage(SystemMessageId.S1_S_REMAINING_MANA_IS_NOW_0_AND_THE_ITEM_HAS_DISAPPEARED);
            sm.addItemName(template);
            player.sendPacket(sm);

            // Unequip.
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
                // Destroy.
                player.getInventory().destroyItem(ItemProcessType.DESTROY, this, player, null);

                // Send update.
                final InventoryUpdate iu = new InventoryUpdate();
                iu.addRemovedItem(this);
                player.sendInventoryUpdate(iu);

                final StatusUpdate su = new StatusUpdate(player);
                su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
                player.sendPacket(su);
            } else {
                player.getWarehouse().destroyItem(ItemProcessType.DESTROY, this, player, null);
            }

            // Delete from world. (redundant - already removed by destroyItem)
            // World.removeObject(this);
        } else {
            // Reschedule if still equipped.
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
        if (consumingMana) {
            return;
        }

        consumingMana = true;
        ItemManaTaskManager.getInstance().add(this);
    }

    /**
     * Returns false cause item can't be attacked
     * @return boolean false
     */
    @Override
    public boolean isAutoAttackable(Creature attacker) {
        return false;
    }

    /**
     * This function basically returns a set of functions from Item/Armor/Weapon, but may add additional functions, if this particular item instance is enhanced for a particular player.
     * @param creature the player
     * @return the functions list
     */
    public List<AbstractFunction> getStatFuncs(Creature creature) {
        return template.getStatFuncs(this, creature);
    }

    /**
     * Updates the database.
     */
    public void updateDatabase() {
        updateDatabase(false);
    }

    /**
     * Updates the database.
     * @param force if the update should necessarily be done.
     */
    public void updateDatabase(boolean force) {
        dbLock.lock();

        try {
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
        } finally {
            dbLock.unlock();
        }
    }

    /**
     * Returns a Item stored in database from its objectID
     * @param ownerId
     * @param rs
     * @return Item
     */
    public static Item restoreFromDb(int ownerId, ResultSet rs) {
        Item inst = null;
        int objectId;
        int itemId;
        int locData;
        int enchantLevel;
        int customType1;
        int customType2;
        int manaLeft;
        long time;
        long count;
        ItemLocation loc;
        try {
            objectId = rs.getInt(1);
            itemId = rs.getInt("item_id");
            count = rs.getLong("count");
            loc = ItemLocation.valueOf(rs.getString("loc"));
            locData = rs.getInt("loc_data");
            enchantLevel = rs.getInt("enchant_level");
            customType1 = rs.getInt("custom_type1");
            customType2 = rs.getInt("custom_type2");
            manaLeft = rs.getInt("mana_left");
            time = rs.getLong("time");
        } catch (Exception e) {
            log.error("Could not restore an item owned by {} from DB:", ownerId, e);
            return null;
        }

        final ItemTemplate item = ItemData.getInstance().getTemplate(itemId);
        if (item == null) {
            log.error("Item item_id={} not known, object_id={}", itemId, objectId);
            return null;
        }

        inst = new Item(objectId, item);
        inst.ownerId = ownerId;
        inst.setCount(count);
        inst.enchantLevel = enchantLevel;
        inst.customType1 = customType1;
        inst.customType2 = customType2;
        inst.itemLocation = loc;
        inst.locationSlot = locData;
        inst.existsInDb = true;
        inst.storedInDb = true;

        // Setup life time for shadow weapons.
        inst.mana = manaLeft;
        inst.time = time;

        // load augmentation and elemental enchant
        if (inst.isEquipable()) {
            inst.restoreAttributes();
        }

        return inst;
    }

    /**
     * Init a dropped Item and add it in the world as a visible object.<br>
     * <br>
     * <b><u>Actions</u>:</b><br>
     * <li>Set the x,y,z position of the Item dropped and update its _worldregion</li>
     * <li>Add the Item dropped to _visibleObjects of its WorldRegion</li>
     * <li>Add the Item dropped in the world as a <b>visible</b> object</li><br>
     * <font color=#FF0000><b><u>Caution</u>: This method DOESN'T ADD the object to _allObjects of World </b></font><br>
     * <br>
     * <b><u>Example of use</u>:</b><br>
     * <li>Drop item</li>
     * <li>Call Pet</li>
     * @param dropper
     * @param locX
     * @param locY
     * @param locZ
     */
    public void dropMe(Creature dropper, int locX, int locY, int locZ) {
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
            setInstanceId(dropper.getInstanceId()); // Inherit instancezone when dropped in visible world.
        } else {
            setInstanceId(0); // No dropper? Make it a global item...
        }

        // Set the x,y,z position of the Item dropped and update its world region.
        setSpawned(true);
        setXYZ(x, y, z);

        setDropTime(System.currentTimeMillis());
        setDropperObjectId(
                dropper != null
                        ? dropper.getObjectId()
                        : 0); // Set the dropper Id for the knownlist packets in sendInfo.

        // Add the Item dropped in the world as a visible object.
        final WorldRegion region = getWorldRegion();
        region.addVisibleObject(this);
        World.addVisibleObject(this, region);
        if (GeneralConfig.SAVE_DROPPED_ITEM) {
            ItemsOnGroundManager.getInstance().save(this);
        }

        setDropperObjectId(0); // Set the dropper Id back to 0 so it no longer shows the drop packet.

        if ((dropper != null) && dropper.isPlayer()) {
            owner = null;

            // Notify to scripts
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

    /**
     * Update the database with values of the item
     */
    private void updateInDb() {
        if (!existsInDb || wear || storedInDb) {
            return;
        }

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE items SET owner_id=?,count=?,loc=?,loc_data=?,enchant_level=?,custom_type1=?,custom_type2=?,mana_left=?,time=? WHERE object_id = ?")) {
            ps.setInt(1, ownerId);
            ps.setLong(2, count);
            ps.setString(3, itemLocation.name());
            ps.setInt(4, locationSlot);
            ps.setInt(5, enchantLevel);
            ps.setInt(6, customType1);
            ps.setInt(7, customType2);
            ps.setInt(8, mana);
            ps.setLong(9, time);
            ps.setInt(10, getObjectId());
            ps.executeUpdate();
            existsInDb = true;
            storedInDb = true;
        } catch (Exception e) {
            log.error("Could not update item {} in DB: Reason: {}", this, e.getMessage(), e);
        }
    }

    /**
     * Insert the item in database
     */
    private void insertIntoDb() {
        if (existsInDb || (getObjectId() == 0) || wear) {
            return;
        }

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO items (owner_id,item_id,count,loc,loc_data,enchant_level,object_id,custom_type1,custom_type2,mana_left,time) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setInt(1, ownerId);
            ps.setInt(2, id);
            ps.setLong(3, count);
            ps.setString(4, itemLocation.name());
            ps.setInt(5, locationSlot);
            ps.setInt(6, enchantLevel);
            ps.setInt(7, getObjectId());
            ps.setInt(8, customType1);
            ps.setInt(9, customType2);
            ps.setInt(10, mana);
            ps.setLong(11, time);

            ps.executeUpdate();
            existsInDb = true;
            storedInDb = true;

            if (augmentation != null) {
                updateItemAttributes(con);
            }

            if (elementals != null) {
                updateItemElements(con);
            }
        } catch (Exception e) {
            log.error("Could not insert item {} into DB: Reason: {}", this, e.getMessage(), e);
        }
    }

    /**
     * Delete item from database
     */
    private void removeFromDb() {
        if (!existsInDb || wear) {
            return;
        }

        try (Connection con = DatabaseFactory.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM items WHERE object_id = ?")) {
                ps.setInt(1, getObjectId());
                ps.executeUpdate();
                existsInDb = false;
                storedInDb = false;
            }

            try (PreparedStatement ps = con.prepareStatement("DELETE FROM item_attributes WHERE itemId = ?")) {
                ps.setInt(1, getObjectId());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement("DELETE FROM item_elementals WHERE itemId = ?")) {
                ps.setInt(1, getObjectId());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement("DELETE FROM item_variables WHERE id = ?")) {
                ps.setInt(1, getObjectId());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            log.error("Could not delete item {} in DB: {}", this, e.getMessage(), e);
        }
    }

    public void resetOwnerTimer() {
        if (itemLootShedule != null) {
            itemLootShedule.cancel(true);
            itemLootShedule = null;
        }
    }

    public void setProtected(boolean isProtected) {
        protectedItem = isProtected;
    }

    public boolean isProtected() {
        return protectedItem;
    }

    public boolean isNightLure() {
        return (((id >= 8505) && (id <= 8513)) || (id == 8485));
    }

    public boolean getCountDecrease() {
        return countDecrease;
    }

    public void setInitCount(int initCount) {
        this.initCount = initCount;
    }

    public void restoreInitCount() {
        if (countDecrease) {
            setCount(initCount);
        }
    }

    public boolean isTimeLimitedItem() {
        return time > 0;
    }

    public long getRemainingTime() {
        return time - System.currentTimeMillis();
    }

    public void endOfLife() {
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
            // Destroy.
            player.getInventory().destroyItem(ItemProcessType.DESTROY, this, player, null);

            // Send update.
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

        // Delete from world. (redundant - already removed by destroyItem)
        // World.removeObject(this);
    }

    public void scheduleLifeTimeTask() {
        if (!isTimeLimitedItem()) {
            return;
        }

        if (getRemainingTime() <= 0) {
            endOfLife();
        } else {
            ItemLifeTimeTaskManager.getInstance().add(this, getTime());
        }
    }

    public void updateElementAttrBonus(Player player) {
        if (elementals == null) {
            return;
        }

        for (Elementals elm : elementals) {
            elm.updateBonus(player, isArmor());
        }
    }

    public void removeElementAttrBonus(Player player) {
        if (elementals == null) {
            return;
        }

        for (Elementals elm : elementals) {
            elm.removeBonus(player);
        }
    }

    @Override
    public void sendInfo(Player player) {
        if (dropperObjectId != 0) {
            player.sendPacket(new DropItem(this, dropperObjectId));
        } else {
            player.sendPacket(new SpawnItem(this));
        }
    }

    public void publish() {
        published = true;
    }

    @Override
    public boolean decayMe() {
        if (GeneralConfig.SAVE_DROPPED_ITEM) {
            ItemsOnGroundManager.getInstance().removeObject(this);
        }

        return super.decayMe();
    }

    public boolean isQuestItem() {
        return template.isQuestItem();
    }

    public boolean isElementable() {
        return ((itemLocation == ItemLocation.INVENTORY) || (itemLocation == ItemLocation.PAPERDOLL))
                && template.isElementable();
    }

    public boolean isFreightable() {
        return template.isFreightable();
    }

    public int useSkillDisTime() {
        return template.useSkillDisTime();
    }

    public int getOlyEnchantLevel() {
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
        return template.getDefaultEnchantLevel();
    }

    public boolean hasPassiveSkills() {
        return (template.getItemType() == EtcItemType.RUNE)
                && (itemLocation == ItemLocation.INVENTORY)
                && (ownerId > 0)
                && template.hasSkills();
    }

    public void giveSkillsToOwner() {
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
        return true;
    }

    @Override
    public Player asPlayer() {
        if ((owner == null) && (ownerId != 0)) {
            owner = World.getPlayer(ownerId);
        }

        return owner;
    }

    public int getEquipReuseDelay() {
        return template.getEquipReuseDelay();
    }

    /**
     * @param player
     * @param command
     */
    public void onBypassFeedback(Player player, String command) {
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
        return (shotsMask & type.getMask()) == type.getMask();
    }

    @Override
    public void setChargedShot(ShotType type, boolean charged) {
        if (charged) {
            shotsMask |= type.getMask();
        } else {
            shotsMask &= ~type.getMask();
        }
    }

    public void unChargeAllShots() {
        shotsMask = 0;
    }

    /**
     * Returns enchant effect object for this item
     * @return enchanteffect
     */
    public int[] getEnchantOptions() {
        final EnchantOptions op = EnchantItemOptionsData.getInstance().getOptions(this);
        if (op != null) {
            return op.getOptions();
        }

        return DEFAULT_ENCHANT_OPTIONS;
    }

    /**
     * Clears all the enchant bonuses if item is enchanted and containing bonuses for enchant value.
     */
    public void clearEnchantStats() {
        final Player player = asPlayer();
        if (player == null) {
            enchantOptions.clear();
            return;
        }

        for (Options op : enchantOptions) {
            op.remove(player);
        }

        enchantOptions.clear();
    }

    /**
     * Clears and applies all the enchant bonuses if item is enchanted and containing bonuses for enchant value.
     */
    public void applyEnchantStats() {
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
    public void setHeading(int heading) {}

    public void stopAllTasks() {
        ItemLifeTimeTaskManager.getInstance().remove(this);
    }

    public ItemVariables getVariables() {
        final ItemVariables vars = getScript(ItemVariables.class);
        return vars != null ? vars : addScript(new ItemVariables(getObjectId()));
    }

    public int getTransmogId() {
        if (!TransmogConfig.ENABLE_TRANSMOG) {
            return 0;
        }

        return getVariables().getInt(ItemVariables.TRANSMOG_ID, 0);
    }

    public void setTransmogId(int transmogId) {
        getVariables().set(ItemVariables.TRANSMOG_ID, transmogId);
        getVariables().storeMe();
    }

    public void removeTransmog() {
        getVariables().remove(ItemVariables.TRANSMOG_ID);
        getVariables().storeMe();
    }

    /**
     * Returns the item in String format
     * @return String
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(template);
        sb.append('[');
        sb.append(getObjectId());
        sb.append(']');
        return sb.toString();
    }
}
