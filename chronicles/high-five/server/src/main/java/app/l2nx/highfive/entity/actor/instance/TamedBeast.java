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
import app.l2nx.highfive.network.serverpackets.AbstractNpcInfo;
import app.l2nx.highfive.network.serverpackets.ActionFailed;
import app.l2nx.highfive.network.serverpackets.SocialAction;
import app.l2nx.highfive.network.serverpackets.StopMove;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import lombok.Getter;
import lombok.Setter;

// While a tamed beast behaves a lot like a pet (ingame) and does have
// an owner, in all other aspects, it acts like a mob.
// In addition, it can be fed in order to increase its duration.
// This class handles the running tasks, AI, and feed of the mob.
// The (mostly optional) AI on feeding the spawn is handled by the datapack ai script.
public class TamedBeast extends FeedableBeast {
    @Getter
    private int foodType;

    private static final int MAX_DISTANCE_FROM_HOME = 30000;
    private static final int MAX_DISTANCE_FROM_OWNER = 2000;
    private static final int MAX_DURATION = 1200000; // 20 minutes
    private static final int DURATION_CHECK_INTERVAL = 60000; // 1 minute
    private static final int DURATION_INCREASE_INTERVAL = 20000; // 20 secs (gained upon feeding)
    private static final int BUFF_INTERVAL = 5000; // 5 seconds

    @Getter
    @Setter
    private int remainingTime = MAX_DURATION;

    private int homeX;
    private int homeY;
    private int homeZ;

    @Getter
    protected Player owner;

    private Future<?> buffTask = null;
    private Future<?> durationCheckTask = null;

    @Getter
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

    public void onReceiveFood() {
        // Eating food extends the duration by 20secs, to a max of 20minutes.
        remainingTime += DURATION_INCREASE_INTERVAL;
        if (remainingTime > MAX_DURATION) {
            remainingTime = MAX_DURATION;
        }
    }

    public Location getHome() {
        return new Location(homeX, homeY, homeZ);
    }

    public void setHome(int x, int y, int z) {
        homeX = x;
        homeY = y;
        homeZ = z;
    }

    public void setHome(Creature c) {
        setHome(c.getX(), c.getY(), c.getZ());
    }

    public void setFoodType(int foodItemId) {
        if (foodItemId > 0) {
            foodType = foodItemId;

            // Start the duration checks.
            // Start the buff tasks.
            if (durationCheckTask != null) {
                durationCheckTask.cancel(true);
            }

            durationCheckTask = ThreadPool.scheduleAtFixedRate(
                    new CheckDuration(this), DURATION_CHECK_INTERVAL, DURATION_CHECK_INTERVAL);
        }
    }

