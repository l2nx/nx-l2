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
import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.config.custom.FakePlayersConfig;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.holders.npc.MinionList;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.mechanics.effects.EffectFlag;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.List;

/**
 * This class manages all Monsters.
 * <ul>
 * <li>Minion</li>
 * <li>RaidBoss</li>
 * <li>GrandBoss</li>
 * </ul>
 */
@SingleWriter
public class Monster extends Attackable {
    protected boolean enableMinions = true;

    private Monster leader = null;

    private MinionList minionList = null;

    public Monster(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.Monster);
        setAutoAttackable(true);
    }

    public Monster getLeader() {
        WorldPartition.checkOwner();
        return leader;
    }

    public void setLeader(Monster leader) {
        WorldPartition.checkOwner();
        this.leader = leader;
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        if (isFakePlayer()) {
            return FakePlayersConfig.FAKE_PLAYER_AUTO_ATTACKABLE
                    || isInCombat()
                    || attacker.isMonster()
                    || (getScriptValue() > 0);
        }

        if (NpcConfig.GUARD_ATTACK_AGGRO_MOB && isAggressive() && (attacker instanceof Guard)) {
            return true;
        }

        if (attacker.isMonster()) {
            return attacker.isFakePlayer();
        }

        if (!attacker.isPlayable()
                && !attacker.isAttackable()
                && !(attacker instanceof Trap)
                && !(attacker instanceof EffectPoint)) {
            return false;
        }

        return super.isAutoAttackable(attacker);
    }

    @Override
    public boolean isAggressive() {
        WorldPartition.checkOwner();
        return getTemplate().isAggressive() && !isAffected(EffectFlag.PASSIVE);
    }

    @Override
    public void onSpawn() {
        WorldPartition.checkOwner();
        if (!isTeleporting() && (leader != null)) {
            setRandomWalking(false);
            setIsRaidMinion(leader.isRaid());
            leader.getMinionList().onMinionSpawn(this);
        }

        // Dynamic script-based minions spawned here, after all preparations.
        super.onSpawn();
    }

    @Override
    public void onTeleported() {
        WorldPartition.checkOwner();
        super.onTeleported();

        if (hasMinions()) {
            getMinionList().onMasterTeleported();
        }
    }

    @Override
    public boolean deleteMe() {
        WorldPartition.checkOwner();
        if (hasMinions()) {
            getMinionList().onMasterDie(true);
        }

        if (leader != null) {
            leader.getMinionList().onMinionDie(this, 0);
        }

        return super.deleteMe();
    }

    public void enableMinions(boolean value) {
        WorldPartition.checkOwner();
        enableMinions = value;
    }

    public boolean hasMinions() {
        WorldPartition.checkOwner();
        return minionList != null;
    }

    public MinionList getMinionList() {
        WorldPartition.checkOwner();
        if (minionList == null) {
            minionList = new MinionList(this);
        }

        return minionList;
    }

    @Override
    public boolean isMonster() {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public Monster asMonster() {
        WorldPartition.checkOwner();
        return this;
    }

    @Override
    public boolean isWalker() {
        WorldPartition.checkOwner();
        return ((leader == null) ? super.isWalker() : leader.isWalker());
    }

    @Override
    public boolean giveRaidCurse() {
        WorldPartition.checkOwner();
        return (isRaidMinion() && (leader != null)) ? leader.giveRaidCurse() : super.giveRaidCurse();
    }

    @Override
    public void doCast(Skill skill, Creature target, List<WorldObject> targets) {
        WorldPartition.checkOwner();
        // Monsters must not buff the player they target.
        if (!skill.hasNegativeEffect() && (getTarget() != null) && getTarget().isPlayer()) {
            setCastingNow(false);
            setCastingSimultaneouslyNow(false);
            return;
        }

        super.doCast(skill, target, targets);
    }
}
