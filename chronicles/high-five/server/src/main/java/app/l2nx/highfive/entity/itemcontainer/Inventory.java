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
package app.l2nx.highfive.entity.itemcontainer;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.config.custom.TransmogConfig;
import app.l2nx.highfive.data.holders.ArmorSet;
import app.l2nx.highfive.data.xml.ArmorSetData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.type.EtcItemType;
import app.l2nx.highfive.entity.item.type.WeaponType;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerItemUnequip;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SkillCoolTime;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.item.model.ItemRow;
import app.l2nx.highfive.repository.item.model.StoredItem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * This class manages inventory. A paperdoll listener may equip or unequip other slots, so the listeners are walked
 * over a copy.
 * @version $Revision: 1.13.2.9.2.12 $ $Date: 2005/03/29 23:15:15 $ rewritten 23.2.2006 by Advi
 */
@Slf4j
@SingleWriter
public abstract class Inventory extends ItemContainer {

    public interface PaperdollListener {
        void notifyEquiped(int slot, Item inst, Inventory inventory);

        void notifyUnequiped(int slot, Item inst, Inventory inventory);
    }

    public static final int ADENA_ID = 57;
    public static final int ANCIENT_ADENA_ID = 5575;

    public static final int PAPERDOLL_UNDER = 0;
    public static final int PAPERDOLL_HEAD = 1;
    public static final int PAPERDOLL_HAIR = 2;
    public static final int PAPERDOLL_HAIR2 = 3;
    public static final int PAPERDOLL_NECK = 4;
    public static final int PAPERDOLL_RHAND = 5;
    public static final int PAPERDOLL_CHEST = 6;
    public static final int PAPERDOLL_LHAND = 7;
    public static final int PAPERDOLL_REAR = 8;
    public static final int PAPERDOLL_LEAR = 9;
    public static final int PAPERDOLL_GLOVES = 10;
    public static final int PAPERDOLL_LEGS = 11;
    public static final int PAPERDOLL_FEET = 12;
    public static final int PAPERDOLL_RFINGER = 13;
    public static final int PAPERDOLL_LFINGER = 14;
    public static final int PAPERDOLL_LBRACELET = 15;
    public static final int PAPERDOLL_RBRACELET = 16;
    public static final int PAPERDOLL_DECO1 = 17;
    public static final int PAPERDOLL_DECO2 = 18;
    public static final int PAPERDOLL_DECO3 = 19;
    public static final int PAPERDOLL_DECO4 = 20;
    public static final int PAPERDOLL_DECO5 = 21;
    public static final int PAPERDOLL_DECO6 = 22;
    public static final int PAPERDOLL_CLOAK = 23;
    public static final int PAPERDOLL_BELT = 24;
    public static final int PAPERDOLL_TOTALSLOTS = 25;

    public static final double MAX_ARMOR_WEIGHT = 12000;

    private final Item[] paperdoll;
    private final ObjectArrayList<PaperdollListener> paperdollListeners;

    protected int totalWeight;

    private int wearedMask;

    private static class ChangeRecorder implements PaperdollListener {
        private final ObjectArrayList<Item> changedItems = new ObjectArrayList<>(1);

        ChangeRecorder(Inventory inventory) {
            inventory.addPaperdollListener(this);
        }

        @Override
        public void notifyEquiped(int slot, Item item, Inventory inventory) {
            changedItems.add(item);
        }

        @Override
        public void notifyUnequiped(int slot, Item item, Inventory inventory) {
            changedItems.add(item);
        }

        List<Item> changedItems() {
            return ObjectLists.unmodifiable(changedItems);
        }
    }

    private static class BowCrossRodListener implements PaperdollListener {
        @Getter
        private static BowCrossRodListener instance = new BowCrossRodListener();

        @Override
        public void notifyUnequiped(int slot, Item item, Inventory inventory) {
            if (slot != PAPERDOLL_RHAND) {
                return;
            }

            if (item.getItemType() == WeaponType.BOW) {
                final Item arrow = inventory.getPaperdollItem(PAPERDOLL_LHAND);
                if (arrow != null) {
                    inventory.setPaperdollItem(PAPERDOLL_LHAND, null);
                }
            } else if (item.getItemType() == WeaponType.CROSSBOW) {
                final Item bolts = inventory.getPaperdollItem(PAPERDOLL_LHAND);
                if (bolts != null) {
                    inventory.setPaperdollItem(PAPERDOLL_LHAND, null);
                }
            } else if (item.getItemType() == WeaponType.FISHINGROD) {
                final Item lure = inventory.getPaperdollItem(PAPERDOLL_LHAND);
                if (lure != null) {
                    inventory.setPaperdollItem(PAPERDOLL_LHAND, null);
                }
            }
        }

