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

import app.l2nx.engine.core.loop.SingleWriter;
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
import app.l2nx.highfive.network.protocol.p267.serverpackets.Earthquake;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExRedSky;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SocialAction;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.OfflineRows;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SingleWriter
public class CursedWeapon {

    private final String name;
    private final int itemId;
    private final int skillId;

    private final int skillMaxLevel;

    private int dropRate;

    private int duration;

    private int durationLost;

    private int disapearChance;

    private int stageKills;

    private boolean dropped = false;

    private boolean activated = false;

    private ScheduledFuture<?> removeTask;

    private int nbKills = 0;

    private long endTime = 0;

    private int playerId = 0;

    private Player player = null;

    private Item item = null;

    private int playerKarma = 0;

    private int playerPkKills = 0;

    private int transformationId = 0;

    public CursedWeapon(int itemId, int skillId, String name) {
        this.name = name;
        this.itemId = itemId;
        this.skillId = skillId;
        skillMaxLevel = SkillData.getInstance().getMaxLevel(this.skillId);
    }

    public String getName() {
        WorldPartition.checkOwner();
        return name;
    }

    public int getItemId() {
        WorldPartition.checkOwner();
        return itemId;
    }

    public int getSkillId() {
        WorldPartition.checkOwner();
        return skillId;
    }

    public void setDropRate(int dropRate) {
        WorldPartition.checkOwner();
        this.dropRate = dropRate;
    }

    public void setDuration(int duration) {
        WorldPartition.checkOwner();
        this.duration = duration;
    }

    public void setDurationLost(int durationLost) {
        WorldPartition.checkOwner();
        this.durationLost = durationLost;
    }

    public void setDisapearChance(int disapearChance) {
        WorldPartition.checkOwner();
        this.disapearChance = disapearChance;
    }

    public int getStageKills() {
        WorldPartition.checkOwner();
        return stageKills;
    }

    public void setStageKills(int stageKills) {
        WorldPartition.checkOwner();
        this.stageKills = stageKills;
    }

    public boolean isDropped() {
        WorldPartition.checkOwner();
        return dropped;
    }

    public void setDropped(boolean dropped) {
        WorldPartition.checkOwner();
        this.dropped = dropped;
    }

    public boolean isActivated() {
        WorldPartition.checkOwner();
        return activated;
    }

    public void setActivated(boolean activated) {
        WorldPartition.checkOwner();
        this.activated = activated;
    }

    public int getNbKills() {
        WorldPartition.checkOwner();
        return nbKills;
    }

    public void setNbKills(int nbKills) {
        WorldPartition.checkOwner();
        this.nbKills = nbKills;
    }

    public long getEndTime() {
        WorldPartition.checkOwner();
        return endTime;
    }

    public void setEndTime(long endTime) {
        WorldPartition.checkOwner();
        this.endTime = endTime;
    }

    public int getPlayerId() {
        WorldPartition.checkOwner();
        return playerId;
    }

    public void setPlayerId(int playerId) {
        WorldPartition.checkOwner();
        this.playerId = playerId;
    }

    public Player getPlayer() {
        WorldPartition.checkOwner();
        return player;
    }

    public void setPlayer(Player player) {
        WorldPartition.checkOwner();
        this.player = player;
    }

    public void setItem(Item item) {
        WorldPartition.checkOwner();
        this.item = item;
    }

    public int getPlayerKarma() {
        WorldPartition.checkOwner();
        return playerKarma;
    }

    public void setPlayerKarma(int playerKarma) {
        WorldPartition.checkOwner();
        this.playerKarma = playerKarma;
    }

    public int getPlayerPkKills() {
        WorldPartition.checkOwner();
        return playerPkKills;
    }

    public void setPlayerPkKills(int playerPkKills) {
        WorldPartition.checkOwner();
        this.playerPkKills = playerPkKills;
    }

