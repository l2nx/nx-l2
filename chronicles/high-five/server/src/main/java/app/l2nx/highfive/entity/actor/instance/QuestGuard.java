/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.attackable.OnAttackableAttack;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.attackable.OnAttackableKill;
import app.l2nx.highfive.mechanics.skill.Skill;
import lombok.Getter;
import lombok.Setter;

/**
 * This class extends Guard class for quests, that require tracking of onAttack and onKill events from monsters' attacks.
 * @author GKR
 */
public class QuestGuard extends Guard {
    @Setter
    private boolean autoAttackable = true;

    @Getter
    @Setter
    private boolean passive = false;

    /**
     * Creates a quest guard.
     * @param template the quest guard NPC template
     */
    public QuestGuard(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.QuestGuard);
        setCanReturnToSpawnPoint(false);
    }

    @Override
    public void addDamage(Creature attacker, int damage, Skill skill) {
        super.addDamage(attacker, damage, skill);

        if (attacker.isAttackable()
                && EventDispatcher.getInstance().hasListener(EventType.ON_ATTACKABLE_ATTACK, this)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnAttackableAttack.builder()
                                    .attacker(null)
                                    .target(this)
                                    .damage(damage)
                                    .skill(skill)
                                    .summon(false)
                                    .build(),
                            this);
        }
    }

    @Override
    protected void notifyKill(Creature killer) {
        super.notifyKill(killer);
        if (killer.isAttackable() && EventDispatcher.getInstance().hasListener(EventType.ON_ATTACKABLE_KILL, this)) {
            EventDispatcher.getInstance()
                    .notifyEventAsyncDelayed(
                            OnAttackableKill.builder()
                                    .attacker(null)
                                    .target(this)
                                    .summon(false)
                                    .build(),
                            this,
                            onKillDelay);
        }
    }

    @Override
    public void addDamageHate(Creature attacker, long damage, long aggro) {
        if (!passive && !attacker.isPlayer()) {
            super.addDamageHate(attacker, damage, aggro);
        }
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        return autoAttackable && !attacker.isPlayer();
    }
}
