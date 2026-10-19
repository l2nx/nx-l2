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
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * This class manages all Monsters.
 * <ul>
 * <li>Minion</li>
 * <li>RaidBoss</li>
 * <li>GrandBoss</li>
 * </ul>
 */
public class Monster extends Attackable {
    protected boolean enableMinions = true;

    @Getter
    @Setter
    private Monster leader = null;

    private MinionList minionList = null;

    /**
     * Creates a monster.
     * @param template the monster NPC template
     */
    public Monster(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.Monster);
        setAutoAttackable(true);
    }

    /**
     * Return True if the attacker is not another Monster.
     */
    @Override
    public boolean isAutoAttackable(Creature attacker) {
        if (isFakePlayer()) {
            return FakePlayersConfig.FAKE_PLAYER_AUTO_ATTACKABLE
                    || isInCombat()
                    || attacker.isMonster()
                    || (getScriptValue() > 0);
        }

        // Check if the Monster target is aggressive.
        if (NpcConfig.GUARD_ATTACK_AGGRO_MOB && isAggressive() && (attacker instanceof Guard)) {
            return true;
        }

        if (attacker.isMonster()) {
            return attacker.isFakePlayer();
        }

        // Anything considers monsters friendly except Players, Attackables (Guards, Friendly NPC), Traps and
        // EffectPoints.
        if (!attacker.isPlayable()
                && !attacker.isAttackable()
                && !(attacker instanceof Trap)
                && !(attacker instanceof EffectPoint)) {
            return false;
        }

        return super.isAutoAttackable(attacker);
    }

    /**
     * Return True if the Monster is Aggressive (aggroRange > 0).
     */
    @Override
    public boolean isAggressive() {
        return getTemplate().isAggressive() && !isAffected(EffectFlag.PASSIVE);
    }

    @Override
    public void onSpawn() {
        if (!isTeleporting() && (leader != null)) {
            setRandomWalking(false);
            setIsRaidMinion(leader.isRaid());
            leader.getMinionList().onMinionSpawn(this);
        }

        // Dynamic script-based minions spawned here, after all preparations.
        super.onSpawn();
    }

    @Override
    public synchronized void onTeleported() {
        super.onTeleported();

        if (hasMinions()) {
            getMinionList().onMasterTeleported();
        }
    }

    @Override
    public boolean deleteMe() {
        if (hasMinions()) {
            getMinionList().onMasterDie(true);
        }

        if (leader != null) {
            leader.getMinionList().onMinionDie(this, 0);
        }

        return super.deleteMe();
    }

    public void enableMinions(boolean value) {
        enableMinions = value;
    }

    public boolean hasMinions() {
        return minionList != null;
    }

    public MinionList getMinionList() {
        if (minionList == null) {
            synchronized (this) {
                if (minionList == null) {
                    minionList = new MinionList(this);
                }
            }
        }

        return minionList;
    }

    @Override
    public boolean isMonster() {
        return true;
    }

    @Override
    public Monster asMonster() {
        return this;
    }

    /**
     * @return true if this Monster (or its master) is registered in WalkingManager
     */
    @Override
    public boolean isWalker() {
        return ((leader == null) ? super.isWalker() : leader.isWalker());
    }

    /**
     * @return {@code true} if this Monster is not raid minion, master state otherwise.
     */
    @Override
    public boolean giveRaidCurse() {
        return (isRaidMinion() && (leader != null)) ? leader.giveRaidCurse() : super.giveRaidCurse();
    }

    @Override
    public void doCast(Skill skill, Creature target, List<WorldObject> targets) {
        // Might need some exceptions here, but it will prevent the monster buffing player bug.
        if (!skill.hasNegativeEffect() && (getTarget() != null) && getTarget().isPlayer()) {
            setCastingNow(false);
            setCastingSimultaneouslyNow(false);
            return;
        }

        super.doCast(skill, target, targets);
    }
}