        @Override
        public void notifyEquiped(int slot, Item item, Inventory inventory) {
            if (slot != PAPERDOLL_RHAND) {
                return;
            }

            if (item.getItemType() == WeaponType.BOW) {
                final Item arrow = inventory.findArrowForBow(item.getTemplate());
                if (arrow != null) {
                    inventory.setPaperdollItem(PAPERDOLL_LHAND, arrow);
                }
            } else if (item.getItemType() == WeaponType.CROSSBOW) {
                final Item bolts = inventory.findBoltForCrossBow(item.getTemplate());
                if (bolts != null) {
                    inventory.setPaperdollItem(PAPERDOLL_LHAND, bolts);
                }
            }
        }
    }

    private static class StatsListener implements PaperdollListener {
        @Getter
        private static StatsListener instance = new StatsListener();

        @Override
        public void notifyUnequiped(int slot, Item item, Inventory inventory) {
            inventory.getOwner().removeStatsOwner(item);
        }

        @Override
        public void notifyEquiped(int slot, Item item, Inventory inventory) {
            inventory.getOwner().addStatFuncs(item.getStatFuncs(inventory.getOwner()));
        }
    }

    @Slf4j
    private static class ItemSkillsListener implements PaperdollListener {
        @Getter
        private static ItemSkillsListener instance = new ItemSkillsListener();

        @Override
        public void notifyUnequiped(int slot, Item item, Inventory inventory) {
            if (!inventory.getOwner().isPlayer()) {
                return;
            }

            final Player player = inventory.getOwner().asPlayer();
            Skill enchant4Skill;
            Skill itemSkill;
            final ItemTemplate it = item.getTemplate();
            boolean update = false;
            boolean updateTimeStamp = false;

            if (item.isAugmented()) {
                item.getAugmentation().removeBonus(player);
            }

            item.removeElementAttrBonus(player);

            if (item.getEnchantLevel() >= 4) {
                enchant4Skill = it.getEnchant4Skill();
                if (enchant4Skill != null) {
                    player.removeSkill(enchant4Skill, false, enchant4Skill.isPassive());
                    update = true;
                }
            }

            item.clearEnchantStats();

            final SkillHolder[] skills = it.getSkills();
            if (skills != null) {
                for (SkillHolder skillInfo : skills) {
                    if (skillInfo == null) {
                        continue;
                    }

                    itemSkill = skillInfo.getSkill();
                    if (itemSkill != null) {
                        player.removeSkill(itemSkill, false, itemSkill.isPassive());
                        update = true;
                    } else {
                        log.warn("Inventory.ItemSkillsListener.Weapon: Incorrect skill: {}.", skillInfo);
                    }
                }
            }

            if (item.isArmor()) {
                try (Snapshot<Item> items = ScratchBuffers.snapshot(inventory.liveItems())) {
                    for (Item itm : items) {
                        if (!itm.isEquipped() || (itm.getTemplate().getSkills() == null) || itm.equals(item)) {
                            continue;
                        }

                        for (SkillHolder sk : itm.getTemplate().getSkills()) {
                            if (player.getSkillLevel(sk.getSkillId()) != 0) {
                                continue;
                            }

                            itemSkill = sk.getSkill();
                            if (itemSkill != null) {
                                player.addSkill(itemSkill, false);
                                if (itemSkill.isActive()) {
                                    if (!player.hasSkillReuse(itemSkill.getReuseHashCode())) {
                                        final int equipDelay = item.getEquipReuseDelay();
                                        if (equipDelay > 0) {
                                            player.addTimeStamp(itemSkill, equipDelay);
                                            player.disableSkill(itemSkill, equipDelay);
                                        }
                                    }

                                    updateTimeStamp = true;
                                }

                                update = true;
                            }
                        }
                    }
                }
            }

            final Skill unequipSkill = it.getUnequipSkill();
            if (unequipSkill != null) {
                unequipSkill.activateSkill(player, Collections.singletonList(player));
            }

            if (update) {
                player.sendSkillList();

                if (updateTimeStamp) {
                    player.sendPacket(new SkillCoolTime(player));
                }
            }
        }

