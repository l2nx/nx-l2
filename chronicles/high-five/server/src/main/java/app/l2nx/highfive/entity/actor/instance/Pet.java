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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.RatesConfig;
import app.l2nx.highfive.data.holders.PetData;
import app.l2nx.highfive.data.holders.PetLevelData;
import app.l2nx.highfive.data.sql.CharSummonTable;
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
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExChangeNpcState;
import app.l2nx.highfive.network.protocol.p267.serverpackets.InventoryUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PetInventoryUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StatusUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StopMove;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.pet.model.PetRow;
import app.l2nx.highfive.repository.pet.model.SummonEffectRow;
import app.l2nx.highfive.taskmanagers.DecayTaskManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

@Slf4j
@SingleWriter
public class Pet extends Summon {

    int currentFed;

    final PetInventory inventory;

    private final int controlObjectId;

    private boolean respawned;

    private final boolean mountable;

    private ScheduledFuture<?> feedTask;
    private PetData data;
    private PetLevelData leveldata;

    private long expBeforeDeath = 0;

    private int curWeightPenalty = 0;

    public Pet(NpcTemplate template, Player owner, Item control) {
        this(template, owner, control, (byte)
                (template.getDisplayId() == 12564 ? owner.getLevel() : template.getLevel()));
    }

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

    public int getCurrentFed() {
        WorldPartition.checkOwner();
        return currentFed;
    }

    @Override
    public PetInventory getInventory() {
        WorldPartition.checkOwner();
        return inventory;
    }

    @Override
    public int getControlObjectId() {
        WorldPartition.checkOwner();
        return controlObjectId;
    }

    public boolean isRespawned() {
        WorldPartition.checkOwner();
        return respawned;
    }

    @Override
    public boolean isMountable() {
        WorldPartition.checkOwner();
        return mountable;
    }

    public PetLevelData getPetLevelData() {
        WorldPartition.checkOwner();
        if (leveldata == null) {
            leveldata = PetDataTable.getInstance()
                    .getPetLevelData(getTemplate().getId(), getStat().getLevel());
        }

        return leveldata;
    }

    public PetData getPetData() {
        WorldPartition.checkOwner();
        if (data == null) {
            data = PetDataTable.getInstance().getPetData(getTemplate().getId());
        }

        return data;
    }

    public void setPetData(PetLevelData value) {
        WorldPartition.checkOwner();
        leveldata = value;
    }

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

                Item food = null;
                for (int id : foodIds) {
                    food = inventory.getItemByItemId(id);
                    if (food != null) {
                        break;
                    }
                }

