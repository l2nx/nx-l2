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

import app.l2nx.highfive.ai.Intention;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.enums.CategoryType;
import app.l2nx.highfive.data.holders.PetData.PetSkillLearn;
import app.l2nx.highfive.data.xml.PetDataTable;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import lombok.Getter;

public class BabyPet extends Pet {
    private static final int BUFF_CONTROL = 5771;
    private static final int AWAKENING = 5753;

    protected List<SkillHolder> buffs = null;
    protected SkillHolder majorHeal = null;
    protected SkillHolder minorHeal = null;
    protected SkillHolder recharge = null;

    private Future<?> castTask;

    @Getter
    protected boolean inSupportMode = true;

    /**
     * Creates a baby pet.
     * @param template the baby pet NPC template
     * @param owner the owner
     * @param control the summoning item
     */
    public BabyPet(NpcTemplate template, Player owner, Item control) {
        super(template, owner, control);
        setInstanceType(InstanceType.BabyPet);
    }

    /**
     * Creates a baby pet.
     * @param template the baby pet NPC template
     * @param owner the owner
     * @param control the summoning item
     * @param level the level
     */
    public BabyPet(NpcTemplate template, Player owner, Item control, byte level) {
        super(template, owner, control, level);
        setInstanceType(InstanceType.BabyPet);
    }

    @Override
    public void onSpawn() {
        super.onSpawn();

        double healPower = 0;
        for (PetSkillLearn psl : PetDataTable.getInstance().getPetData(getId()).getAvailableSkills()) {
            final int id = psl.getSkillId();
            final int level = PetDataTable.getInstance().getPetData(getId()).getAvailableLevel(id, getLevel());
            if (level == 0) {
                continue;
            }

            final Skill skill = SkillData.getInstance().getSkill(id, level);
            if (skill == null) {
                continue;
            }

            if ((skill.getId() == BUFF_CONTROL) || (skill.getId() == AWAKENING)) {
                continue;
            }

            if (skill.hasEffectType(EffectType.MANAHEAL_BY_LEVEL)) {
                recharge = new SkillHolder(skill);
                continue;
            }

            if (skill.hasEffectType(EffectType.HEAL)) {
                if (healPower == 0) {
                    // Set both heal types to the same skill.
                    majorHeal = new SkillHolder(skill);
                    minorHeal = majorHeal;
                    healPower = skill.getPower();
                } else {
                    // another heal skill found - search for most powerful
                    if (skill.getPower() > healPower) {
                        majorHeal = new SkillHolder(skill);
                    } else {
                        minorHeal = new SkillHolder(skill);
                    }
                }
                continue;
            }

            if (skill.isContinuous() && !skill.isDebuff()) {
                if (buffs == null) {
                    buffs = new ArrayList<>();
                }

                buffs.add(new SkillHolder(skill));
            }
        }

        startCastTask();
    }

    @Override
    public boolean doDie(Creature killer) {
        if (!super.doDie(killer)) {
            return false;
        }

        stopCastTask();
        abortCast();
        return true;
    }

    @Override
    public synchronized void unSummon(Player owner) {
        stopCastTask();
        abortCast();
        super.unSummon(owner);
    }

    @Override
    public void doRevive() {
        super.doRevive();
        startCastTask();
    }

    @Override
    public void onDecay() {
        super.onDecay();

        if (buffs != null) {
            buffs.clear();
        }
    }

    private void startCastTask() {
        if ((majorHeal != null) || (buffs != null) || ((recharge != null) && (castTask == null) && !isDead())) {
            castTask = ThreadPool.scheduleAtFixedRate(new CastTask(this), 3000, 2000);
        }
    }

    @Override
    public void switchMode() {
        inSupportMode = !inSupportMode;
    }

    private void stopCastTask() {
        if (castTask != null) {
            castTask.cancel(false);
            castTask = null;
        }
    }

    protected void castSkill(Skill skill) {
        // casting automatically stops any other action (such as autofollow or a move-to).
        // We need to gather the necessary info to restore the previous state.
        final boolean previousFollowStatus = getFollowStatus();

        // Pet not following and owner outside cast range.
        if (!previousFollowStatus && !isInsideRadius3D(getOwner(), skill.getCastRange())) {
            return;
        }

        setTarget(getOwner());
        useMagic(skill, false, false);

        final SystemMessage msg = new SystemMessage(SystemMessageId.YOUR_PET_USES_S1);
        msg.addSkillName(skill);
        sendPacket(msg);

        // calling useMagic changes the follow status, if the babypet actually casts
        // (as opposed to failing due some factors, such as too low MP, etc).
        // if the status has actually been changed, revert it. Else, allow the pet to
        // continue whatever it was trying to do.
        // NOTE: This is important since the pet may have been told to attack a target.
        // reverting the follow status will abort this attack! While aborting the attack
        // in order to heal is natural, it is not acceptable to abort the attack on its own,
        // merely because the timer stroke and without taking any other action...
        if (previousFollowStatus != getFollowStatus()) {
            setFollowStatus(previousFollowStatus);
        }
    }