        @Override
        public void notifyEquiped(int slot, Item item, Inventory inventory) {
            if (!inventory.getOwner().isPlayer()) {
                return;
            }

            final Player player = inventory.getOwner().asPlayer();
            Skill enchant4Skill;
            Skill itemSkill;
            final ItemTemplate it = item.getTemplate();
            boolean update = false;
            boolean updateTimeStamp = false;

            if (item.isAugmented()) {
                item.getAugmentation().applyBonus(player);
            }

            item.updateElementAttrBonus(player);

            if (item.getEnchantLevel() >= 4) {
                enchant4Skill = it.getEnchant4Skill();
                if (enchant4Skill != null) {
                    player.addSkill(enchant4Skill, false);
                    update = true;
                }
            }

            item.applyEnchantStats();

            final SkillHolder[] skills = it.getSkills();
            if (skills != null) {
                for (SkillHolder skillInfo : skills) {
                    if (skillInfo == null) {
                        continue;
                    }

                    itemSkill = skillInfo.getSkill();
                    if (itemSkill != null) {
                        itemSkill.setReferenceItemId(item.getId());
                        player.addSkill(itemSkill, false);
                        if (itemSkill.isActive()) {
                            if (!player.hasSkillReuse(itemSkill.getReuseHashCode())) {
                                final int equipDelay = item.getEquipReuseDelay();
                                if (equipDelay > 0) {
                                    player.addTimeStamp(itemSkill, equipDelay);
                                    player.disableSkill(itemSkill, equipDelay);
                                }
                            }

                            updateTimeStamp = true;
                        }

                        update = true;
                    } else {
                        log.warn("Inventory.ItemSkillsListener.Weapon: Incorrect skill: {}.", skillInfo);
                    }
                }
            }

            if (update) {
                player.sendSkillList();

                if (updateTimeStamp) {
                    player.sendPacket(new SkillCoolTime(player));
                }
            }
        }
    }

    @Slf4j
    private static class ArmorSetListener implements PaperdollListener {
        @Getter
        private static ArmorSetListener instance = new ArmorSetListener();

        @Override
        public void notifyEquiped(int slot, Item item, Inventory inventory) {
            if (!inventory.getOwner().isPlayer()) {
                return;
            }

            final Player player = inventory.getOwner().asPlayer();

            final Item chestItem = inventory.getPaperdollItem(PAPERDOLL_CHEST);
            if (chestItem == null) {
                return;
            }

            if (!ArmorSetData.getInstance().isArmorSet(chestItem.getId())) {
                return;
            }

            final ArmorSet armorSet = ArmorSetData.getInstance().getSet(chestItem.getId());
            boolean update = false;
            boolean updateTimeStamp = false;

            if (armorSet.containItem(slot, item.getId())) {
                if (armorSet.containAll(player)) {
                    Skill itemSkill;
                    final List<SkillHolder> skills = armorSet.getSkills();
                    if (skills != null) {
                        for (SkillHolder holder : skills) {
                            itemSkill = holder.getSkill();
                            if (itemSkill != null) {
                                player.addSkill(itemSkill, false);
                                if (itemSkill.isActive()) {
                                    if (!player.hasSkillReuse(itemSkill.getReuseHashCode())) {
                                        final int equipDelay = item.getEquipReuseDelay();
                                        if (equipDelay > 0) {
                                            player.addTimeStamp(itemSkill, equipDelay);
                                            player.disableSkill(itemSkill, equipDelay);
                                        }
                                    }

                                    updateTimeStamp = true;
                                }

                                update = true;
                            } else {
                                log.warn("Inventory.ArmorSetListener: Incorrect skill: {}.", holder);
                            }
                        }
                    }

                    if (armorSet.containShield(player)) {
                        for (SkillHolder holder : armorSet.getShieldSkillId()) {
                            if (holder.getSkill() != null) {
                                player.addSkill(holder.getSkill(), false);
                                update = true;
                            } else {
                                log.warn("Inventory.ArmorSetListener: Incorrect skill: {}.", holder);
                            }
                        }
                    }

                    if (armorSet.isEnchanted6(player)) {
                        for (SkillHolder holder : armorSet.getEnchant6skillId()) {
                            if (holder.getSkill() != null) {
                                player.addSkill(holder.getSkill(), false);
                                update = true;
                            } else {
                                log.warn("Inventory.ArmorSetListener: Incorrect skill: {}.", holder);
                            }
                        }
                    }
                }
            } else if (armorSet.containShield(item.getId())) {
                for (SkillHolder holder : armorSet.getShieldSkillId()) {
                    if (holder.getSkill() != null) {
                        player.addSkill(holder.getSkill(), false);
                        update = true;
                    } else {
                        log.warn("Inventory.ArmorSetListener: Incorrect skill: {}.", holder);
                    }
                }
            }

            if (update) {
                player.sendSkillList();

                if (updateTimeStamp) {
                    player.sendPacket(new SkillCoolTime(player));
                }
            }
        }