    public void endOfLife() {
        WorldPartition.checkOwner();
        if (activated && OfflineRows.isBusy(playerId)) {
            OfflineRows.whenSettled(playerId, () -> {
                if (activated) {
                    endOfLife();
                }
            });
            return;
        }
        if (activated) {
            if ((player != null) && player.isOnline()) {
                log.info("{} being removed online.", name);
                player.abortAttack();

                player.setKarma(playerKarma);
                player.setPkKills(playerPkKills);
                player.setCursedWeaponEquippedId(0);
                removeSkill();

                player.getInventory().unEquipItemInBodySlot(BodyPart.LR_HAND);
                player.storeMe();

                player.getInventory().destroyItemByItemId(ItemProcessType.NONE, itemId, 1, player, null);
                player.sendItemList(true);
                player.broadcastUserInfo();
            } else {
                log.info("{} being removed offline.", name);

                CursedWeaponsManager.getInstance().removeFromOfflinePlayer(this, playerId);
            }
        }

        // either this cursed weapon is in the inventory of someone who has another cursed weapon equipped,
        // OR this cursed weapon is on the ground.
        else if ((player != null) && (player.getInventory().getItemByItemId(itemId) != null)) {
            player.getInventory().destroyItemByItemId(ItemProcessType.NONE, itemId, 1, player, null);
            player.sendItemList(true);
            player.broadcastUserInfo();
        } else if (item != null) {
            item.decayMe();
            log.info("{} item has been removed from World.", name);
        }

        CursedWeaponsManager.getInstance().removeFromDb(itemId);

        final SystemMessage sm = new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED);
        sm.addItemName(itemId);
        CursedWeaponsManager.announce(sm);

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

    @SingleWriter
    private class RemoveTask implements Runnable {
        protected RemoveTask() {}

        @Override
        public void run() {
            WorldPartition.checkOwner();
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
        }

        dropped = true;
        final SystemMessage sm = new SystemMessage(SystemMessageId.S2_WAS_DROPPED_IN_THE_S1_REGION);
        if (player != null) {
            sm.addZoneName(player.getX(), player.getY(), player.getZ());
        } else if (this.player != null) {
            sm.addZoneName(this.player.getX(), this.player.getY(), this.player.getZ());
        } else {
            sm.addZoneName(killer.getX(), killer.getY(), killer.getZ());
        }

        sm.addItemName(itemId);
        CursedWeaponsManager.announce(sm);
    }

