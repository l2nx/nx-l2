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
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.instance.Defender;
import app.l2nx.highfive.entity.actor.instance.FortCommander;
import app.l2nx.highfive.entity.actor.instance.SiegeFlag;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectFlag;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.util.StatSet;

/**
 * Fear effect implementation.
 * @author littlecrow
 */
public class Fear extends AbstractEffect
{
	public Fear(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
	}
	
	@Override
	public boolean canStart(Creature effector, Creature effected, Skill skill)
	{
		return effected.isPlayer() || effected.isSummon() || (effected.isAttackable() && //
			!((effected instanceof Defender) || (effected instanceof FortCommander) || //
				(effected instanceof SiegeFlag) || (effected.getTemplate().getRace() == Race.SIEGE_WEAPON)));
	}
	
	@Override
	public int getEffectFlags()
	{
		return EffectFlag.FEAR.getMask();
	}
	
	@Override
	public EffectType getEffectType()
	{
		return EffectType.FEAR;
	}
	
	@Override
	public int getTicks()
	{
		return 5;
	}
	
	@Override
	public boolean onActionTime(Creature effector, Creature effected, Skill skill)
	{
		effected.getAI().notifyActionAfraid(effector, false);
		return false;
	}
	
	@Override
	public void onStart(Creature effector, Creature effected, Skill skill)
	{
		if (effected.isCastingNow() && effected.canAbortCast())
		{
			effected.abortCast();
		}
		
		effected.getAI().notifyActionAfraid(effector, true);
	}
}