        @Override
        public void notifyUnequiped(int slot, Item item, Inventory inventory) {
            if (!inventory.getOwner().isPlayer()) {
                return;
            }

            final Player player = inventory.getOwner().asPlayer();
            boolean remove = false;
            Skill itemSkill;
            List<SkillHolder> skills = null;
            List<SkillHolder> shieldSkill = null;
            List<SkillHolder> skillId6 = null;
            if (slot == PAPERDOLL_CHEST) {
                if (!ArmorSetData.getInstance().isArmorSet(item.getId())) {
                    return;
                }

                final ArmorSet armorSet = ArmorSetData.getInstance().getSet(item.getId());
                remove = true;
                skills = armorSet.getSkills();
                shieldSkill = armorSet.getShieldSkillId();
                skillId6 = armorSet.getEnchant6skillId();
            } else {
                final Item chestItem = inventory.getPaperdollItem(PAPERDOLL_CHEST);
                if (chestItem == null) {
                    return;
                }

                final ArmorSet armorSet = ArmorSetData.getInstance().getSet(chestItem.getId());
                if (armorSet == null) {
                    return;
                }

                if (armorSet.containItem(slot, item.getId())) {
                    remove = true;
                    skills = armorSet.getSkills();
                    shieldSkill = armorSet.getShieldSkillId();
                    skillId6 = armorSet.getEnchant6skillId();
                } else if (armorSet.containShield(item.getId())) {
                    remove = true;
                    shieldSkill = armorSet.getShieldSkillId();
                }
            }

            if (remove) {
                if (skills != null) {
                    for (SkillHolder holder : skills) {
                        itemSkill = holder.getSkill();
                        if (itemSkill != null) {
                            player.removeSkill(itemSkill, false, itemSkill.isPassive());
                        } else {
                            log.warn("Inventory.ArmorSetListener: Incorrect skill: {}.", holder);
                        }
                    }
                }

                if (shieldSkill != null) {
                    for (SkillHolder holder : shieldSkill) {
                        itemSkill = holder.getSkill();
                        if (itemSkill != null) {
                            player.removeSkill(itemSkill, false, itemSkill.isPassive());
                        } else {
                            log.warn("Inventory.ArmorSetListener: Incorrect skill: {}.", holder);
                        }
                    }
                }

                if (skillId6 != null) {
                    for (SkillHolder holder : skillId6) {
                        itemSkill = holder.getSkill();
                        if (itemSkill != null) {
                            player.removeSkill(itemSkill, false, itemSkill.isPassive());
                        } else {
                            log.warn("Inventory.ArmorSetListener: Incorrect skill: {}.", holder);
                        }
                    }
                }

                player.checkItemRestriction();
                player.sendSkillList();
            }
        }
    }

    private static class BraceletListener implements PaperdollListener {
        @Getter
        private static BraceletListener instance = new BraceletListener();

        @Override
        public void notifyUnequiped(int slot, Item item, Inventory inventory) {
            final Player player = item.asPlayer();
            if ((player != null) && player.isChangingClass()) {
                return;
            }

            if (item.getTemplate().getBodyPart() == BodyPart.R_BRACELET) {
                inventory.unEquipItemInSlot(PAPERDOLL_DECO1);
                inventory.unEquipItemInSlot(PAPERDOLL_DECO2);
                inventory.unEquipItemInSlot(PAPERDOLL_DECO3);
                inventory.unEquipItemInSlot(PAPERDOLL_DECO4);
                inventory.unEquipItemInSlot(PAPERDOLL_DECO5);
                inventory.unEquipItemInSlot(PAPERDOLL_DECO6);
            }
        }

        @Override
        public void notifyEquiped(int slot, Item item, Inventory inventory) {}
    }

    protected Inventory() {
        paperdoll = new Item[PAPERDOLL_TOTALSLOTS];
        paperdollListeners = new ObjectArrayList<>();
        if (this instanceof PlayerInventory) {
            paperdollListeners.add(ArmorSetListener.getInstance());
            paperdollListeners.add(BowCrossRodListener.getInstance());
            paperdollListeners.add(ItemSkillsListener.getInstance());
            paperdollListeners.add(BraceletListener.getInstance());
        }

        paperdollListeners.add(StatsListener.getInstance());
    }

    protected abstract ItemLocation getEquipLocation();

    private ChangeRecorder newRecorder() {
        return new ChangeRecorder(this);
    }

    public Item dropItem(ItemProcessType process, Item item, Player actor, Object reference) {
        WorldPartition.checkOwner();
        if ((item == null) || (getItemByObjectId(item.getObjectId()) == null)) {
            return null;
        }

        removeItem(item);
        item.setOwnerId(process, 0, actor, reference);
        item.setItemLocation(ItemLocation.VOID);
        item.setLastChange(Item.REMOVED);

        item.updateDatabase();
        refreshWeight();
        return item;
    }

    public Item dropItem(ItemProcessType process, int objectId, long count, Player actor, Object reference) {
        WorldPartition.checkOwner();
        final Item item = getItemByObjectId(objectId);
        if (item == null) {
            return null;
        }

        if (item.getCount() > count) {
            item.changeCount(process, -count, actor, reference);
            item.setLastChange(Item.MODIFIED);
            item.updateDatabase();

            final Item newItem = ItemManager.createItem(process, item.getId(), count, actor, reference);
            newItem.updateDatabase();
            refreshWeight();
            return newItem;
        }

        return dropItem(process, item, actor, reference);
    }

