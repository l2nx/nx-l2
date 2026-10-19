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
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.util.StatSet;

/**
 * Clan Gate effect implementation.
 * @author ZaKaX
 */
public class ClanGate extends AbstractEffect
{
	public ClanGate(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
	}
	
	@Override
	public void onStart(Creature effector, Creature effected, Skill skill)
	{
		if (effected.isPlayer())
		{
			final Clan clan = effected.asPlayer().getClan();
			if (clan != null)
			{
				clan.broadcastToOtherOnlineMembers(new SystemMessage(SystemMessageId.COURT_MAGICIAN_THE_PORTAL_HAS_BEEN_CREATED), effected.asPlayer());
			}
		}
	}
}