                if ((food != null) && isHungry()) {
                    final IItemHandler handler = ItemHandler.getInstance().getHandler(food.getEtcItem());
                    if (handler != null) {
                        final SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_PET_WAS_HUNGRY_SO_IT_ATE_S1);
                        sm.addItemName(food.getId());
                        sendPacket(sm);
                        handler.onItemUse(Pet.this, food, false);
                    }
                }

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

    public static Pet spawnPet(NpcTemplate template, Player owner, Item control) {
        WorldPartition.checkOwner();
        final Pet existingPet = World.getPet(owner.getObjectId());
        if (existingPet != null) {
            existingPet.unSummon(owner);
        }

        final PetData data = PetDataTable.getInstance().getPetData(template.getId());
        final Pet pet = restore(control, template, owner);

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
        WorldPartition.checkOwner();
        return (PetStat) super.getStat();
    }

    @Override
    public void initCharStat() {
        WorldPartition.checkOwner();
        setStat(new PetStat(this));
    }

    @Override
    public int getSummonType() {
        WorldPartition.checkOwner();
        return 2;
    }

    public Item getControlItem() {
        WorldPartition.checkOwner();
        return getOwner().getInventory().getItemByObjectId(controlObjectId);
    }

    public void setCurrentFed(int num) {
        WorldPartition.checkOwner();
        if (num <= 0) {
            sendPacket(new ExChangeNpcState(getObjectId(), 0x64));
        } else if ((currentFed <= 0) && (num > 0)) {
            sendPacket(new ExChangeNpcState(getObjectId(), 0x65));
        }

        currentFed = num > getMaxFed() ? getMaxFed() : num;
    }

    @Override
    public Item getActiveWeaponInstance() {
        WorldPartition.checkOwner();
        // Equipping puts an R_HAND item into the right hand slot, so the slot holds the only candidate.
        final Item weapon = inventory.getPaperdollItem(Inventory.PAPERDOLL_RHAND);
        return (weapon != null) && (weapon.getTemplate().getBodyPart() == BodyPart.R_HAND) ? weapon : null;
    }

    @Override
    public Weapon getActiveWeaponItem() {
        WorldPartition.checkOwner();
        final Item weapon = getActiveWeaponInstance();
        return weapon == null ? null : (Weapon) weapon.getTemplate();
    }

    @Override
    public Item getSecondaryWeaponInstance() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public Weapon getSecondaryWeaponItem() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public boolean destroyItem(
            ItemProcessType process, int objectId, long count, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
        final Item item = inventory.destroyItem(process, objectId, count, getOwner(), reference);
        if (item == null) {
            if (sendMessage) {
                sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
            }

            return false;
        }

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

    @Override
    public boolean destroyItemByItemId(
            ItemProcessType process, int itemId, long count, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
        final Item item = inventory.destroyItemByItemId(process, itemId, count, getOwner(), reference);
        if (item == null) {
            if (sendMessage) {
                sendPacket(SystemMessageId.INCORRECT_ITEM_COUNT_2);
            }

            return false;
        }

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
        WorldPartition.checkOwner();
        if (isDead()) {
            return;
        }

        getAI().setIntentionIdle();
        broadcastPacket(new StopMove(this));
        if (!object.isItem()) {
            log.warn("{} trying to pickup wrong target.{}", this, object);
            sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        final Item target = (Item) object;

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
        if (!target.isSpawned()) {
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

        target.pickupMe(this);

        if (GeneralConfig.SAVE_DROPPED_ITEM) {
            ItemsOnGroundManager.getInstance().removeObject(target);
        }

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

            if (getOwner().isInParty()
                    && (getOwner().getParty().getDistributionType() != PartyDistributionType.FINDERS_KEEPERS)) {
                getOwner().getParty().distributeItem(getOwner(), target);
            } else {
                final Item item = inventory.addItem(ItemProcessType.PICKUP, target, getOwner(), this);

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
        WorldPartition.checkOwner();
        inventory.transferItemsToOwner();
        super.deleteMe(owner);
        destroyControlItem(owner, false);
        CharSummonTable.getInstance().getPets().remove(getOwner().getObjectId());
    }

    @Override
    public boolean doDie(Creature killer) {
        WorldPartition.checkOwner();
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

        return true;
    }

    @Override
    public void doRevive() {
        WorldPartition.checkOwner();
        getOwner().removeReviving();

        super.doRevive();

        DecayTaskManager.getInstance().cancel(this);
        startFeed();
        if (!isHungry()) {
            setRunning();
        }

        getAI().setIntentionActive();
    }

    @Override
    public void doRevive(double revivePower) {
        WorldPartition.checkOwner();
        restoreExp(revivePower);
        doRevive();
    }

    public Item transferItem(
            ItemProcessType process, int objectId, long count, Inventory target, Player actor, WorldObject reference) {
        WorldPartition.checkOwner();
        final Item oldItem = inventory.getItemByObjectId(objectId);
        final Item playerOldItem = target.getItemByItemId(oldItem.getId());
        final Item newItem = inventory.transferItem(process, objectId, count, target, actor, reference);
        if (newItem == null) {
            return null;
        }

        final PetInventoryUpdate petIU = new PetInventoryUpdate();
        if ((oldItem.getCount() > 0) && (oldItem != newItem)) {
            petIU.addModifiedItem(oldItem);
        } else {
            petIU.addRemovedItem(oldItem);
        }

        sendPacket(petIU);

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

    public void destroyControlItem(Player owner, boolean evolve) {
        WorldPartition.checkOwner();
        World.removePet(owner.getObjectId());

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
    }

    public void dropAllItems() {
        WorldPartition.checkOwner();
        try {
            for (Item item : inventory.getItems()) {
                dropItemHere(item);
            }
        } catch (Exception e) {
            log.warn("Pet Drop Error: {}", e.getMessage(), e);
        }
    }

    public void dropItemHere(Item item, boolean protect) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        dropItemHere(dropit, false);
    }

    private static Pet restore(Item control, NpcTemplate template, Player owner) {
        final Optional<PetRow> stored;
        try {
            stored = CharSummonTable.getInstance().findPet(control.getObjectId());
        } catch (DataAccessException e) {
            log.warn("Could not restore pet data for owner: {} - {}", owner, e.getMessage(), e);
            return null;
        }

        if (stored.isEmpty()) {
            return template.isType("BabyPet")
                    ? new BabyPet(template, owner, control)
                    : new Pet(template, owner, control);
        }

        final PetRow row = stored.get();
        final Pet pet = template.isType("BabyPet")
                ? new BabyPet(template, owner, control, (byte) row.level())
                : new Pet(template, owner, control, (byte) row.level());

        pet.respawned = true;
        pet.setName(row.name());

        long exp = row.exp();
        final PetLevelData info = PetDataTable.getInstance().getPetLevelData(pet.getId(), pet.getLevel());

        // Avoids de-leveling when exp-per-level values changed.
        if ((info != null) && (exp < info.getPetMaxExp())) {
            exp = info.getPetMaxExp();
        }

        pet.getStat().setExp(exp);
        pet.getStat().setSp(row.sp());

        pet.getStatus().setCurrentHp(row.currentHp());
        pet.getStatus().setCurrentMp(row.currentMp());
        pet.getStatus().setCurrentCp(pet.getMaxCp());
        if (row.currentHp() < 1) {
            pet.setDead(true);
            pet.stopHpMpRegeneration();
        }

        pet.setCurrentFed(row.fed());
        return pet;
    }

    @Override
    public void setRestoreSummon(boolean value) {
        WorldPartition.checkOwner();
        restoreSummon = value;
    }

    @Override
    public void stopSkillEffects(SkillFinishType type, int skillId) {
        WorldPartition.checkOwner();
        super.stopSkillEffects(type, skillId);
        SummonEffectTable.getInstance().removePetEffects(getControlObjectId(), skillId);
    }

    @Override
    public void storeMe() {
        WorldPartition.checkOwner();
        if (controlObjectId == 0) {
            // This is a summon, not a pet, don't store anything.
            return;
        }

        if (!PlayerConfig.RESTORE_PET_ON_RECONNECT) {
            restoreSummon = false;
        }

        try {
            CharSummonTable.getInstance()
                    .savePet(PetRow.builder()
                            .controlItemId(controlObjectId)
                            .ownerId(getOwner().getObjectId())
                            .name(getName())
                            .level(getStat().getLevel())
                            .currentHp((int) Math.round(getStatus().getCurrentHp()))
                            .currentMp((int) Math.round(getStatus().getCurrentMp()))
                            .exp(getStat().getExp())
                            .sp(getStat().getSp())
                            .fed(currentFed)
                            .restore(restoreSummon)
                            .build());

            respawned = true;
            if (restoreSummon) {
                CharSummonTable.getInstance().getPets().put(getOwner().getObjectId(), getControlObjectId());
            } else {
                CharSummonTable.getInstance().getPets().remove(getOwner().getObjectId());
            }
        } catch (DataAccessException e) {
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
        WorldPartition.checkOwner();
        if (!PlayerConfig.SUMMON_STORE_SKILL_COOLTIME) {
            return;
        }

        SummonEffectTable.getInstance().clearPetEffects(getControlObjectId());

        final List<SummonEffectRow> rows = new ArrayList<>();
        if (storeEffects) {
            for (Entry<Skill, StoredEffect> entry : effectsToStore().entrySet()) {
                final Skill skill = entry.getKey();
                final StoredEffect stored = entry.getValue();
                rows.add(SummonEffectRow.builder()
                        .skillId(skill.getId())
                        .skillLevel(skill.getLevel())
                        .remainingMillis(stored.remainingMillis())
                        .tickPhaseMillis(stored.tickPhaseMillis())
                        .build());

                SummonEffectTable.getInstance().addPetEffect(getControlObjectId(), skill, stored);
            }
        }

        try {
            CharSummonTable.getInstance().savePetEffects(controlObjectId, rows);
        } catch (DataAccessException e) {
            log.warn("Could not store pet effect data: ", e);
        }
    }

    @Override
    public void restoreEffects() {
        WorldPartition.checkOwner();
        try {
            if (!SummonEffectTable.getInstance().containsPetId(getControlObjectId())) {
                for (SummonEffectRow row : CharSummonTable.getInstance().findPetEffects(controlObjectId)) {
                    final Skill skill = SkillData.getInstance().getSkill(row.skillId(), row.skillLevel());
                    if (skill == null) {
                        continue;
                    }

                    if (skill.hasEffects(EffectScope.GENERAL)) {
                        CharSummonTable.toStoredEffect(row, controlObjectId)
                                .ifPresent(stored -> SummonEffectTable.getInstance()
                                        .addPetEffect(getControlObjectId(), skill, stored));
                    }
                }
            }

            CharSummonTable.getInstance().deletePetEffects(controlObjectId);
        } catch (DataAccessException e) {
            log.warn("Could not restore {} active effect data: {}", this, e.getMessage(), e);
        } finally {
            SummonEffectTable.getInstance().applyPetEffects(this, getControlObjectId());
        }
    }

    public void stopFeed() {
        WorldPartition.checkOwner();
        if (feedTask == null) {
            return;
        }

        feedTask.cancel(false);
        feedTask = null;
    }

    public void startFeed() {
        WorldPartition.checkOwner();
        stopFeed();
        if (!isDead() && (getOwner().getSummon() == this)) {
            feedTask = WorldPartition.loop().scheduleAtFixedRate(new FeedTask(), 10000, 10000, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void unSummon(Player owner) {
        WorldPartition.checkOwner();
        stopFeed();
        super.unSummon(owner);

        if (!isDead()) {
            if (inventory != null) {
                inventory.deleteMe();
            }

            World.removePet(owner.getObjectId());
        }
    }

    public void restoreExp(double restorePercent) {
        WorldPartition.checkOwner();
        if (expBeforeDeath > 0) {
            getStat().addExp(Math.round(((expBeforeDeath - getStat().getExp()) * restorePercent) / 100));
            expBeforeDeath = 0;
        }
    }

    private void deathPenalty() {
        // TODO: Need Correct Penalty

        final int level = getStat().getLevel();
        final double percentLost = (-0.07 * level) + 6.5;

        final long lostExp = Math.round(
                ((getStat().getExpForLevel(level + 1) - getStat().getExpForLevel(level)) * percentLost) / 100);

        expBeforeDeath = getStat().getExp();

        getStat().addExp(-lostExp);
    }

    @Override
    public void addExpAndSp(double addToExp, double addToSp) {
        WorldPartition.checkOwner();
        if (getId() == 12564) {
            getStat().addExpAndSp(Math.round(addToExp * RatesConfig.SINEATER_XP_RATE), addToSp);
        } else {
            getStat().addExpAndSp(Math.round(addToExp * RatesConfig.PET_XP_RATE), addToSp);
        }
    }

    @Override
    public long getExpForThisLevel() {
        WorldPartition.checkOwner();
        return getStat().getExpForLevel(getLevel());
    }

    @Override
    public long getExpForNextLevel() {
        WorldPartition.checkOwner();
        return getStat().getExpForLevel(getLevel() + 1);
    }

    @Override
    public int getLevel() {
        WorldPartition.checkOwner();
        return getStat().getLevel();
    }

    public int getMaxFed() {
        WorldPartition.checkOwner();
        return getStat().getMaxFeed();
    }

    @Override
    public int getCriticalHit(Creature target, Skill skill) {
        WorldPartition.checkOwner();
        return getStat().getCriticalHit(target, skill);
    }

    @Override
    public int getSkillLevel(int skillId) {
        WorldPartition.checkOwner();
        if (getKnownSkill(skillId) == null) {
            return 0;
        }

        final int level = getLevel();
        return level > 70 ? 7 + ((level - 70) / 5) : level / 10;
    }

    public void updateRefOwner(Player owner) {
        WorldPartition.checkOwner();
        final int oldOwnerId = getOwner().getObjectId();
        setOwner(owner);
        World.removePet(oldOwnerId);
        World.addPet(oldOwnerId, this);
    }

    public int getInventoryLimit() {
        WorldPartition.checkOwner();
        return NpcConfig.INVENTORY_MAXIMUM_PET;
    }

    public void refreshOverloaded() {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        if ((getInventory() != null) && (getOwner() != null)) {
            refreshOverloaded();
        }

        super.updateAndBroadcastStatus(value);
    }

    @Override
    public boolean isHungry() {
        WorldPartition.checkOwner();
        return currentFed
                < ((getPetData().getHungryLimit() / 100f) * getPetLevelData().getPetMaxFeed());
    }

    public boolean isUncontrollable() {
        WorldPartition.checkOwner();
        return currentFed <= 0;
    }

    @Override
    public int getWeapon() {
        WorldPartition.checkOwner();
        final Item weapon = inventory.getPaperdollItem(Inventory.PAPERDOLL_RHAND);
        return weapon != null ? weapon.getId() : 0;
    }

    @Override
    public int getArmor() {
        WorldPartition.checkOwner();
        final Item weapon = inventory.getPaperdollItem(Inventory.PAPERDOLL_CHEST);
        return weapon != null ? weapon.getId() : 0;
    }

    public int getJewel() {
        WorldPartition.checkOwner();
        final Item weapon = inventory.getPaperdollItem(Inventory.PAPERDOLL_NECK);
        return weapon != null ? weapon.getId() : 0;
    }

    @Override
    public short getSoulShotsPerHit() {
        WorldPartition.checkOwner();
        return getPetLevelData().getPetSoulShot();
    }

    @Override
    public short getSpiritShotsPerHit() {
        WorldPartition.checkOwner();
        return getPetLevelData().getPetSpiritShot();
    }

    @Override
    public void setName(String name) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        return data.getFood().contains(itemId);
    }

    @Override
    public boolean isPet() {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public Pet asPet() {
        WorldPartition.checkOwner();
        return this;
    }

    @Override
    public double getRunSpeed() {
        WorldPartition.checkOwner();
        return super.getRunSpeed() * (isUncontrollable() ? 0.5d : 1.0d);
    }

    @Override
    public double getWalkSpeed() {
        WorldPartition.checkOwner();
        return super.getWalkSpeed() * (isUncontrollable() ? 0.5d : 1.0d);
    }

    @Override
    public double getMovementSpeedMultiplier() {
        WorldPartition.checkOwner();
        return super.getMovementSpeedMultiplier() * (isUncontrollable() ? 0.5d : 1.0d);
    }

    @Override
    public double getMoveSpeed() {
        WorldPartition.checkOwner();
        if (isInsideZone(ZoneId.WATER)) {
            return isRunning() ? getSwimRunSpeed() : getSwimWalkSpeed();
        }

        return isRunning() ? getRunSpeed() : getWalkSpeed();
    }
}
