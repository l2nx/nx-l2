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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.RatesConfig;
import app.l2nx.highfive.data.holders.PetData;
import app.l2nx.highfive.data.holders.PetLevelData;
import app.l2nx.highfive.data.sql.CharSummonTable;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.data.sql.StoredEffectRows;
import app.l2nx.highfive.data.sql.SummonEffectTable;
import app.l2nx.highfive.data.xml.PetDataTable;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.stat.PetStat;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.groups.PartyDistributionType;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.PetInventory;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.handler.IItemHandler;
import app.l2nx.highfive.handler.ItemHandler;
import app.l2nx.highfive.managers.CursedWeaponsManager;
import app.l2nx.highfive.managers.FortSiegeManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.managers.ItemsOnGroundManager;
import app.l2nx.highfive.mechanics.skill.EffectScope;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.StoredEffect;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.ActionFailed;
import app.l2nx.highfive.network.serverpackets.ExChangeNpcState;
import app.l2nx.highfive.network.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.serverpackets.PetInventoryUpdate;
import app.l2nx.highfive.network.serverpackets.StatusUpdate;
import app.l2nx.highfive.network.serverpackets.StopMove;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.taskmanagers.DecayTaskManager;
import app.l2nx.highfive.threads.ThreadPool;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Future;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Pet extends Summon {

    private static final String ADD_SKILL_SAVE =
            "INSERT INTO character_pet_skills_save (petObjItemId,skill_id,skill_level,remaining_ms,tick_phase_ms,buff_index) VALUES (?,?,?,?,?,?)";
    private static final String RESTORE_SKILL_SAVE =
            "SELECT petObjItemId,skill_id,skill_level,remaining_ms,tick_phase_ms,buff_index FROM character_pet_skills_save WHERE petObjItemId=? ORDER BY buff_index ASC";
    private static final String DELETE_SKILL_SAVE = "DELETE FROM character_pet_skills_save WHERE petObjItemId=?";

    @Getter
    int currentFed;

    @Getter
    final PetInventory inventory;

    @Getter
    private final int controlObjectId;

    @Getter
    private boolean respawned;

    @Getter
    private final boolean mountable;

    private Future<?> feedTask;
    private PetData data;
    private PetLevelData leveldata;

    /** The Experience before the last Death Penalty */
    private long expBeforeDeath = 0;

    private int curWeightPenalty = 0;

    /**
     * Creates a pet.
     * @param template the pet NPC template
     * @param owner the owner
     * @param control the summoning item
     */
    public Pet(NpcTemplate template, Player owner, Item control) {
        this(template, owner, control, (byte)
                (template.getDisplayId() == 12564 ? owner.getLevel() : template.getLevel()));
    }

    /**
     * Creates a pet.
     * @param template the pet NPC template
     * @param owner the pet NPC template
     * @param control the summoning item
     * @param level the level
     */
    public Pet(NpcTemplate template, Player owner, Item control, byte level) {
        super(template, owner);
        setInstanceType(InstanceType.Pet);

        controlObjectId = control.getObjectId();
        getStat().setLevel((byte) Math.max(level, PetDataTable.getInstance().getPetMinLevel(template.getId())));
        inventory = new PetInventory(this);
        inventory.restore();

        final int npcId = template.getId();
        mountable = PetDataTable.isMountable(npcId);
        getPetData();
        getPetLevelData();
    }

    public PetLevelData getPetLevelData() {
        if (leveldata == null) {
            leveldata = PetDataTable.getInstance()
                    .getPetLevelData(getTemplate().getId(), getStat().getLevel());
        }

        return leveldata;
    }

    public PetData getPetData() {
        if (data == null) {
            data = PetDataTable.getInstance().getPetData(getTemplate().getId());
        }

        return data;
    }

    public void setPetData(PetLevelData value) {
        leveldata = value;
    }

    /**
     * Manage Feeding Task.<br>
     * Feed or kill the pet depending on hunger level.<br>
     * If pet has food in inventory and feed level drops below 55% then consume food from inventory.<br>
     * Send a broadcastStatusUpdate packet for this Pet
     */
    class FeedTask implements Runnable {
        @Override
        public void run() {
            try {
                if ((getOwner() == null)
                        || !getOwner().hasSummon()
                        || (getOwner().getSummon().getObjectId() != getObjectId())) {
                    stopFeed();
                    return;
                }

                setCurrentFed(currentFed > getFeedConsume() ? currentFed - getFeedConsume() : 0);
                broadcastStatusUpdate();

                if (isUncontrollable()) {
                    if (ClockBridge.random().nextInt(100) < 30) {
                        // Owl Monk remove PK.
                        if ((getTemplate().getId() == 16050) && (getOwner() != null)) {
                            getOwner()
                                    .setPkKills(Math.max(
                                            0,
                                            getOwner().getPkKills()
                                                    - ClockBridge.random().nextInt(1, 7)));
                        }

                        sendPacket(SystemMessageId.STARVING_GRUMPY_AND_FED_UP_YOUR_PET_HAS_LEFT);
                        unSummon(getOwner());
                        stopFeed();
                        return;
                    }

                    sendPacket(SystemMessageId.YOUR_PET_SERVITOR_IS_UNRESPONSIVE_AND_WILL_NOT_OBEY_ANY_ORDERS);
                    return;
                }

                final Set<Integer> foodIds = getPetData().getFood();
                if (foodIds.isEmpty()) {
                    if (isHungry()) {
                        sendPacket(SystemMessageId.YOUR_PET_IS_VERY_HUNGRY_PLEASE_BE_CAREFUL);
                    }
                    return;
                }

                // Try to find food in pet inventory.
                Item food = null;
                for (int id : foodIds) {
                    food = inventory.getItemByItemId(id);
                    if (food != null) {
                        break;
                    }
                }

                // Pet is hungry and food is available - auto feed.
                if ((food != null) && isHungry()) {
                    final IItemHandler handler = ItemHandler.getInstance().getHandler(food.getEtcItem());
                    if (handler != null) {
                        final SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_PET_WAS_HUNGRY_SO_IT_ATE_S1);
                        sm.addItemName(food.getId());
                        sendPacket(sm);
                        handler.onItemUse(Pet.this, food, false);
                    }
                }

                // Notify player if pet uncontrollable due to starvation.
                if (isUncontrollable()) {
                    sendPacket(
                            SystemMessageId
                                    .YOUR_PET_IS_STARVING_AND_WILL_NOT_OBEY_UNTIL_IT_GETS_IT_S_FOOD_FEED_YOUR_PET);
                }
            } catch (Exception e) {
                log.error("Pet [ObjectId: {}] a feed task error has occurred", getObjectId(), e);
            }
        }

        private int getFeedConsume() {
            return isAttackingNow()
                    ? getPetLevelData().getPetFeedBattle()
                    : getPetLevelData().getPetFeedNormal();
        }
    }

    public static synchronized Pet spawnPet(NpcTemplate template, Player owner, Item control) {
        final Pet existingPet = World.getPet(owner.getObjectId());
        if (existingPet != null) // Owner has a pet listed in world.
        {
            existingPet.unSummon(owner);
        }

        final PetData data = PetDataTable.getInstance().getPetData(template.getId());
        final Pet pet = restore(control, template, owner);

        // Add the pet instance to world.
        if (pet != null) {
            pet.setTitle(owner.getName());
            if (data.isSynchLevel() && (pet.getLevel() != owner.getLevel())) {
                final byte availableLevel = (byte) Math.min(data.getMaxLevel(), owner.getLevel());
                pet.getStat().setLevel(availableLevel);
                pet.getStat().setExp(pet.getStat().getExpForLevel(availableLevel));
            }

            World.addPet(owner.getObjectId(), pet);
        }

        return pet;
    }

    @Override
    public PetStat getStat() {
        return (PetStat) super.getStat();
    }

    @Override
    public void initCharStat() {
        setStat(new PetStat(this));
    }

    @Override
    public int getSummonType() {
        return 2;
    }

    public Item getControlItem() {
        return getOwner().getInventory().getItemByObjectId(controlObjectId);
    }

    public void setCurrentFed(int num) {
        if (num <= 0) {
            sendPacket(new ExChangeNpcState(getObjectId(), 0x64));
        } else if ((currentFed <= 0) && (num > 0)) {
            sendPacket(new ExChangeNpcState(getObjectId(), 0x65));
        }

        currentFed = num > getMaxFed() ? getMaxFed() : num;
    }

    /**
     * Returns the pet's currently equipped weapon instance (if any).
     */
    @Override
    public Item getActiveWeaponInstance() {
        for (Item item : inventory.getItems()) {
            if ((item.getItemLocation() == ItemLocation.PET_EQUIP)
                    && (item.getTemplate().getBodyPart() == BodyPart.R_HAND)) {
                return item;
            }
        }

        return null;
    }

    /**
     * Returns the pet's currently equipped weapon (if any).
     */
    @Override
    public Weapon getActiveWeaponItem() {
        final Item weapon = getActiveWeaponInstance();
        return weapon == null ? null : (Weapon) weapon.getTemplate();
    }

    @Override
    public Item getSecondaryWeaponInstance() {
        // temporary? unavailable
        return null;
    }

    @Override
    public Weapon getSecondaryWeaponItem() {
        // temporary? unavailable
        return null;
    }

    /**
     * Destroys item from inventory and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param objectId : int Item Instance identifier of the item to be destroyed
     * @param count : int Quantity of items to be destroyed
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage : boolean Specifies whether to send message to Client about this action
     * @return boolean informing if the action was successfull
     */
    @Override
    public boolean destroyItem(
            ItemProcessType process, int objectId, long count, WorldObject reference, boolean sendMessage) {
        final Item item = inventory.destroyItem(process, objectId, count, getOwner(), reference);
        if (item == null) {
            if (sendMessage) {
                sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
            }

            return false;
        }

        // Send Pet inventory update packet.
        final PetInventoryUpdate petIU = new PetInventoryUpdate();
        petIU.addItem(item);
        sendPacket(petIU);

        if (sendMessage) {
            final SystemMessage sm;
            if (count > 1) {
                sm = new SystemMessage(SystemMessageId.S2_S1_HAS_DISAPPEARED);
                sm.addItemName(item.getId());
                sm.addLong(count);
            } else {
                sm = new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED);
                sm.addItemName(item.getId());
            }
        }

        return true;
    }

    /**
     * Destroy item from inventory by using its <b>itemId</b> and send a Server->Client InventoryUpdate packet to the Player.
     * @param process : ItemProcessType identifier of process triggering this action
     * @param itemId : int Item identifier of the item to be destroyed
     * @param count : int Quantity of items to be destroyed
     * @param reference : WorldObject Object referencing current action like NPC selling item or previous item in transformation
     * @param sendMessage : boolean Specifies whether to send message to Client about this action
     * @return boolean informing if the action was successfull
     */
    @Override
    public boolean destroyItemByItemId(
            ItemProcessType process, int itemId, long count, WorldObject reference, boolean sendMessage) {
        final Item item = inventory.destroyItemByItemId(process, itemId, count, getOwner(), reference);
        if (item == null) {
            if (sendMessage) {
                sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
            }

            return false;
        }

        // Send Pet inventory update packet.
        final PetInventoryUpdate petIU = new PetInventoryUpdate();
        petIU.addItem(item);
        sendPacket(petIU);

        if (sendMessage) {
            final SystemMessage sm;
            if (count > 1) {
                sm = new SystemMessage(SystemMessageId.S2_S1_HAS_DISAPPEARED);
                sm.addItemName(item.getId());
                sm.addLong(count);
            } else {
                sm = new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED);
                sm.addItemName(item.getId());
            }

            sendPacket(sm);
        }

        return true;
    }

    @Override
    public void doPickupItem(WorldObject object) {
        if (isDead()) {
            return;
        }

        getAI().setIntentionIdle();
        broadcastPacket(new StopMove(this));
        if (!object.isItem()) {
            // Do not try to pickup anything that is not an item :)
            log.warn("{} trying to pickup wrong target.{}", this, object);
            sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        final Item target = (Item) object;

        // Cursed weapons
        if (CursedWeaponsManager.getInstance().isCursed(target.getId())) {
            final SystemMessage smsg = new SystemMessage(SystemMessageId.YOU_HAVE_FAILED_TO_PICK_UP_S1);
            smsg.addItemName(target.getId());
            sendPacket(smsg);
            return;
        }

        if (FortSiegeManager.getInstance().isCombat(target.getId())) {
            return;
        }

        final boolean follow = getFollowStatus();
        SystemMessage smsg = null;
        synchronized (target) {
            // Check if the target to pick up is visible.
            if (!target.isSpawned()) {
                // Send a Server->Client packet ActionFailed to this Player.
                sendPacket(ActionFailed.STATIC_PACKET);
                return;
            }

            if (!target.getDropProtection().tryPickUp(this)) {
                sendPacket(ActionFailed.STATIC_PACKET);
                smsg = new SystemMessage(SystemMessageId.YOU_HAVE_FAILED_TO_PICK_UP_S1);
                smsg.addItemName(target);
                sendPacket(smsg);
                return;
            }

            if (((isInParty() && (getParty().getDistributionType() == PartyDistributionType.FINDERS_KEEPERS))
                            || !isInParty())
                    && !inventory.validateCapacity(target)) {
                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(SystemMessageId.YOUR_PET_CANNOT_CARRY_ANY_MORE_ITEMS);
                return;
            }

            if ((target.getOwnerId() != 0)
                    && (target.getOwnerId() != getOwner().getObjectId())
                    && !getOwner().isInLooterParty(target.getOwnerId())) {
                if (target.getId() == Inventory.ADENA_ID) {
                    smsg = new SystemMessage(SystemMessageId.YOU_HAVE_FAILED_TO_PICK_UP_S1_ADENA);
                    smsg.addLong(target.getCount());
                } else if (target.getCount() > 1) {
                    smsg = new SystemMessage(SystemMessageId.YOU_HAVE_FAILED_TO_PICK_UP_S2_S1_S);
                    smsg.addItemName(target);
                    smsg.addLong(target.getCount());
                } else {
                    smsg = new SystemMessage(SystemMessageId.YOU_HAVE_FAILED_TO_PICK_UP_S1);
                    smsg.addItemName(target);
                }

                sendPacket(ActionFailed.STATIC_PACKET);
                sendPacket(smsg);
                return;
            }

            if ((target.getItemLootShedule() != null)
                    && ((target.getOwnerId() == getOwner().getObjectId())
                            || getOwner().isInLooterParty(target.getOwnerId()))) {
                target.resetOwnerTimer();
            }

            // Remove from the ground!
            target.pickupMe(this);

            if (GeneralConfig.SAVE_DROPPED_ITEM) {
                ItemsOnGroundManager.getInstance().removeObject(target);
            }
        }

        // Herbs
        if (target.getTemplate().hasExImmediateEffect()) {
            final IItemHandler handler = ItemHandler.getInstance().getHandler(target.getEtcItem());
            if (handler == null) {
                log.warn("No item handler registered for item ID: {}.", target.getId());
            } else {
                handler.onItemUse(this, target, false);
            }

            ItemManager.destroyItem(ItemProcessType.NONE, target, getOwner(), null);
            broadcastStatusUpdate();
        } else {
            if (target.getId() == Inventory.ADENA_ID) {
                smsg = new SystemMessage(SystemMessageId.YOUR_PET_PICKED_UP_S1_ADENA);
                smsg.addLong(target.getCount());
                sendPacket(smsg);
            } else if (target.isEnchanted()) {
                smsg = new SystemMessage(SystemMessageId.YOUR_PET_PICKED_UP_S1_S2);
                smsg.addInt(target.getEnchantLevel());
                smsg.addItemName(target);
                sendPacket(smsg);
            } else if (target.getCount() > 1) {
                smsg = new SystemMessage(SystemMessageId.YOUR_PET_PICKED_UP_S2_S1_S);
                smsg.addLong(target.getCount());
                smsg.addItemName(target);
                sendPacket(smsg);
            } else {
                smsg = new SystemMessage(SystemMessageId.YOUR_PET_PICKED_UP_S1);
                smsg.addItemName(target);
                sendPacket(smsg);
            }

            // If owner is in party and it is not finders keepers, distribute the item instead of stealing it -.-
            if (getOwner().isInParty()
                    && (getOwner().getParty().getDistributionType() != PartyDistributionType.FINDERS_KEEPERS)) {
                getOwner().getParty().distributeItem(getOwner(), target);
            } else {
                final Item item = inventory.addItem(ItemProcessType.PICKUP, target, getOwner(), this);

                // sendPacket(new PetItemList(_inventory.getItems()));
                sendPacket(new PetInventoryUpdate(item));
            }
        }

        getAI().setIntentionIdle();

        if (follow) {
            followOwner();
        }
    }

    @Override
    public void deleteMe(Player owner) {
        inventory.transferItemsToOwner();
        super.deleteMe(owner);
        destroyControlItem(owner, false); // this should also delete the pet from the db
        CharSummonTable.getInstance().getPets().remove(getOwner().getObjectId());
    }

    @Override
    public boolean doDie(Creature killer) {
        final Player owner = getOwner();
        if ((owner != null) && !owner.isInDuel() && (!isInsideZone(ZoneId.PVP) || isInsideZone(ZoneId.SIEGE))) {
            deathPenalty();
        }

        if (!super.doDie(killer, true)) {
            return false;
        }

        stopFeed();
        sendPacket(
                SystemMessageId
                        .THE_PET_HAS_BEEN_KILLED_IF_YOU_DON_T_RESURRECT_IT_WITHIN_24_HOURS_THE_PET_S_BODY_WILL_DISAPPEAR_ALONG_WITH_ALL_THE_PET_S_ITEMS);
        DecayTaskManager.getInstance().add(this);

        // Do not decrease exp if is in duel, arena.
        return true;
    }

    @Override
    public void doRevive() {
        getOwner().removeReviving();

        super.doRevive();

        // stopDecay
        DecayTaskManager.getInstance().cancel(this);
        startFeed();
        if (!isHungry()) {
            setRunning();
        }

        getAI().setIntentionActive();
    }

    @Override
    public void doRevive(double revivePower) {
        // Restore the pet's lost experience,.
        // depending on the % return of the skill used (based on its power).
        restoreExp(revivePower);
        doRevive();
    }

    /**
     * Transfers item to another inventory
     * @param process ItemProcessType identifier of process triggering this action
     * @param objectId Item Identifier of the item to be transfered
     * @param count Quantity of items to be transfered
     * @param target
     * @param actor the player requesting the item transfer
     * @param reference Object referencing current action like NPC selling item or previous item in transformation
     * @return Item corresponding to the new item or the updated item in inventory
     */
    public Item transferItem(
            ItemProcessType process, int objectId, long count, Inventory target, Player actor, WorldObject reference) {
        final Item oldItem = inventory.getItemByObjectId(objectId);
        final Item playerOldItem = target.getItemByItemId(oldItem.getId());
        final Item newItem = inventory.transferItem(process, objectId, count, target, actor, reference);
        if (newItem == null) {
            return null;
        }

        // Send inventory update packet.
        final PetInventoryUpdate petIU = new PetInventoryUpdate();
        if ((oldItem.getCount() > 0) && (oldItem != newItem)) {
            petIU.addModifiedItem(oldItem);
        } else {
            petIU.addRemovedItem(oldItem);
        }

        sendPacket(petIU);

        // Send target update packet.
        if (!newItem.isStackable()) {
            final Player owner = getOwner();
            if (owner != null) {
                final InventoryUpdate iu = new InventoryUpdate();
                iu.addNewItem(newItem);
                owner.sendInventoryUpdate(iu);
            }
        } else if ((playerOldItem != null) && newItem.isStackable()) {
            final Player owner = getOwner();
            if (owner != null) {
                final InventoryUpdate iu = new InventoryUpdate();
                iu.addModifiedItem(newItem);
                owner.sendInventoryUpdate(iu);
            }
        }

        return newItem;
    }

    /**
     * Remove the Pet from DB and its associated item from the player inventory
     * @param owner The owner from whose inventory we should delete the item
     * @param evolve
     */
    public void destroyControlItem(Player owner, boolean evolve) {
        // Remove the pet instance from world.
        World.removePet(owner.getObjectId());

        // Delete from inventory.
        try {
            Item removedItem;
            if (evolve) {
                removedItem =
                        owner.getInventory().destroyItem(ItemProcessType.FEE, controlObjectId, 1, getOwner(), this);
            } else {
                removedItem =
                        owner.getInventory().destroyItem(ItemProcessType.DESTROY, controlObjectId, 1, getOwner(), this);
                if (removedItem != null) {
                    final SystemMessage sm = new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED);
                    sm.addItemName(removedItem);
                    owner.sendPacket(sm);
                }
            }

            if (removedItem == null) {
                log.warn("Couldn't destroy pet control item for {} pet: {} evolve: {}", owner, this, evolve);
            } else {
                final InventoryUpdate iu = new InventoryUpdate();
                iu.addRemovedItem(removedItem);
                owner.sendInventoryUpdate(iu);

                final StatusUpdate su = new StatusUpdate(owner);
                su.addAttribute(StatusUpdate.CUR_LOAD, owner.getCurrentLoad());
                owner.sendPacket(su);

                owner.broadcastUserInfo();

                World.removeObject(removedItem);
            }
        } catch (Exception e) {
            log.warn("Error while destroying control item: {}", e.getMessage(), e);
        }

        // Pet control item no longer exists, delete the pet from the db.
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement("DELETE FROM pets WHERE item_obj_id = ?")) {
            ps.setInt(1, controlObjectId);
            ps.execute();
        } catch (Exception e) {
            log.error("Failed to delete Pet [ObjectId: {}]", getObjectId(), e);
        }
    }

    public void dropAllItems() {
        try {
            for (Item item : inventory.getItems()) {
                dropItemHere(item);
            }
        } catch (Exception e) {
            log.warn("Pet Drop Error: {}", e.getMessage(), e);
        }
    }

    public void dropItemHere(Item item, boolean protect) {
        final Item dropit =
                inventory.dropItem(ItemProcessType.DROP, item.getObjectId(), item.getCount(), getOwner(), this);
        if (dropit != null) {
            if (protect) {
                dropit.getDropProtection().protect(getOwner());
            }

            log.trace("Item id to drop: {} amount: {}", dropit.getId(), dropit.getCount());
            dropit.dropMe(this, getX(), getY(), getZ() + 100);
        }
    }

    public void dropItemHere(Item dropit) {
        dropItemHere(dropit, false);
    }

    private static Pet restore(Item control, NpcTemplate template, Player owner) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT item_obj_id, name, level, curHp, curMp, exp, sp, fed FROM pets WHERE item_obj_id=?")) {
            Pet pet;
            ps.setInt(1, control.getObjectId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    if (template.isType("BabyPet")) {
                        pet = new BabyPet(template, owner, control);
                    } else {
                        pet = new Pet(template, owner, control);
                    }

                    return pet;
                }

                if (template.isType("BabyPet")) {
                    pet = new BabyPet(template, owner, control, rs.getByte("level"));
                } else {
                    pet = new Pet(template, owner, control, rs.getByte("level"));
                }

                pet.respawned = true;
                pet.setName(rs.getString("name"));

                long exp = rs.getLong("exp");
                final PetLevelData info = PetDataTable.getInstance().getPetLevelData(pet.getId(), pet.getLevel());

                // DS: update experience based by level.
                // Avoiding pet delevels due to exp per level values changed.
                if ((info != null) && (exp < info.getPetMaxExp())) {
                    exp = info.getPetMaxExp();
                }

                pet.getStat().setExp(exp);
                pet.getStat().setSp(rs.getInt("sp"));

                pet.getStatus().setCurrentHp(rs.getInt("curHp"));
                pet.getStatus().setCurrentMp(rs.getInt("curMp"));
                pet.getStatus().setCurrentCp(pet.getMaxCp());
                if (rs.getDouble("curHp") < 1) {
                    pet.setDead(true);
                    pet.stopHpMpRegeneration();
                }

                pet.setCurrentFed(rs.getInt("fed"));
            }

            return pet;
        } catch (Exception e) {
            log.warn("Could not restore pet data for owner: {} - {}", owner, e.getMessage(), e);
        }

        return null;
    }

    @Override
    public void setRestoreSummon(boolean value) {
        restoreSummon = value;
    }

    @Override
    public void stopSkillEffects(SkillFinishType type, int skillId) {
        super.stopSkillEffects(type, skillId);
        SummonEffectTable.getInstance().removePetEffects(getControlObjectId(), skillId);
    }

    @Override
    public void storeMe() {
        if (controlObjectId == 0) {
            // This is a summon, not a pet, don't store anything.
            return;
        }

        if (!PlayerConfig.RESTORE_PET_ON_RECONNECT) {
            restoreSummon = false;
        }

        final String req = !respawned
                ? "INSERT INTO pets (name,level,curHp,curMp,exp,sp,fed,ownerId,restore,item_obj_id) VALUES (?,?,?,?,?,?,?,?,?,?)"
                : "UPDATE pets SET name=?,level=?,curHp=?,curMp=?,exp=?,sp=?,fed=?,ownerId=?,restore=? WHERE item_obj_id = ?";
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(req)) {
            ps.setString(1, getName());
            ps.setInt(2, getStat().getLevel());
            ps.setDouble(3, getStatus().getCurrentHp());
            ps.setDouble(4, getStatus().getCurrentMp());
            ps.setLong(5, getStat().getExp());
            ps.setLong(6, getStat().getSp());
            ps.setInt(7, currentFed);
            ps.setInt(8, getOwner().getObjectId());
            ps.setString(9, String.valueOf(restoreSummon)); // True restores pet on login.
            ps.setInt(10, controlObjectId);
            ps.executeUpdate();

            respawned = true;
            if (restoreSummon) {
                CharSummonTable.getInstance().getPets().put(getOwner().getObjectId(), getControlObjectId());
            } else {
                CharSummonTable.getInstance().getPets().remove(getOwner().getObjectId());
            }
        } catch (Exception e) {
            log.error("Failed to store Pet [ObjectId: {}] data", getObjectId(), e);
        }

        final Item itemInst = getControlItem();
        if ((itemInst != null) && (itemInst.getEnchantLevel() != getStat().getLevel())) {
            itemInst.setEnchantLevel(getStat().getLevel());
            itemInst.updateDatabase();
        }
    }

    @Override
    public void storeEffect(boolean storeEffects) {
        if (!PlayerConfig.SUMMON_STORE_SKILL_COOLTIME) {
            return;
        }

        // Clear list for overwrite.
        SummonEffectTable.getInstance().clearPetEffects(getControlObjectId());

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps1 = con.prepareStatement(DELETE_SKILL_SAVE);
                PreparedStatement ps2 = con.prepareStatement(ADD_SKILL_SAVE)) {
            // Delete all current stored effects for summon to avoid dupe.
            ps1.setInt(1, controlObjectId);
            ps1.execute();

            int buffIndex = 0;

            // Store all effect data along with calculated remaining.
            if (storeEffects) {
                for (Entry<Skill, StoredEffect> entry : effectsToStore().entrySet()) {
                    final Skill skill = entry.getKey();
                    final StoredEffect stored = entry.getValue();

                    ps2.setInt(1, controlObjectId);
                    ps2.setInt(2, skill.getId());
                    ps2.setInt(3, skill.getLevel());
                    ps2.setObject(4, stored.remainingMillis(), Types.BIGINT);
                    ps2.setLong(5, stored.tickPhaseMillis());
                    ps2.setInt(6, ++buffIndex);
                    ps2.addBatch();

                    SummonEffectTable.getInstance().addPetEffect(getControlObjectId(), skill, stored);
                }

                ps2.executeBatch();
            }
        } catch (Exception e) {
            log.warn("Could not store pet effect data: ", e);
        }
    }

    @Override
    public void restoreEffects() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps1 = con.prepareStatement(RESTORE_SKILL_SAVE);
                PreparedStatement ps2 = con.prepareStatement(DELETE_SKILL_SAVE)) {
            if (!SummonEffectTable.getInstance().containsPetId(getControlObjectId())) {
                ps1.setInt(1, controlObjectId);
                try (ResultSet rs = ps1.executeQuery()) {
                    while (rs.next()) {
                        final Skill skill =
                                SkillData.getInstance().getSkill(rs.getInt("skill_id"), rs.getInt("skill_level"));
                        if (skill == null) {
                            continue;
                        }

                        if (skill.hasEffects(EffectScope.GENERAL)) {
                            StoredEffectRows.read(rs, controlObjectId)
                                    .ifPresent(stored -> SummonEffectTable.getInstance()
                                            .addPetEffect(getControlObjectId(), skill, stored));
                        }
                    }
                }
            }

            ps2.setInt(1, controlObjectId);
            ps2.executeUpdate();
        } catch (Exception e) {
            log.warn("Could not restore {} active effect data: {}", this, e.getMessage(), e);
        } finally {
            SummonEffectTable.getInstance().applyPetEffects(this, getControlObjectId());
        }
    }

    public synchronized void stopFeed() {
        if (feedTask == null) {
            return;
        }

        feedTask.cancel(false);
        feedTask = null;
    }

    public synchronized void startFeed() {
        // Stop feeding task if it is active.
        stopFeed();
        if (!isDead() && (getOwner().getSummon() == this)) {
            feedTask = ThreadPool.scheduleAtFixedRate(new FeedTask(), 10000, 10000);
        }
    }

    @Override
    public synchronized void unSummon(Player owner) {
        stopFeed();
        super.unSummon(owner);

        if (!isDead()) {
            if (inventory != null) {
                inventory.deleteMe();
            }

            World.removePet(owner.getObjectId());
        }
    }

    /**
     * Restore the specified % of experience this Pet has lost.
     * @param restorePercent
     */
    public void restoreExp(double restorePercent) {
        if (expBeforeDeath > 0) {
            // Restore the specified % of lost experience.
            getStat().addExp(Math.round(((expBeforeDeath - getStat().getExp()) * restorePercent) / 100));
            expBeforeDeath = 0;
        }
    }

    private void deathPenalty() {
        // TODO: Need Correct Penalty

        final int level = getStat().getLevel();
        final double percentLost = (-0.07 * level) + 6.5;

        // Calculate the Experience loss.
        final long lostExp = Math.round(
                ((getStat().getExpForLevel(level + 1) - getStat().getExpForLevel(level)) * percentLost) / 100);

        // Get the Experience before applying penalty.
        expBeforeDeath = getStat().getExp();

        // Set the new Experience value of the Pet.
        getStat().addExp(-lostExp);
    }

    @Override
    public synchronized void addExpAndSp(double addToExp, double addToSp) {
        if (getId() == 12564) {
            getStat().addExpAndSp(Math.round(addToExp * RatesConfig.SINEATER_XP_RATE), addToSp);
        } else {
            getStat().addExpAndSp(Math.round(addToExp * RatesConfig.PET_XP_RATE), addToSp);
        }
    }

    @Override
    public long getExpForThisLevel() {
        return getStat().getExpForLevel(getLevel());
    }

    @Override
    public long getExpForNextLevel() {
        return getStat().getExpForLevel(getLevel() + 1);
    }

    @Override
    public int getLevel() {
        return getStat().getLevel();
    }

    public int getMaxFed() {
        return getStat().getMaxFeed();
    }

    @Override
    public int getCriticalHit(Creature target, Skill skill) {
        return getStat().getCriticalHit(target, skill);
    }

    @Override
    public int getSkillLevel(int skillId) {
        if (getKnownSkill(skillId) == null) {
            return 0;
        }

        final int level = getLevel();
        return level > 70 ? 7 + ((level - 70) / 5) : level / 10;
    }

    public void updateRefOwner(Player owner) {
        final int oldOwnerId = getOwner().getObjectId();
        setOwner(owner);
        World.removePet(oldOwnerId);
        World.addPet(oldOwnerId, this);
    }

    public int getInventoryLimit() {
        return NpcConfig.INVENTORY_MAXIMUM_PET;
    }

    public void refreshOverloaded() {
        if ((getInventory() == null) || (getOwner() == null)) {
            return;
        }

        final int maxLoad = getMaxLoad();
        if (maxLoad > 0) {
            final long weightproc = ((getCurrentLoad() - getBonusWeightPenalty()) * 1000) / maxLoad;
            int newWeightPenalty;
            if ((weightproc < 500) || getOwner().getDietMode()) {
                newWeightPenalty = 0;
            } else if (weightproc < 666) {
                newWeightPenalty = 1;
            } else if (weightproc < 800) {
                newWeightPenalty = 2;
            } else if (weightproc < 1000) {
                newWeightPenalty = 3;
            } else {
                newWeightPenalty = 4;
            }

            if (curWeightPenalty != newWeightPenalty) {
                curWeightPenalty = newWeightPenalty;
                if (newWeightPenalty > 0) {
                    addSkill(SkillData.getInstance().getSkill(4270, newWeightPenalty));
                    setOverloaded(getCurrentLoad() >= maxLoad);
                } else {
                    removeSkill(getKnownSkill(4270), true);
                    setOverloaded(false);
                }
            }
        }
    }

    @Override
    public void updateAndBroadcastStatus(int value) {
        if ((getInventory() != null) && (getOwner() != null)) {
            refreshOverloaded();
        }

        super.updateAndBroadcastStatus(value);
    }

    @Override
    public boolean isHungry() {
        return currentFed
                < ((getPetData().getHungryLimit() / 100f) * getPetLevelData().getPetMaxFeed());
    }

    /**
     * Verifies if a pet can be controlled by it's owner.<br>
     * Starving pets cannot be controlled.
     * @return {@code true} if the per cannot be controlled
     */
    public boolean isUncontrollable() {
        return currentFed <= 0;
    }

    @Override
    public int getWeapon() {
        final Item weapon = inventory.getPaperdollItem(Inventory.PAPERDOLL_RHAND);
        return weapon != null ? weapon.getId() : 0;
    }

    @Override
    public int getArmor() {
        final Item weapon = inventory.getPaperdollItem(Inventory.PAPERDOLL_CHEST);
        return weapon != null ? weapon.getId() : 0;
    }

    public int getJewel() {
        final Item weapon = inventory.getPaperdollItem(Inventory.PAPERDOLL_NECK);
        return weapon != null ? weapon.getId() : 0;
    }

    @Override
    public short getSoulShotsPerHit() {
        return getPetLevelData().getPetSoulShot();
    }

    @Override
    public short getSpiritShotsPerHit() {
        return getPetLevelData().getPetSpiritShot();
    }

    @Override
    public void setName(String name) {
        final Item controlItem = getControlItem();
        if (controlItem != null) {
            if (controlItem.getCustomType2() == (name == null ? 1 : 0)) {
                // Name not set yet.
                controlItem.setCustomType2(name != null ? 1 : 0);
                controlItem.updateDatabase();

                final Player owner = getOwner();
                if (owner != null) {
                    final InventoryUpdate iu = new InventoryUpdate();
                    iu.addModifiedItem(controlItem);
                    owner.sendInventoryUpdate(iu);
                }
            }
        } else {
            log.warn("Pet control item null, for pet: {}", toString());
        }

        super.setName(name);
    }

    public boolean canEatFoodId(int itemId) {
        return data.getFood().contains(itemId);
    }

    @Override
    public boolean isPet() {
        return true;
    }

    @Override
    public Pet asPet() {
        return this;
    }

    @Override
    public double getRunSpeed() {
        return super.getRunSpeed() * (isUncontrollable() ? 0.5d : 1.0d);
    }

    @Override
    public double getWalkSpeed() {
        return super.getWalkSpeed() * (isUncontrollable() ? 0.5d : 1.0d);
    }

    @Override
    public double getMovementSpeedMultiplier() {
        return super.getMovementSpeedMultiplier() * (isUncontrollable() ? 0.5d : 1.0d);
    }

    @Override
    public double getMoveSpeed() {
        if (isInsideZone(ZoneId.WATER)) {
            return isRunning() ? getSwimRunSpeed() : getSwimWalkSpeed();
        }

        return isRunning() ? getRunSpeed() : getWalkSpeed();
    }
}
