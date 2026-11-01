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
package app.l2nx.highfive.entity.actor.stat;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.config.custom.ClassBalanceConfig;
import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.data.xml.PetDataTable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SocialAction;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StatusUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SingleWriter
public class PetStat extends SummonStat {
    public PetStat(Pet activeChar, CreatureAccess access) {
        super(activeChar, access);
    }

    public boolean addExp(int value) {
        WorldPartition.checkOwner();
        final Pet pet = pet();
        if (pet.isUncontrollable() || !super.addExp(value)) {
            return false;
        }

        pet.updateAndBroadcastStatus(1);

        // The PetInfo packet wipes the PartySpelled (list of active spells' icons). Re-add them.
        pet.updateEffectIcons(true);

        return true;
    }

    public boolean addExpAndSp(double addToExp, double addToSp) {
        WorldPartition.checkOwner();
        final long finalExp = Math.round(addToExp);
        final Pet pet = pet();
        if (pet.isUncontrollable() || !addExp(finalExp)) {
            return false;
        }

        final SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_PET_GAINED_S1_EXPERIENCE_POINTS);
        sm.addLong(finalExp);
        pet.updateAndBroadcastStatus(1);
        pet.sendPacket(sm);
        return true;
    }

    @Override
    public boolean addLevel(byte value) {
        WorldPartition.checkOwner();
        if ((getLevel() + value) > (getMaxLevel() - 1)) {
            return false;
        }

        final boolean levelIncreased = super.addLevel(value);

        final Pet pet = pet();
        final StatusUpdate su = new StatusUpdate(pet);
        su.addAttribute(StatusUpdate.LEVEL, getLevel());
        su.addAttribute(StatusUpdate.MAX_HP, getMaxHp());
        su.addAttribute(StatusUpdate.MAX_MP, getMaxMp());
        pet.broadcastPacket(su);
        if (levelIncreased) {
            pet.broadcastPacket(new SocialAction(pet.getObjectId(), SocialAction.LEVEL_UP));
        }

        pet.updateAndBroadcastStatus(1);

        if (pet.getControlItem() != null) {
            pet.getControlItem().setEnchantLevel(getLevel());
        }

        return levelIncreased;
    }

    @Override
    public long getExpForLevel(int level) {
        WorldPartition.checkOwner();
        final Pet pet = pet();
        try {
            return PetDataTable.getInstance()
                    .getPetLevelData(pet.getId(), level)
                    .getPetMaxExp();
        } catch (NullPointerException e) {
            if (pet != null) {
                log.warn(
                        "Pet objectId:{}, NpcId:{}, level:{} is missing data from pets_stats table!",
                        pet.getObjectId(),
                        pet.getId(),
                        level);
            }

            throw e;
        }
    }

    @Override
    public Pet getActiveChar() {
        WorldPartition.checkOwner();
        return pet();
    }

    public int getFeedBattle() {
        WorldPartition.checkOwner();
        return pet().getPetLevelData().getPetFeedBattle();
    }

    public int getFeedNormal() {
        WorldPartition.checkOwner();
        return pet().getPetLevelData().getPetFeedNormal();
    }

    @Override
    public void setLevel(byte value) {
        WorldPartition.checkOwner();
        final Pet pet = pet();
        pet.setPetData(
                PetDataTable.getInstance().getPetLevelData(pet.getTemplate().getId(), value));
        if (pet.getPetLevelData() == null) {
            throw new IllegalArgumentException(
                    "No pet data for npc: " + pet.getTemplate().getId() + " level: " + value);
        }

        pet.stopFeed();
        super.setLevel(value);

        pet.startFeed();

        if (pet.getControlItem() != null) {
            pet.getControlItem().setEnchantLevel(getLevel());
        }
    }

    public int getMaxFeed() {
        WorldPartition.checkOwner();
        return pet().getPetLevelData().getPetMaxFeed();
    }

    @Override
    protected int maxHp() {
        return (int) calc(Stat.MAX_HP, pet().getPetLevelData().getPetMaxHP(), null, null);
    }

    @Override
    protected int maxMp() {
        return (int) calc(Stat.MAX_MP, pet().getPetLevelData().getPetMaxMP(), null, null);
    }

    @Override
    public double getMAtk(Creature target, Skill skill) {
        WorldPartition.checkOwner();
        return calc(Stat.MAGIC_ATTACK, pet().getPetLevelData().getPetMAtk(), target, skill);
    }

    @Override
    public double getMDef(Creature target, Skill skill) {
        WorldPartition.checkOwner();
        return calc(Stat.MAGIC_DEFENCE, pet().getPetLevelData().getPetMDef(), target, skill);
    }

    @Override
    public double getPAtk(Creature target) {
        WorldPartition.checkOwner();
        return calc(Stat.POWER_ATTACK, pet().getPetLevelData().getPetPAtk(), target, null);
    }

    @Override
    public double getPDef(Creature target) {
        WorldPartition.checkOwner();
        final Pet pet = pet();
        if (target != null) {
            if (target.isPlayable()) {
                return ClassBalanceConfig.PVP_PHYSICAL_ATTACK_DEFENCE_MULTIPLIERS[
                                pet.asPlayer().getPlayerClass().getId()]
                        * calc(Stat.POWER_DEFENCE, pet.getPetLevelData().getPetPDef(), target, null);
            }

            return ClassBalanceConfig.PVE_PHYSICAL_ATTACK_DEFENCE_MULTIPLIERS[
                            pet.asPlayer().getPlayerClass().getId()]
                    * calc(Stat.POWER_DEFENCE, pet.getPetLevelData().getPetPDef(), target, null);
        }

        return calc(Stat.POWER_DEFENCE, pet.getPetLevelData().getPetPDef(), target, null);
    }

    @Override
    protected double pAtkSpd() {
        return pet().isHungry() ? super.pAtkSpd() / 2 : super.pAtkSpd();
    }

    @Override
    protected int mAtkSpd() {
        return pet().isHungry() ? super.mAtkSpd() / 2 : super.mAtkSpd();
    }

    @Override
    public int getMaxLevel() {
        WorldPartition.checkOwner();
        return ExperienceData.getInstance().getMaxPetLevel();
    }

    private Pet pet() {
        return (Pet) creature();
    }
}