    private class CastTask implements Runnable {
        private final BabyPet baby;
        private final List<Skill> currentBuffs = new ArrayList<>();

        public CastTask(BabyPet baby) {
            this.baby = baby;
        }

        @Override
        public void run() {
            final Player owner = baby.getOwner();

            // If the owner doesn't meet the conditions avoid casting.
            if ((owner == null) || owner.isDead() || owner.isInvul()) {
                return;
            }

            // If the pet doesn't meet the conditions avoid casting.
            if (baby.isDead()
                    || baby.isCastingNow()
                    || baby.isBetrayed()
                    || baby.isMuted()
                    || baby.isOutOfControl()
                    || !inSupportMode
                    || (baby.getAI().getIntention() == Intention.CAST)) {
                return;
            }

            Skill skill = null;
            if (majorHeal != null) {
                // If the owner's HP is more than 80% for Baby Pets and 70% for Improved Baby pets, do nothing.
                // If the owner's HP is very low, under 15% for Baby pets and under 30% for Improved Baby Pets, have 75%
                // chances of using a strong heal.
                // Otherwise, have 25% chances for weak heal.
                final double hpPercent = owner.getCurrentHp() / owner.getMaxHp();
                final boolean isImprovedBaby = isInCategory(CategoryType.BABY_PET_GROUP);
                if ((isImprovedBaby && (hpPercent < 0.3)) || (!isImprovedBaby && (hpPercent < 0.15))) {
                    skill = majorHeal.getSkill();
                    if (!baby.isSkillDisabled(skill)
                            && (ClockBridge.random().nextInt(100) <= 75)
                            && (baby.getCurrentMp() >= skill.getMpConsume())) {
                        castSkill(skill);
                        return;
                    }
                } else if ((majorHeal.getSkill() != minorHeal.getSkill())
                        && ((isImprovedBaby && (hpPercent < 0.7)) || (!isImprovedBaby && (hpPercent < 0.8)))) {
                    // Cast minorHeal only if it's different than majorHeal, then pet has two heals available.
                    skill = minorHeal.getSkill();
                    if (!baby.isSkillDisabled(skill)
                            && (ClockBridge.random().nextInt(100) <= 25)
                            && (baby.getCurrentMp() >= skill.getMpConsume())) {
                        castSkill(skill);
                        return;
                    }
                }
            }

            // Buff Control is not active.
            if (!baby.isAffectedBySkill(BUFF_CONTROL)) {
                // searching for usable buffs
                if ((buffs != null) && !buffs.isEmpty()) {
                    for (SkillHolder buff : buffs) {
                        skill = buff.getSkill();
                        if (baby.isSkillDisabled(skill)) {
                            continue;
                        }

                        if (baby.getCurrentMp() < skill.getMpConsume()) {
                            continue;
                        }

                        // If owner already have the buff, continue.
                        final BuffInfo buffInfo =
                                owner.getEffectList().getBuffInfoByAbnormalType(skill.getAbnormalType());
                        if ((buffInfo != null)
                                && (skill.getAbnormalLevel()
                                        <= buffInfo.getSkill().getAbnormalLevel())) {
                            continue;
                        }

                        // If owner have the buff blocked, continue.
                        if ((owner.getEffectList().getBlockedAbnormalTypes() != null)
                                && owner.getEffectList()
                                        .getBlockedAbnormalTypes()
                                        .contains(skill.getAbnormalType())) {
                            continue;
                        }

                        currentBuffs.add(skill);
                    }
                }

                if (!currentBuffs.isEmpty()) {
                    skill = currentBuffs.get(ClockBridge.random().nextInt(currentBuffs.size()));
                    castSkill(skill);
                    currentBuffs.clear();
                    return;
                }
            }

            // buffs/heal not casted, trying recharge, if exist recharge casted only if owner in combat stance.
            if ((recharge != null)
                    && owner.isInCombat()
                    && ((owner.getCurrentMp() / owner.getMaxMp()) < 0.6)
                    && (ClockBridge.random().nextInt(100) <= 60)) {
                skill = recharge.getSkill();
                if (!baby.isSkillDisabled(skill) && (baby.getCurrentMp() >= skill.getMpConsume())) {
                    castSkill(skill);
                }
            }
        }
    }
}
