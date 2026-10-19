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

import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Trap;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.util.StatSet;

/**
 * Summon Trap effect implementation.
 * @author Zoey76
 */
public class SummonTrap extends AbstractEffect {
    private final int _despawnTime;
    private final int _npcId;

    public SummonTrap(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        _despawnTime = params.getInt("despawnTime", 0);
        _npcId = params.getInt("npcId", 0);
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        if ((effected == null) || !effected.isPlayer() || effected.isAlikeDead()) {
            return;
        }

        if (_npcId <= 0) {
            LOGGER.warning(
                    SummonTrap.class.getSimpleName() + ": Invalid NPC ID:" + _npcId + " in skill ID: " + skill.getId());
            return;
        }

        final Player player = effected.asPlayer();
        if (player.inObserverMode() || player.isMounted()) {
            return;
        }

        // Unsummon previous trap
        if (player.getTrap() != null) {
            player.getTrap().unSummon();
        }

        final NpcTemplate npcTemplate = NpcData.getInstance().getTemplate(_npcId);
        if (npcTemplate == null) {
            LOGGER.warning(SummonTrap.class.getSimpleName() + ": Spawn of the non-existing Trap ID: " + _npcId
                    + " in skill ID:" + skill.getId());
            return;
        }

        final Trap trap = new Trap(npcTemplate, player, _despawnTime);
        trap.fullRestore();
        trap.setInvul(true);
        trap.setHeading(player.getHeading());
        trap.spawnMe(player.getX(), player.getY(), player.getZ());
        player.setTrap(trap);
    }
}
