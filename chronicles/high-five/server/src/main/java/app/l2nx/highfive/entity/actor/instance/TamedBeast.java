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
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.protocol.p267.serverpackets.AbstractNpcInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SocialAction;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StopMove;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@SingleWriter
public class TamedBeast extends FeedableBeast {
    private int foodType;

    private static final int MAX_DISTANCE_FROM_HOME = 30000;
    private static final int MAX_DISTANCE_FROM_OWNER = 2000;
    private static final int MAX_DURATION = 1200000;
    private static final int DURATION_CHECK_INTERVAL = 60000;
    private static final int DURATION_INCREASE_INTERVAL = 20000;
    private static final int BUFF_INTERVAL = 5000;

    private int remainingTime = MAX_DURATION;

    private int homeX;
    private int homeY;
    private int homeZ;

    protected Player owner;

    private ScheduledFuture<?> buffTask = null;
    private ScheduledFuture<?> durationCheckTask = null;

    protected boolean freyaBeast;

    private Collection<Skill> beastSkills = null;

    public TamedBeast(int npcTemplateId) {
        super(NpcData.getInstance().getTemplate(npcTemplateId));
        setInstanceType(InstanceType.TamedBeast);
        setHome(this);
    }

    public TamedBeast(int npcTemplateId, Player owner, int foodSkillId, int x, int y, int z) {
        super(NpcData.getInstance().getTemplate(npcTemplateId));
        freyaBeast = false;
        setInstanceType(InstanceType.TamedBeast);
        fullRestore();
        setOwner(owner);
        setFoodType(foodSkillId);
        setHome(x, y, z);
        spawnMe(x, y, z);
    }

    public TamedBeast(int npcTemplateId, Player owner, int food, int x, int y, int z, boolean isFreyaBeast) {
        super(NpcData.getInstance().getTemplate(npcTemplateId));
        freyaBeast = isFreyaBeast;
        setInstanceType(InstanceType.TamedBeast);
        fullRestore();
        setFoodType(food);
        setHome(x, y, z);
        spawnMe(x, y, z);
        setOwner(owner);
        if (isFreyaBeast) {
            getAI().setIntentionFollow(this.owner);
        }
    }

    public int getFoodType() {
        WorldPartition.checkOwner();
        return foodType;
    }

    public int getRemainingTime() {
        WorldPartition.checkOwner();
        return remainingTime;
    }

    public void setRemainingTime(int remainingTime) {
        WorldPartition.checkOwner();
        this.remainingTime = remainingTime;
    }

    public Player getOwner() {
        WorldPartition.checkOwner();
        return owner;
    }

    public boolean isFreyaBeast() {
        WorldPartition.checkOwner();
        return freyaBeast;
    }

    public void onReceiveFood() {
        WorldPartition.checkOwner();
        remainingTime += DURATION_INCREASE_INTERVAL;
        if (remainingTime > MAX_DURATION) {
            remainingTime = MAX_DURATION;
        }
    }

    public Location getHome() {
        WorldPartition.checkOwner();
        return new Location(homeX, homeY, homeZ);
    }

    public void setHome(int x, int y, int z) {
        WorldPartition.checkOwner();
        homeX = x;
        homeY = y;
        homeZ = z;
    }

    public void setHome(Creature c) {
        WorldPartition.checkOwner();
        setHome(c.getX(), c.getY(), c.getZ());
    }