    @Override
    protected void addItem(Item item) {
        super.addItem(item);
        if (item.isEquipped()) {
            equipItem(item);
        }

        if (item.getTemplate().getSkills() != null) {
            final Player owner = getOwner().asPlayer();
            if (owner != null) {
                owner.sendSkillList();
            }
        }
    }

    @Override
    protected boolean removeItem(Item item) {
        for (int i = 0; i < paperdoll.length; i++) {
            if (paperdoll[i] == item) {
                unEquipItemInSlot(i);
            }
        }

        if (item.getTemplate().getSkills() != null) {
            final Player owner = getOwner().asPlayer();
            if (owner != null) {
                owner.sendSkillList();
            }
        }

        return super.removeItem(item);
    }

    public Item getPaperdollItem(int slot) {
        WorldPartition.checkOwner();
        return paperdoll[slot];
    }

    public boolean isPaperdollSlotEmpty(int slot) {
        WorldPartition.checkOwner();
        return paperdoll[slot] == null;
    }

    public static int getPaperdollIndex(BodyPart bodyPart) {
        WorldPartition.checkOwner();
        return paperdollIndex(bodyPart);
    }

    private static int paperdollIndex(BodyPart bodyPart) {
        return bodyPart == null ? -1 : bodyPart.getPaperdollSlot();
    }

    public Item getPaperdollItemByBodyPart(BodyPart bodyPart) {
        WorldPartition.checkOwner();
        final int index = paperdollIndex(bodyPart);
        return index == -1 ? null : paperdoll[index];
    }

    public int getPaperdollItemId(int slot) {
        WorldPartition.checkOwner();
        return paperdollItemId(slot);
    }

    private int paperdollItemId(int slot) {
        final Item item = paperdoll[slot];
        if (item != null) {
            if (TransmogConfig.ENABLE_TRANSMOG) {
                final int transmogId = item.getTransmogId();
                if (transmogId > 0) {
                    return transmogId;
                }
            }

            return item.getId();
        }

        return 0;
    }

    public Item getPaperdollItemByItemId(int itemId) {
        WorldPartition.checkOwner();
        for (Item item : paperdoll) {
            if ((item != null) && (item.getId() == itemId)) {
                return item;
            }
        }

        return null;
    }

    public int getPaperdollItemDisplayId(int slot) {
        WorldPartition.checkOwner();
        final Item item = paperdoll[slot];
        if (item != null) {
            if (TransmogConfig.ENABLE_TRANSMOG) {
                final int transmogId = item.getTransmogId();
                if (transmogId > 0) {
                    return transmogId;
                }
            }

            return item.getDisplayId();
        }

        return 0;
    }

    public int getPaperdollAugmentationId(int slot) {
        WorldPartition.checkOwner();
        final Item item = paperdoll[slot];
        return ((item != null) && (item.getAugmentation() != null))
                ? item.getAugmentation().getAugmentationId()
                : 0;
    }

    public int getPaperdollObjectId(int slot) {
        WorldPartition.checkOwner();
        final Item item = paperdoll[slot];
        return (item != null) ? item.getObjectId() : 0;
    }

    public void addPaperdollListener(PaperdollListener listener) {
        WorldPartition.checkOwner();
        if (!paperdollListeners.contains(listener)) {
            paperdollListeners.add(listener);
        }
    }

    public void removePaperdollListener(PaperdollListener listener) {
        WorldPartition.checkOwner();
        paperdollListeners.remove(listener);
    }

    /**
     * The item must already be in the inventory.
     * @return the item previously in the slot
     */
    public Item setPaperdollItem(int slot, Item item) {
        WorldPartition.checkOwner();
        final Item old = paperdoll[slot];
        if (old != item) {
            if (old != null) {
                paperdoll[slot] = null;

                old.setItemLocation(getBaseLocation());
                old.setLastChange(Item.MODIFIED);

                int mask = 0;
                for (int i = 0; i < PAPERDOLL_TOTALSLOTS; i++) {
                    final Item pi = paperdoll[i];
                    if (pi != null) {
                        mask |= pi.getTemplate().getItemMask();
                    }
                }

                wearedMask = mask;

                try (Snapshot<PaperdollListener> listeners = ScratchBuffers.snapshot(paperdollListeners)) {
                    for (PaperdollListener listener : listeners) {
                        listener.notifyUnequiped(slot, old, this);
                    }
                }

                old.updateDatabase();
            }

            if (item != null) {
                paperdoll[slot] = item;
                item.setItemLocation(getEquipLocation(), slot);
                item.setLastChange(Item.MODIFIED);
                wearedMask |= item.getTemplate().getItemMask();
                try (Snapshot<PaperdollListener> listeners = ScratchBuffers.snapshot(paperdollListeners)) {
                    for (PaperdollListener listener : listeners) {
                        listener.notifyEquiped(slot, item, this);
                    }
                }

                item.updateDatabase();
            }
        }

        if (old != null) {
            final Creature owner = getOwner();
            if ((owner != null)
                    && owner.isPlayer()
                    && EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_ITEM_UNEQUIP, old.getTemplate())) {
                EventDispatcher.getInstance()
                        .notifyEventAsync(new OnPlayerItemUnequip(owner.asPlayer(), old), old.getTemplate());
            }
        }