    @Override
    public boolean doDie(Creature killer) {
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

        // Clean up variables.
        if ((owner != null) && (owner.getTrainedBeasts() != null)) {
            owner.getTrainedBeasts().remove(this);
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
        return !freyaBeast;
    }

    public void addBeastSkill(Skill skill) {
        if (beastSkills == null) {
            beastSkills = ConcurrentHashMap.newKeySet();
        }

        beastSkills.add(skill);
    }

    public void castBeastSkills() {
        if ((owner == null) || (beastSkills == null)) {
            return;
        }

        int delay = 100;
        for (Skill skill : beastSkills) {
            ThreadPool.schedule(new buffCast(skill), delay);
            delay += (100 + skill.getHitTime());
        }

        ThreadPool.schedule(new buffCast(null), delay);
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
        if (owner != null) {
            this.owner = owner;
            setTitle(owner.getName());

            // Broadcast the new title.
            setShowSummonAnimation(true);
            broadcastPacket(new AbstractNpcInfo.NpcInfo(this, owner));
            owner.addTrainedBeast(this);

            // Always and automatically follow the owner.
            getAI().startFollow(this.owner, 100);
            if (!freyaBeast) {
                // Instead of calculating this value each time, let's get this now and pass it on.
                int totalBuffsAvailable = 0;
                for (Skill skill : getTemplate().getSkills().values()) {
                    // If the skill is a buff, check if the owner has it already [ owner.getEffect(Skill skill) ].
                    if (skill.isContinuous() && !skill.isDebuff()) {
                        totalBuffsAvailable++;
                    }
                }

                // Start the buff tasks.
                if (buffTask != null) {
                    buffTask.cancel(true);
                }

                buffTask = ThreadPool.scheduleAtFixedRate(
                        new CheckOwnerBuffs(this, totalBuffsAvailable), BUFF_INTERVAL, BUFF_INTERVAL);
            }
        } else {
            deleteMe(); // despawn if no owner
        }
    }

    public boolean isTooFarFromHome() {
        return !isInsideRadius3D(homeX, homeY, homeZ, MAX_DISTANCE_FROM_HOME);
    }

    @Override
    public boolean deleteMe() {
        if (buffTask != null) {
            buffTask.cancel(true);
        }

        durationCheckTask.cancel(true);
        stopHpMpRegeneration();

        // Clean up variables.
        if ((owner != null) && (owner.getTrainedBeasts() != null)) {
            owner.getTrainedBeasts().remove(this);
        }

        setTarget(null);
        buffTask = null;
        durationCheckTask = null;
        owner = null;
        foodType = 0;
        remainingTime = 0;

        // Remove the spawn.
        return super.deleteMe();
    }

    // Notification triggered by the owner when the owner is attacked.
    // Tamed mobs will heal/recharge or debuff the enemy according to their skills.
    public void onOwnerGotAttacked(Creature attacker) {
        // Check if the owner is no longer around...if so, despawn.
        if ((owner == null) || !owner.isOnline()) {
            deleteMe();
            return;
        }

        // If the owner is too far away, stop anything else and immediately run towards the owner.
        if (!owner.isInsideRadius3D(this, MAX_DISTANCE_FROM_OWNER)) {
            getAI().startFollow(owner);
            return;
        }

        // If the owner is dead, do nothing...
        if (owner.isDead() || freyaBeast) {
            return;
        }

        // If the tamed beast is currently in the middle of casting, let it complete its skill...
        if (isCastingNow()) {
            return;
        }

        final float hPRatio = ((float) owner.getCurrentHp()) / owner.getMaxHp();

        // If the owner has a lot of HP, then debuff the enemy with a random debuff among the available skills.
        // Use of more than one debuff at this moment is acceptable.
        if (hPRatio >= 0.8) {
            for (Skill skill : getTemplate().getSkills().values()) {
                // if the skill is a debuff, check if the attacker has it already [ attacker.getEffect(Skill skill) ]
                if (skill.isDebuff()
                        && (ClockBridge.random().nextInt(3) < 1)
                        && ((attacker != null) && attacker.isAffectedBySkill(skill.getId()))) {
                    sitCastAndFollow(skill, attacker);
                }
            }
        }

        // for HP levels between 80% and 50%, do not react to attack events (so that MP can regenerate a bit)
        // for lower HP ranges, heal or recharge the owner with 1 skill use per attack.
        else if (hPRatio < 0.5) {
            int chance = 1;
            if (hPRatio < 0.25) {
                chance = 2;
            }

            // If the owner has a lot of HP, then debuff the enemy with a random debuff among the available skills.
            for (Skill skill : getTemplate().getSkills().values()) {
                // If the skill is a buff, check if the owner has it already [ owner.getEffect(Skill skill) ].
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

    /**
     * Prepare and cast a skill:<br>
     * First smoothly prepare the beast for casting, by abandoning other actions.<br>
     * Next, call super.doCast(skill) in order to actually cast the spell.<br>
     * Finally, return to auto-following the owner.
     * @param skill
     * @param target
     */
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
                if ((item != null) && (item.getCount() >= 1)) {
                    owner.destroyItem(ItemProcessType.DESTROY, item, 1, tamedBeast, true);
                    tamedBeast.broadcastPacket(new SocialAction(tamedBeast.getObjectId(), 3));
                } else {
                    tamedBeast.deleteMe();
                }
            } else {
                tamedBeast.setRemainingTime(tamedBeast.getRemainingTime() - DURATION_CHECK_INTERVAL);

                // I tried to avoid this as much as possible...but it seems I can't avoid hardcoding.
                // ids further, except by carrying an additional variable just for these two lines...
                // Find which food item needs to be consumed.
                if (foodTypeSkillId == 2188) {
                    item = owner.getInventory().getItemByItemId(6643);
                } else if (foodTypeSkillId == 2189) {
                    item = owner.getInventory().getItemByItemId(6644);
                }

                // If the owner has enough food, call the item handler (use the food and triffer all necessary actions).
                if ((item != null) && (item.getCount() >= 1)) {
                    final WorldObject oldTarget = owner.getTarget();
                    owner.setTarget(tamedBeast);

                    // Emulate a call to the owner using food, but bypass all checks for range, etc.
                    // This also causes a call to the AI tasks handling feeding, which may call onReceiveFood as
                    // required.
                    owner.callSkill(
                            SkillData.getInstance().getSkill(foodTypeSkillId, 1),
                            Collections.singletonList(tamedBeast));
                    owner.setTarget(oldTarget);
                } else {
                    // if the owner has no food, the beast immediately despawns, except when it was only
                    // newly spawned. Newly spawned beasts can last up to 5 minutes
                    if (tamedBeast.getRemainingTime() < (MAX_DURATION - 300000)) {
                        tamedBeast.setRemainingTime(-1);
                    }
                }

                // There are too many conflicting reports about whether distance from home should be taken into
                // consideration. Disabled for now.
                // if (_tamedBeast.isTooFarFromHome())
                // _tamedBeast.setRemainingTime(-1);
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

            // Check if the owner is no longer around...if so, despawn.
            if ((owner == null) || !owner.isOnline()) {
                deleteMe();
                return;
            }

            // If the owner is too far away, stop anything else and immediately run towards the owner.
            if (!isInsideRadius3D(owner, MAX_DISTANCE_FROM_OWNER)) {
                getAI().startFollow(owner);
                return;
            }

            // If the owner is dead, do nothing...
            if (owner.isDead()) {
                return;
            }

            // If the tamed beast is currently casting a spell, do not interfere (do not attempt to cast anything new
            // yet).
            if (isCastingNow()) {
                return;
            }

            int totalBuffsOnOwner = 0;
            int i = 0;
            final int rand = numBuffs > 0 ? ClockBridge.random().nextInt(numBuffs) : 0;
            Skill buffToGive = null;

            // Get this npc's skills: getSkills().
            for (Skill skill : tamedBeast.getTemplate().getSkills().values()) {
                // If the skill is a buff, check if the owner has it already [ owner.getEffect(Skill skill) ].
                if (skill.isContinuous() && !skill.isDebuff()) {
                    if (i++ == rand) {
                        buffToGive = skill;
                    }

                    if (owner.isAffectedBySkill(skill.getId())) {
                        totalBuffsOnOwner++;
                    }
                }
            }

            // If the owner has less than 60% of this beast's available buff, cast a random buff.
            if (((numBuffs * 2) / 3) > totalBuffsOnOwner) {
                tamedBeast.sitCastAndFollow(buffToGive, owner);
            }

            getAI().setIntentionFollow(tamedBeast.getOwner());
        }
    }

    @Override
    public void onAction(Player player, boolean interact) {
        if ((player == null) || !canTarget(player)) {
            return;
        }

        // Check if the Player already target the Npc.
        if (this != player.getTarget()) {
            // Set the target of the Player player.
            player.setTarget(this);
        } else if (interact) {
            if (isAutoAttackable(player) && (Math.abs(player.getZ() - getZ()) < 100)) {
                player.getAI().setIntentionAttack(this);
            } else {
                // Send a Server->Client ActionFailed to the Player in order to avoid that the client wait another
                // packet.
                player.sendPacket(ActionFailed.STATIC_PACKET);
            }
        }
    }
}