    public void setFoodType(int foodItemId) {
        WorldPartition.checkOwner();
        if (foodItemId > 0) {
            foodType = foodItemId;

            if (durationCheckTask != null) {
                durationCheckTask.cancel(true);
            }

            durationCheckTask = WorldPartition.loop()
                    .scheduleAtFixedRate(
                            new CheckDuration(this),
                            DURATION_CHECK_INTERVAL,
                            DURATION_CHECK_INTERVAL,
                            TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public boolean doDie(Creature killer) {
        WorldPartition.checkOwner();
        if (!super.doDie(killer)) {
            return false;
        }

        getAI().stopFollow();
        if (buffTask != null) {
            buffTask.cancel(true);
        }

        if (durationCheckTask != null) {
            durationCheckTask.cancel(true);
        }

        if (owner != null) {
            owner.removeTrainedBeast(this);
        }

        buffTask = null;
        durationCheckTask = null;
        owner = null;
        foodType = 0;
        remainingTime = 0;
        return true;
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        return !freyaBeast;
    }

    public void addBeastSkill(Skill skill) {
        WorldPartition.checkOwner();
        if (beastSkills == null) {
            // Skill has identity equality: insertion order keeps the cast sequence the same on every run.
            beastSkills = new LinkedHashSet<>();
        }

        beastSkills.add(skill);
    }

    public void castBeastSkills() {
        WorldPartition.checkOwner();
        if ((owner == null) || (beastSkills == null)) {
            return;
        }

        int delay = 100;
        for (Skill skill : beastSkills) {
            WorldPartition.loop().schedule(new buffCast(skill), delay, TimeUnit.MILLISECONDS);
            delay += (100 + skill.getHitTime());
        }

        WorldPartition.loop().schedule(new buffCast(null), delay, TimeUnit.MILLISECONDS);
    }

    private class buffCast implements Runnable {
        private final Skill skill;

        public buffCast(Skill skill) {
            this.skill = skill;
        }

        @Override
        public void run() {
            if (skill == null) {
                getAI().setIntentionFollow(owner);
            } else {
                sitCastAndFollow(skill, owner);
            }
        }
    }

    public void setOwner(Player owner) {
        WorldPartition.checkOwner();
        if (owner != null) {
            this.owner = owner;
            setTitle(owner.getName());

            setShowSummonAnimation(true);
            broadcastPacket(new AbstractNpcInfo.NpcInfo(this, owner));
            owner.addTrainedBeast(this);

            getAI().startFollow(this.owner, 100);
            if (!freyaBeast) {
                int totalBuffsAvailable = 0;
                for (Skill skill : getTemplate().getSkills().values()) {
                    if (skill.isContinuous() && !skill.isDebuff()) {
                        totalBuffsAvailable++;
                    }
                }

                if (buffTask != null) {
                    buffTask.cancel(true);
                }

                buffTask = WorldPartition.loop()
                        .scheduleAtFixedRate(
                                new CheckOwnerBuffs(this, totalBuffsAvailable),
                                BUFF_INTERVAL,
                                BUFF_INTERVAL,
                                TimeUnit.MILLISECONDS);
            }
        } else {
            deleteMe();
        }
    }

    public boolean isTooFarFromHome() {
        WorldPartition.checkOwner();
        return !isInsideRadius3D(homeX, homeY, homeZ, MAX_DISTANCE_FROM_HOME);
    }

    @Override
    public boolean deleteMe() {
        WorldPartition.checkOwner();
        if (buffTask != null) {
            buffTask.cancel(true);
        }

        durationCheckTask.cancel(true);
        stopHpMpRegeneration();

        if (owner != null) {
            owner.removeTrainedBeast(this);
        }

        setTarget(null);
        buffTask = null;
        durationCheckTask = null;
        owner = null;
        foodType = 0;
        remainingTime = 0;

        return super.deleteMe();
    }

    public void onOwnerGotAttacked(Creature attacker) {
        WorldPartition.checkOwner();
        if ((owner == null) || !owner.isOnline()) {
            deleteMe();
            return;
        }

        if (!owner.isInsideRadius3D(this, MAX_DISTANCE_FROM_OWNER)) {
            getAI().startFollow(owner);
            return;
        }

        if (owner.isDead() || freyaBeast) {
            return;
        }

        if (isCastingNow()) {
            return;
        }

        final float hPRatio = ((float) owner.getCurrentHp()) / owner.getMaxHp();

        if (hPRatio >= 0.8) {
            for (Skill skill : getTemplate().getSkills().values()) {
                if (skill.isDebuff()
                        && (ClockBridge.random().nextInt(3) < 1)
                        && ((attacker != null) && attacker.isAffectedBySkill(skill.getId()))) {
                    sitCastAndFollow(skill, attacker);
                }
            }
        }

        // Between 80% and 50% HP it deliberately does nothing, so MP can regenerate.
        else if (hPRatio < 0.5) {
            int chance = 1;
            if (hPRatio < 0.25) {
                chance = 2;
            }

            for (Skill skill : getTemplate().getSkills().values()) {
                if ((ClockBridge.random().nextInt(5) < chance)
                        && skill.hasEffectType(
                                EffectType.CPHEAL,
                                EffectType.HEAL,
                                EffectType.MANAHEAL_BY_LEVEL,
                                EffectType.MANAHEAL_PERCENT)) {
                    sitCastAndFollow(skill, owner);
                }
            }
        }
    }

    protected void sitCastAndFollow(Skill skill, Creature target) {
        stopMove(null);
        broadcastPacket(new StopMove(this));
        getAI().setIntentionIdle();

        setTarget(target);
        doCast(skill);
        getAI().setIntentionFollow(owner);
    }

    private static class CheckDuration implements Runnable {
        private final TamedBeast tamedBeast;

        CheckDuration(TamedBeast tamedBeast) {
            this.tamedBeast = tamedBeast;
        }

        @Override
        public void run() {
            final int foodTypeSkillId = tamedBeast.getFoodType();
            final Player owner = tamedBeast.getOwner();
            Item item = null;
            if (tamedBeast.freyaBeast) {
                item = owner.getInventory().getItemByItemId(foodTypeSkillId);
                if ((item != null) && owner.destroyItem(ItemProcessType.DESTROY, item, 1, tamedBeast, true)) {
                    tamedBeast.broadcastPacket(new SocialAction(tamedBeast.getObjectId(), 3));
                } else {
                    tamedBeast.deleteMe();
                }
            } else {
                tamedBeast.setRemainingTime(tamedBeast.getRemainingTime() - DURATION_CHECK_INTERVAL);

                if (foodTypeSkillId == 2188) {
                    item = owner.getInventory().getItemByItemId(6643);
                } else if (foodTypeSkillId == 2189) {
                    item = owner.getInventory().getItemByItemId(6644);
                }

                if ((item != null) && (item.getCount() >= 1)) {
                    final WorldObject oldTarget = owner.getTarget();
                    owner.setTarget(tamedBeast);

                    owner.callSkill(
                            SkillData.getInstance().getSkill(foodTypeSkillId, 1),
                            Collections.singletonList(tamedBeast));
                    owner.setTarget(oldTarget);
                } else {
                    // No food: despawn, unless newly spawned (those get a 5 minute grace period).
                    if (tamedBeast.getRemainingTime() < (MAX_DURATION - 300000)) {
                        tamedBeast.setRemainingTime(-1);
                    }
                }

                if (tamedBeast.getRemainingTime() <= 0) {
                    tamedBeast.deleteMe();
                }
            }
        }
    }

    private class CheckOwnerBuffs implements Runnable {
        private final TamedBeast tamedBeast;
        private final int numBuffs;

        CheckOwnerBuffs(TamedBeast tamedBeast, int numBuffs) {
            this.tamedBeast = tamedBeast;
            this.numBuffs = numBuffs;
        }

        @Override
        public void run() {
            final Player owner = tamedBeast.getOwner();

            if ((owner == null) || !owner.isOnline()) {
                deleteMe();
                return;
            }

            if (!isInsideRadius3D(owner, MAX_DISTANCE_FROM_OWNER)) {
                getAI().startFollow(owner);
                return;
            }

            if (owner.isDead()) {
                return;
            }

            if (isCastingNow()) {
                return;
            }

            int totalBuffsOnOwner = 0;
            int i = 0;
            final int rand = numBuffs > 0 ? ClockBridge.random().nextInt(numBuffs) : 0;
            Skill buffToGive = null;

            for (Skill skill : tamedBeast.getTemplate().getSkills().values()) {
                if (skill.isContinuous() && !skill.isDebuff()) {
                    if (i++ == rand) {
                        buffToGive = skill;
                    }

                    if (owner.isAffectedBySkill(skill.getId())) {
                        totalBuffsOnOwner++;
                    }
                }
            }

            // Casts when the owner has fewer than two thirds of the available buffs.
            if (((numBuffs * 2) / 3) > totalBuffsOnOwner) {
                tamedBeast.sitCastAndFollow(buffToGive, owner);
            }

            getAI().setIntentionFollow(tamedBeast.getOwner());
        }
    }

    @Override
    public void onAction(Player player, boolean interact) {
        WorldPartition.checkOwner();
        if ((player == null) || !canTarget(player)) {
            return;
        }

        if (this != player.getTarget()) {
            player.setTarget(this);
        } else if (interact) {
            if (isAutoAttackable(player) && (Math.abs(player.getZ() - getZ()) < 100)) {
                player.getAI().setIntentionAttack(this);
            } else {
                player.sendPacket(ActionFailed.STATIC_PACKET);
            }
        }
    }
}
