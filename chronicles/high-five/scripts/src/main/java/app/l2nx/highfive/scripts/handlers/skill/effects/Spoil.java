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
import app.l2nx.highfive.entity.actor.instance.Monster;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.util.StatSet;

/**
 * Spoil effect implementation.
 * @author _drunk_, Ahmed, Zoey76
 */
public class Spoil extends AbstractEffect
{
	public Spoil(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
	}
	
	@Override
	public boolean calcSuccess(Creature effector, Creature effected, Skill skill)
	{
		return Formulas.calcMagicSuccess(effector, effected, skill);
	}
	
	@Override
	public boolean isInstant()
	{
		return true;
	}
	
	@Override
	public void onStart(Creature effector, Creature effected, Skill skill)
	{
		if (!effected.isMonster() || effected.isDead())
		{
			effector.sendPacket(SystemMessageId.INVALID_TARGET);
			return;
		}
		
		final Monster target = effected.asMonster();
		if (target.isSpoiled())
		{
			effector.sendPacket(SystemMessageId.IT_HAS_ALREADY_BEEN_SPOILED);
			return;
		}
		
		target.setSpoilerObjectId(effector.getObjectId());
		effector.sendPacket(SystemMessageId.THE_SPOIL_CONDITION_HAS_BEEN_ACTIVATED);
		target.getAI().notifyActionAttacked(effector);
	}
}
