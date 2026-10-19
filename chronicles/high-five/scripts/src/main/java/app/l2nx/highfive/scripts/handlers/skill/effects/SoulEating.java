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
package app.l2nx.highfive.scripts.handlers.skill.effects;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Playable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.playable.OnPlayableExpChanged;
import app.l2nx.highfive.mechanics.events.listeners.ConsumerEventListener;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.ExSpawnEmitter;
import app.l2nx.highfive.util.StatSet;

/**
 * Soul Eating effect implementation.
 * @author UnAfraid
 */
public class SoulEating extends AbstractEffect {
    private final int expNeeded;

    public SoulEating(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);
        expNeeded = params.getInt("expNeeded");
    }

    @Override
    public void onExit(Creature effector, Creature effected, Skill skill) {
        if (effected.isPlayer()) {
            effected.removeListenerIf(EventType.ON_PLAYABLE_EXP_CHANGED, listener -> listener.getOwner() == this);
        }
    }

    public void onExperienceReceived(Playable playable, long exp) {
        // TODO: Verify logic.
        if (playable.isPlayer() && (exp >= expNeeded)) {
            final Player player = playable.asPlayer();
            final int maxSouls = (int) player.calcStat(Stat.MAX_SOULS, 0, null, null);
            if (player.getChargedSouls() >= maxSouls) {
                playable.sendPacket(SystemMessageId.SOUL_CANNOT_BE_ABSORBED_ANYMORE);
                return;
            }

            player.increaseSouls(1);

            if ((player.getTarget() != null) && player.getTarget().isNpc()) {
                final Npc npc = playable.getTarget().asNpc();
                player.broadcastPacket(new ExSpawnEmitter(player, npc));
            }
        }
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        if (effected.isPlayer()) {
            effected.addListener(new ConsumerEventListener(
                    effected,
                    EventType.ON_PLAYABLE_EXP_CHANGED,
                    (OnPlayableExpChanged event) ->
                            onExperienceReceived(event.activeChar(), (event.newExp() - event.oldExp())),
                    this));
        }
    }
}