        return old;
    }

    /**
     * @return the items equipped or unequipped on the way, read-only
     */
    public List<Item> unEquipItemInBodySlotAndRecord(BodyPart bodyPart) {
        WorldPartition.checkOwner();
        final ChangeRecorder recorder = newRecorder();
        try {
            unEquipItemInBodySlot(bodyPart);
        } finally {
            removePaperdollListener(recorder);
        }

        return recorder.changedItems();
    }

    public Item unEquipItemInSlot(int pdollSlot) {
        WorldPartition.checkOwner();
        return setPaperdollItem(pdollSlot, null);
    }

    /**
     * @return the items equipped or unequipped on the way, read-only
     */
    public List<Item> unEquipItemInSlotAndRecord(int slot) {
        WorldPartition.checkOwner();
        final ChangeRecorder recorder = newRecorder();
        try {
            unEquipItemInSlot(slot);
            if (getOwner().isPlayer()) {
                getOwner().asPlayer().refreshExpertisePenalty();
            }
        } finally {
            removePaperdollListener(recorder);
        }

        return recorder.changedItems();
    }

    public Item unEquipItemInBodySlot(BodyPart bodyPart) {
        WorldPartition.checkOwner();
        if (bodyPart == BodyPart.HAIRALL) {
            setPaperdollItem(PAPERDOLL_HAIR, null);
        }

        final int pdollSlot = BodyPart.getPaperdollIndex(bodyPart);
        if (pdollSlot >= 0) {
            final Item old = setPaperdollItem(pdollSlot, null);
            if ((old != null) && getOwner().isPlayer()) {
                getOwner().asPlayer().refreshExpertisePenalty();
            }

            return old;
        }

        return null;
    }

    /**
     * @return the items equipped or unequipped on the way, read-only
     */
    public List<Item> equipItemAndRecord(Item item) {
        WorldPartition.checkOwner();
        final ChangeRecorder recorder = newRecorder();
        try {
            equipItem(item);
        } finally {
            removePaperdollListener(recorder);
        }

        return recorder.changedItems();
    }

    public void equipItem(Item item) {
        WorldPartition.checkOwner();
        if (item.isPending()) {
            return;
        }

        if (getOwner().isPlayer()) {
            if (getOwner().asPlayer().isInStoreMode()) {
                return;
            }

            final Player player = getOwner().asPlayer();
            if (!player.isGM() && !player.isHero() && item.isHeroItem()) {
                return;
            }
        }

        final BodyPart bodyPart = item.getTemplate().getBodyPart();

        final Item formal = getPaperdollItem(PAPERDOLL_CHEST);
        if ((item.getId() != 21163 /* Wedding Bouquet */)
                && (formal != null)
                && (formal.getTemplate().getBodyPart() == BodyPart.ALLDRESS)) {
            switch (bodyPart) {
                case LR_HAND:
                case L_HAND:
                case R_HAND:
                case LEGS:
                case FEET:
                case GLOVES:
                case HEAD: {
                    return;
                }
            }
        }

        switch (bodyPart) {
            case LR_HAND: {
                setPaperdollItem(PAPERDOLL_LHAND, null);
                setPaperdollItem(PAPERDOLL_RHAND, item);
                break;
            }
            case L_HAND: {
                final Item rh = getPaperdollItem(PAPERDOLL_RHAND);
                if ((rh != null)
                        && (rh.getTemplate().getBodyPart() == BodyPart.LR_HAND)
                        && !(((rh.getItemType() == WeaponType.BOW) && (item.getItemType() == EtcItemType.ARROW))
                                || ((rh.getItemType() == WeaponType.CROSSBOW)
                                        && (item.getItemType() == EtcItemType.BOLT))
                                || ((rh.getItemType() == WeaponType.FISHINGROD)
                                        && (item.getItemType() == EtcItemType.LURE)))) {
                    setPaperdollItem(PAPERDOLL_RHAND, null);
                }

                setPaperdollItem(PAPERDOLL_LHAND, item);
                break;
            }
            case R_HAND: {
                // Arrows are not handled here: the paperdoll listener unequips them.
                setPaperdollItem(PAPERDOLL_RHAND, item);
                break;
            }
            case L_EAR:
            case R_EAR:
            case LR_EAR: {
                if (paperdoll[PAPERDOLL_LEAR] == null) {
                    setPaperdollItem(PAPERDOLL_LEAR, item);
                } else if (paperdoll[PAPERDOLL_REAR] == null) {
                    setPaperdollItem(PAPERDOLL_REAR, item);
                } else {
                    setPaperdollItem(PAPERDOLL_LEAR, item);
                }
                break;
            }
            case L_FINGER:
            case R_FINGER:
            case LR_FINGER: {
                if (paperdoll[PAPERDOLL_LFINGER] == null) {
                    setPaperdollItem(PAPERDOLL_LFINGER, item);
                } else if (paperdoll[PAPERDOLL_RFINGER] == null) {
                    setPaperdollItem(PAPERDOLL_RFINGER, item);
                } else {
                    setPaperdollItem(PAPERDOLL_LFINGER, item);
                }
                break;
            }
            case NECK: {
                setPaperdollItem(PAPERDOLL_NECK, item);
                break;
            }
            case FULL_ARMOR: {
                setPaperdollItem(PAPERDOLL_LEGS, null);
                setPaperdollItem(PAPERDOLL_CHEST, item);
                break;
            }
            case CHEST: {
                setPaperdollItem(PAPERDOLL_CHEST, item);
                break;
            }
            case LEGS: {
                final Item chest = getPaperdollItem(PAPERDOLL_CHEST);
                if ((chest != null) && (chest.getTemplate().getBodyPart() == BodyPart.FULL_ARMOR)) {
                    setPaperdollItem(PAPERDOLL_CHEST, null);
                }

                setPaperdollItem(PAPERDOLL_LEGS, item);
                break;
            }
            case FEET: {
                setPaperdollItem(PAPERDOLL_FEET, item);
                break;
            }
            case GLOVES: {
                setPaperdollItem(PAPERDOLL_GLOVES, item);
                break;
            }
            case HEAD: {
                setPaperdollItem(PAPERDOLL_HEAD, item);
                break;
            }
            case HAIR: {
                final Item hair = getPaperdollItem(PAPERDOLL_HAIR);
                if ((hair != null) && (hair.getTemplate().getBodyPart() == BodyPart.HAIRALL)) {
                    setPaperdollItem(PAPERDOLL_HAIR2, null);
                } else {
                    setPaperdollItem(PAPERDOLL_HAIR, null);
                }

                setPaperdollItem(PAPERDOLL_HAIR, item);
                break;
            }
            case HAIR2: {
                final Item hair2 = getPaperdollItem(PAPERDOLL_HAIR);
                if ((hair2 != null) && (hair2.getTemplate().getBodyPart() == BodyPart.HAIRALL)) {
                    setPaperdollItem(PAPERDOLL_HAIR, null);
                } else {
                    setPaperdollItem(PAPERDOLL_HAIR2, null);
                }

                setPaperdollItem(PAPERDOLL_HAIR2, item);
                break;
            }
            case HAIRALL: {
                setPaperdollItem(PAPERDOLL_HAIR2, null);
                setPaperdollItem(PAPERDOLL_HAIR, item);
                break;
            }
            case UNDERWEAR: {
                setPaperdollItem(PAPERDOLL_UNDER, item);
                break;
            }
            case BACK: {
                setPaperdollItem(PAPERDOLL_CLOAK, item);
                break;
            }
            case L_BRACELET: {
                setPaperdollItem(PAPERDOLL_LBRACELET, item);
                break;
            }
            case R_BRACELET: {
                setPaperdollItem(PAPERDOLL_RBRACELET, item);
                break;
            }
            case DECO: {
                equipTalisman(item);
                break;
            }
            case BELT: {
                setPaperdollItem(PAPERDOLL_BELT, item);
                break;
            }
            case ALLDRESS: {
                setPaperdollItem(PAPERDOLL_LEGS, null);
                setPaperdollItem(PAPERDOLL_LHAND, null);
                setPaperdollItem(PAPERDOLL_RHAND, null);
                setPaperdollItem(PAPERDOLL_HEAD, null);
                setPaperdollItem(PAPERDOLL_FEET, null);
                setPaperdollItem(PAPERDOLL_GLOVES, null);
                setPaperdollItem(PAPERDOLL_CHEST, item);
                break;
            }
            default: {
                log.warn("Unknown body slot {} for Item ID: {}", bodyPart, item.getId());
                break;
            }
        }
    }

    @Override
    protected void refreshWeight() {
        long weight = 0;
        for (Item item : liveItems()) {
            if (item.getTemplate() != null) {
                weight += item.getTemplate().getWeight() * item.getCount();
            }
        }

        totalWeight = (int) Math.min(weight, Integer.MAX_VALUE);
    }

    public Item findArrowForBow(ItemTemplate bow) {
        WorldPartition.checkOwner();
        return bow == null ? null : findAmmunition(bow, EtcItemType.ARROW);
    }

    public Item findBoltForCrossBow(ItemTemplate crossbow) {
        WorldPartition.checkOwner();
        return findAmmunition(crossbow, EtcItemType.BOLT);
    }

    private Item findAmmunition(ItemTemplate weapon, EtcItemType type) {
        for (Item item : freeItems(item -> item.isEtcItem()
                && (item.getTemplate().getCrystalTypePlus() == weapon.getCrystalTypePlus())
                && (item.getEtcItem().getItemType() == type))) {
            return item;
        }

        return null;
    }

    @Override
    public void restore() {
        WorldPartition.checkOwner();
        try {
            for (ItemRow row :
                    ItemManager.getInstance().findItems(getOwnerId(), getBaseLocation(), getEquipLocation())) {
                restoreItem(Item.restoreFromDb(getOwnerId(), row));
            }

            refreshWeight();
        } catch (DataAccessException e) {
            log.warn("Could not restore inventory:", e);
        }
    }

    @Override
    public void restore(List<StoredItem> stored) {
        WorldPartition.checkOwner();
        for (StoredItem row : stored) {
            restoreItem(Item.restoreStored(getOwnerId(), IdManager.getInstance().handleOf(row.id()), row));
        }

        refreshWeight();
    }

    private void restoreItem(Item item) {
        if (item == null) {
            return;
        }

        if (getOwner().isPlayer()) {
            final Player player = getOwner().asPlayer();
            if (!player.isGM() && !player.isHero() && item.isHeroItem()) {
                item.setItemLocation(ItemLocation.INVENTORY);
            }
        }

        World.addObject(item);

        if (item.isStackable() && (getItemByItemIdIncludingPending(item.getId()) != null)) {
            addItem(ItemProcessType.RESTORE, item, getOwner().asPlayer(), null);
        } else {
            addItem(item);
        }
    }

    public int getTalismanSlots() {
        WorldPartition.checkOwner();
        return getOwner().asPlayer().getStat().getTalismanSlots();
    }

    private void equipTalisman(Item item) {
        if (getTalismanSlots() == 0) {
            return;
        }

        for (int i = PAPERDOLL_DECO1; i < (PAPERDOLL_DECO1 + getTalismanSlots()); i++) {
            if ((paperdoll[i] != null) && (paperdollItemId(i) == item.getId())) {
                setPaperdollItem(i, item);
                return;
            }
        }

        for (int i = PAPERDOLL_DECO1; i < (PAPERDOLL_DECO1 + getTalismanSlots()); i++) {
            if (paperdoll[i] == null) {
                setPaperdollItem(i, item);
                return;
            }
        }

        setPaperdollItem(PAPERDOLL_DECO1, item);
    }

    public boolean canEquipCloak() {
        WorldPartition.checkOwner();
        return getOwner().asPlayer().getStat().canEquipCloak();
    }

    public void reloadEquippedItems() {
        WorldPartition.checkOwner();
        // Reads each slot live: a listener may empty a later slot (a bow takes its arrows off), which then gets no
        // notify; the array is fixed-size, so the index walk stays valid.
        for (int i = 0; i < paperdoll.length; i++) {
            final Item item = paperdoll[i];
            if (item == null) {
                continue;
            }

            final int slot = item.getLocationSlot();
            try (Snapshot<PaperdollListener> listeners = ScratchBuffers.snapshot(paperdollListeners)) {
                for (PaperdollListener listener : listeners) {
                    listener.notifyUnequiped(slot, item, this);
                    listener.notifyEquiped(slot, item, this);
                }
            }
        }
    }

    @SafeVarargs
    public final List<Item> getPaperdollItems(Predicate<Item>... filters) {
        WorldPartition.checkOwner();
        final ObjectArrayList<Item> items = new ObjectArrayList<>(PAPERDOLL_TOTALSLOTS);
        for (Item item : paperdoll) {
            if (item != null) {
                items.add(item);
            }
        }

        // The filters are the caller's code, so they run after the walk.
        for (Predicate<Item> filter : filters) {
            items.removeIf(filter.negate());
        }

        return ObjectLists.unmodifiable(items);
    }

    public int getTotalWeight() {
        WorldPartition.checkOwner();
        return totalWeight;
    }

    public int getWearedMask() {
        WorldPartition.checkOwner();
        return wearedMask;
    }
}
