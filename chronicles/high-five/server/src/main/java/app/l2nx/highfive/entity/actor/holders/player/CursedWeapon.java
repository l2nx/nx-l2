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
package app.l2nx.highfive.entity.actor.holders.player;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.data.xml.TransformData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.groups.PartyMessageType;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.CursedWeaponsManager;
import app.l2nx.highfive.mechanics.skill.CommonSkill;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.Earthquake;
import app.l2nx.highfive.network.serverpackets.ExRedSky;
import app.l2nx.highfive.network.serverpackets.SocialAction;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CursedWeapon {

    // name is the name of the cursed weapon associated with its ID.
    @Getter
    private final String name;
    // itemId is the Item ID of the cursed weapon.
    @Getter
    private final int itemId;
    // skillId is the skills ID.
    @Getter
    private final int skillId;

    private final int skillMaxLevel;

    @Setter
    private int dropRate;

    @Setter
    private int duration;

    @Setter
    private int durationLost;

    @Setter
    private int disapearChance;

    @Getter
    @Setter
    private int stageKills;

    // This should be false unless if the cursed weapon is dropped, in that case it would be true.
    @Getter
    @Setter
    private boolean dropped = false;

    // This sets the cursed weapon status to true only if a player has the cursed weapon, otherwise this should be
    // false.
    @Getter
    @Setter
    private boolean activated = false;

    private ScheduledFuture<?> removeTask;

    @Getter
    @Setter
    private int nbKills = 0;

    @Getter
    @Setter
    long endTime = 0;

    @Getter
    @Setter
    private int playerId = 0;

    @Getter
    @Setter
    protected Player player = null;

    @Setter
    private Item item = null;

    @Getter
    @Setter
    private int playerKarma = 0;

    @Getter
    @Setter
    private int playerPkKills = 0;

    protected int transformationId = 0;

    public CursedWeapon(int itemId, int skillId, String name) {
        this.name = name;
        this.itemId = itemId;
        this.skillId = skillId;
        skillMaxLevel = SkillData.getInstance().getMaxLevel(this.skillId);
    }

    public void endOfLife() {
        if (activated) {
            if ((player != null) && player.isOnline()) {
                // Remove from player
                log.info("{} being removed online.", name);
                player.abortAttack();

                player.setKarma(playerKarma);
                player.setPkKills(playerPkKills);
                player.setCursedWeaponEquippedId(0);
                removeSkill();

                // Remove
                player.getInventory().unEquipItemInBodySlot(BodyPart.LR_HAND);
                player.storeMe();

                // Destroy
                player.getInventory().destroyItemByItemId(ItemProcessType.NONE, itemId, 1, player, null);
                player.sendItemList(true);
                player.broadcastUserInfo();
            } else {
                // Remove from Db
                log.info("{} being removed offline.", name);

                CursedWeaponsManager.getInstance().removeFromOfflinePlayer(this, playerId);
            }
        }

        // either this cursed weapon is in the inventory of someone who has another cursed weapon equipped,
        // OR this cursed weapon is on the ground.
        else if ((player != null) && (player.getInventory().getItemByItemId(itemId) != null)) {
            // Destroy
            player.getInventory().destroyItemByItemId(ItemProcessType.NONE, itemId, 1, player, null);
            player.sendItemList(true);
            player.broadcastUserInfo();
        }
        // is dropped on the ground
        else if (item != null) {
            item.decayMe();
            // World.removeObject(item); // redundant - decayMe() already calls World.removeObject
            log.info("{} item has been removed from World.", name);
        }

        // Delete infos from table if any.
        CursedWeaponsManager.getInstance().removeFromDb(itemId);

        final SystemMessage sm = new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED);
        sm.addItemName(itemId);
        CursedWeaponsManager.announce(sm);

        // Reset state
        cancelTask();
        activated = false;
        dropped = false;
        endTime = 0;
        player = null;
        playerId = 0;
        playerKarma = 0;
        playerPkKills = 0;
        item = null;
        nbKills = 0;
    }

    private void cancelTask() {
        if (removeTask != null) {
            removeTask.cancel(true);
            removeTask = null;
        }
    }

    private class RemoveTask implements Runnable {
        protected RemoveTask() {}

        @Override
        public void run() {
            if (ClockBridge.wallMillis() >= endTime) {
                endOfLife();
            }
        }
    }

    private void dropIt(Attackable attackable, Player player) {
        dropIt(attackable, player, null, true);
    }

    private void dropIt(Attackable attackable, Player player, Creature killer, boolean fromMonster) {
        activated = false;
        if (fromMonster) {
            item = attackable.dropItem(player, itemId, 1);
            item.setDropTime(0); // Prevent item from being removed by ItemsAutoDestroy.

            // RedSky and Earthquake
            final ExRedSky rs = new ExRedSky(10);
            final Earthquake eq = new Earthquake(player.getX(), player.getY(), player.getZ(), 14, 3);
            World.broadcastToAllOnlinePlayers(rs);
            World.broadcastToAllOnlinePlayers(eq);
        } else {
            item = this.player.getInventory().getItemByItemId(itemId);
            this.player.dropItem(ItemProcessType.DEATH, item, killer, true);
            this.player.setKarma(playerKarma);
            this.player.setPkKills(playerPkKills);
            this.player.setCursedWeaponEquippedId(0);
            removeSkill();
            this.player.abortAttack();

            // Item item = _player.getInventory().getItemByItemId(_itemId);
            // _player.getInventory().dropItem("DieDrop", item, _player, null);
            // player.getInventory().getItemByItemId(itemId).dropMe(player, player.getX(), player.getY(),
            // _player.getZ());
        }

        dropped = true;
        final SystemMessage sm = new SystemMessage(SystemMessageId.S2_WAS_DROPPED_IN_THE_S1_REGION);
        if (player != null) {
            sm.addZoneName(player.getX(), player.getY(), player.getZ()); // Region Name
        } else if (this.player != null) {
            sm.addZoneName(this.player.getX(), this.player.getY(), this.player.getZ()); // Region Name
        } else {
            sm.addZoneName(killer.getX(), killer.getY(), killer.getZ()); // Region Name
        }

        sm.addItemName(itemId);
        CursedWeaponsManager.announce(sm); // in the Hot Spring region
    }

    public void cursedOnLogin() {
        doTransform();
        giveSkill();

        final SystemMessage msg = new SystemMessage(SystemMessageId.S2_S_OWNER_HAS_LOGGED_INTO_THE_S1_REGION);
        msg.addZoneName(player.getX(), player.getY(), player.getZ());
        msg.addItemName(player.getCursedWeaponEquippedId());
        CursedWeaponsManager.announce(msg);

        final CursedWeapon cw = CursedWeaponsManager.getInstance().getCursedWeapon(player.getCursedWeaponEquippedId());
        final SystemMessage msg2 = new SystemMessage(SystemMessageId.S1_HAS_S2_MINUTE_S_OF_USAGE_TIME_REMAINING);
        final int timeLeft = (int) (cw.getTimeLeft() / 60000);
        msg2.addItemName(player.getCursedWeaponEquippedId());
        msg2.addInt(timeLeft);
        player.sendPacket(msg2);
    }

    /**
     * Yesod:<br>
     * Rebind the passive skill belonging to the CursedWeapon. Invoke this method if the weapon owner switches to a subclass.
     */
    public void giveSkill() {
        int level = 1 + (nbKills / stageKills);
        if (level > skillMaxLevel) {
            level = skillMaxLevel;
        }

        final Skill skill = SkillData.getInstance().getSkill(skillId, level);
        player.addSkill(skill, false);

        // Void Burst, Void Flow.
        player.addSkill(CommonSkill.VOID_BURST.getSkill(), false);
        player.addTransformSkill(CommonSkill.VOID_BURST.getSkill());
        player.addSkill(CommonSkill.VOID_FLOW.getSkill(), false);
        player.addTransformSkill(CommonSkill.VOID_FLOW.getSkill());
        player.sendSkillList();
    }

    public void doTransform() {
        if (itemId == 8689) {
            transformationId = 302;
        } else if (itemId == 8190) {
            transformationId = 301;
        }

        if (player.isTransformed() || player.isInStance()) {
            player.stopTransformation(true);

            ThreadPool.schedule(() -> TransformData.getInstance().transformPlayer(transformationId, player), 500);
        } else {
            TransformData.getInstance().transformPlayer(transformationId, player);
        }
    }

    public void removeSkill() {
        player.removeSkill(skillId);
        player.removeSkill(CommonSkill.VOID_BURST.getSkill().getId());
        player.removeSkill(CommonSkill.VOID_FLOW.getSkill().getId());
        player.untransform();
        player.sendSkillList();
    }

    public void reActivate() {
        activated = true;
        if ((endTime - ClockBridge.wallMillis()) <= 0) {
            endOfLife();
        } else {
            removeTask = ThreadPool.scheduleAtFixedRate(new RemoveTask(), durationLost * 12000, durationLost * 12000);
        }
    }

    public boolean checkDrop(Attackable attackable, Player player) {
        if (ClockBridge.random().nextInt(100000) < dropRate) {
            // Drop the item
            dropIt(attackable, player);

            // Start the Life Task.
            endTime = ClockBridge.wallMillis() + (duration * 60000);
            removeTask = ThreadPool.scheduleAtFixedRate(new RemoveTask(), durationLost * 12000, durationLost * 12000);
            return true;
        }

        return false;
    }

    public void activate(Player player, Item item) {
        // If the player is mounted, attempt to unmount first.
        // Only allow picking up the cursed weapon if unmounting is successful.
        if (player.isMounted() && !player.dismount()) {
            // TODO: Verify the following system message, may still be custom.
            player.sendPacket(SystemMessageId.YOU_HAVE_FAILED_TO_PICK_UP_S1);
            player.dropItem(ItemProcessType.DROP, item, null, true);
            return;
        }

        activated = true;

        // Player holding it data.
        this.player = player;
        playerId = this.player.getObjectId();
        playerKarma = this.player.getKarma();
        playerPkKills = this.player.getPkKills();
        saveData();

        // Change player stats
        this.player.setCursedWeaponEquippedId(itemId);
        this.player.setKarma(9999999);
        this.player.setPkKills(0);
        if (this.player.isInParty()) {
            this.player.getParty().removePartyMember(this.player, PartyMessageType.EXPELLED);
        }

        // Disable All Skills
        // Do Transform
        doTransform();

        // Add skill
        giveSkill();

        // Equip with the weapon.
        this.item = item;
        this.player.getInventory().equipItem(this.item);
        SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_EQUIPPED_YOUR_S1);
        sm.addItemName(this.item);
        this.player.sendPacket(sm);

        // Fully heal player
        this.player.setCurrentHpMp(this.player.getMaxHp(), this.player.getMaxMp());
        this.player.setCurrentCp(this.player.getMaxCp());

        // Refresh inventory
        this.player.sendItemList(false);

        // Refresh player stats
        this.player.broadcastUserInfo();

        ThreadPool.schedule(() -> this.player.broadcastPacket(new SocialAction(this.player.getObjectId(), 17)), 300);

        sm = new SystemMessage(SystemMessageId.THE_OWNER_OF_S2_HAS_APPEARED_IN_THE_S1_REGION);
        sm.addZoneName(this.player.getX(), this.player.getY(), this.player.getZ()); // Region Name
        sm.addItemName(this.item);
        CursedWeaponsManager.announce(sm);
    }

    public void saveData() {
        CursedWeaponsManager.getInstance().saveData(this);
    }

    public void dropIt(Creature killer) {
        if (ClockBridge.random().nextInt(100) <= disapearChance) {
            // Remove it
            endOfLife();
        } else {
            // Unequip & Drop
            dropIt(null, null, killer, false);

            // Reset player stats
            player.setKarma(playerKarma);
            player.setPkKills(playerPkKills);
            player.setCursedWeaponEquippedId(0);
            removeSkill();

            player.abortAttack();

            player.broadcastUserInfo();
        }
    }

    public void increaseKills() {
        nbKills++;

        if ((player != null) && player.isOnline()) {
            player.setPkKills(nbKills);
            player.updateUserInfo();
            if (((nbKills % stageKills) == 0) && (nbKills <= (stageKills * (skillMaxLevel - 1)))) {
                giveSkill();
            }
        }

        // Reduce time-to-live
        endTime -= durationLost * 60000;
        saveData();
    }

    public boolean isActive() {
        return activated || dropped;
    }

    public int getLevel() {
        if (nbKills > (stageKills * skillMaxLevel)) {
            return skillMaxLevel;
        }

        return (nbKills / stageKills);
    }

    public long getTimeLeft() {
        return endTime - ClockBridge.wallMillis();
    }

    public void goTo(Player player) {
        if (player == null) {
            return;
        }

        if (activated && (this.player != null)) {
            // Go to player holding the weapon.
            player.teleToLocation(this.player.getLocation(), true);
        } else if (dropped && (item != null)) {
            // Go to item on the ground.
            player.teleToLocation(item.getLocation(), true);
        } else {
            player.sendMessage(name + " isn't in the World.");
        }
    }

    public Location getWorldPosition() {
        if (activated && (player != null)) {
            return player.getLocation();
        }

        if (dropped && (item != null)) {
            return item.getLocation();
        }

        return null;
    }

    public long getDuration() {
        return duration;
    }
}