    public void cursedOnLogin() {
        WorldPartition.checkOwner();
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

    /** Rebinds the passive skill; call it when the owner switches subclass. */
    public void giveSkill() {
        WorldPartition.checkOwner();
        int level = 1 + (nbKills / stageKills);
        if (level > skillMaxLevel) {
            level = skillMaxLevel;
        }

        final Skill skill = SkillData.getInstance().getSkill(skillId, level);
        player.addSkill(skill, false);

        player.addSkill(CommonSkill.VOID_BURST.getSkill(), false);
        player.addTransformSkill(CommonSkill.VOID_BURST.getSkill());
        player.addSkill(CommonSkill.VOID_FLOW.getSkill(), false);
        player.addTransformSkill(CommonSkill.VOID_FLOW.getSkill());
        player.sendSkillList();
    }

    public void doTransform() {
        WorldPartition.checkOwner();
        if (itemId == 8689) {
            transformationId = 302;
        } else if (itemId == 8190) {
            transformationId = 301;
        }

        if (player.isTransformed() || player.isInStance()) {
            player.stopTransformation(true);

            WorldPartition.loop()
                    .schedule(
                            () -> TransformData.getInstance().transformPlayer(transformationId, player),
                            500,
                            TimeUnit.MILLISECONDS);
        } else {
            TransformData.getInstance().transformPlayer(transformationId, player);
        }
    }

    public void removeSkill() {
        WorldPartition.checkOwner();
        player.removeSkill(skillId);
        player.removeSkill(CommonSkill.VOID_BURST.getSkill().getId());
        player.removeSkill(CommonSkill.VOID_FLOW.getSkill().getId());
        player.untransform();
        player.sendSkillList();
    }

    public void reActivate() {
        WorldPartition.checkOwner();
        activated = true;
        if ((endTime - ClockBridge.wallMillis()) <= 0) {
            endOfLife();
        } else {
            removeTask = WorldPartition.loop()
                    .scheduleAtFixedRate(
                            new RemoveTask(), durationLost * 12000, durationLost * 12000, TimeUnit.MILLISECONDS);
        }
    }

    public boolean checkDrop(Attackable attackable, Player player) {
        WorldPartition.checkOwner();
        if (ClockBridge.random().nextInt(100000) < dropRate) {
            dropIt(attackable, player);

            endTime = ClockBridge.wallMillis() + (duration * 60000);
            removeTask = WorldPartition.loop()
                    .scheduleAtFixedRate(
                            new RemoveTask(), durationLost * 12000, durationLost * 12000, TimeUnit.MILLISECONDS);
            return true;
        }

        return false;
    }

    public void activate(Player player, Item item) {
        WorldPartition.checkOwner();
        // Pickup fails unless the player can dismount first.
        if (player.isMounted() && !player.dismount()) {
            // TODO: Verify the following system message, may still be custom.
            player.sendPacket(SystemMessageId.YOU_HAVE_FAILED_TO_PICK_UP_S1);
            player.dropItem(ItemProcessType.DROP, item, null, true);
            return;
        }

        activated = true;

        this.player = player;
        playerId = this.player.getObjectId();
        playerKarma = this.player.getKarma();
        playerPkKills = this.player.getPkKills();
        saveData();

        this.player.setCursedWeaponEquippedId(itemId);
        this.player.setKarma(9999999);
        this.player.setPkKills(0);
        if (this.player.isInParty()) {
            this.player.getParty().removePartyMember(this.player, PartyMessageType.EXPELLED);
        }

        doTransform();

        giveSkill();

        this.item = item;
        this.player.getInventory().equipItem(this.item);
        SystemMessage sm = new SystemMessage(SystemMessageId.YOU_HAVE_EQUIPPED_YOUR_S1);
        sm.addItemName(this.item);
        this.player.sendPacket(sm);

        this.player.setCurrentHpMp(this.player.getMaxHp(), this.player.getMaxMp());
        this.player.setCurrentCp(this.player.getMaxCp());

        this.player.sendItemList(false);

        this.player.broadcastUserInfo();

        WorldPartition.loop()
                .schedule(
                        () -> this.player.broadcastPacket(new SocialAction(this.player.getObjectId(), 17)),
                        300,
                        TimeUnit.MILLISECONDS);

        sm = new SystemMessage(SystemMessageId.THE_OWNER_OF_S2_HAS_APPEARED_IN_THE_S1_REGION);
        sm.addZoneName(this.player.getX(), this.player.getY(), this.player.getZ());
        sm.addItemName(this.item);
        CursedWeaponsManager.announce(sm);
    }

    public void saveData() {
        WorldPartition.checkOwner();
        CursedWeaponsManager.getInstance().saveData(this);
    }

    public void dropIt(Creature killer) {
        WorldPartition.checkOwner();
        if (ClockBridge.random().nextInt(100) <= disapearChance) {
            endOfLife();
        } else {
            dropIt(null, null, killer, false);

            player.setKarma(playerKarma);
            player.setPkKills(playerPkKills);
            player.setCursedWeaponEquippedId(0);
            removeSkill();

            player.abortAttack();

            player.broadcastUserInfo();
        }
    }

    public void increaseKills() {
        WorldPartition.checkOwner();
        nbKills++;

        if ((player != null) && player.isOnline()) {
            player.setPkKills(nbKills);
            player.updateUserInfo();
            if (((nbKills % stageKills) == 0) && (nbKills <= (stageKills * (skillMaxLevel - 1)))) {
                giveSkill();
            }
        }

        endTime -= durationLost * 60000;
        saveData();
    }

    public boolean isActive() {
        WorldPartition.checkOwner();
        return activated || dropped;
    }

    public int getLevel() {
        WorldPartition.checkOwner();
        if (nbKills > (stageKills * skillMaxLevel)) {
            return skillMaxLevel;
        }

        return (nbKills / stageKills);
    }

    public long getTimeLeft() {
        WorldPartition.checkOwner();
        return endTime - ClockBridge.wallMillis();
    }

    public void goTo(Player player) {
        WorldPartition.checkOwner();
        if (player == null) {
            return;
        }

        if (activated && (this.player != null)) {
            player.teleToLocation(this.player.getLocation(), true);
        } else if (dropped && (item != null)) {
            player.teleToLocation(item.getLocation(), true);
        } else {
            player.sendMessage(name + " isn't in the World.");
        }
    }

    public Location getWorldPosition() {
        WorldPartition.checkOwner();
        if (activated && (player != null)) {
            return player.getLocation();
        }

        if (dropped && (item != null)) {
            return item.getLocation();
        }

        return null;
    }

    public long getDuration() {
        WorldPartition.checkOwner();
        return duration;
    }
}
