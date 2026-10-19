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

import java.util.concurrent.ThreadLocalRandom;

import app.l2nx.highfive.ai.AttackableAI;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.util.StatSet;

/**
 * Run Away effect implementation.
 * @author Zoey76
 */
public class RunAway extends AbstractEffect
{
	private final int _power;
	private final int _time;
	
	public RunAway(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
		
		_power = params.getInt("power", 0);
		_time = params.getInt("time", 0);
	}
	
	@Override
	public boolean isInstant()
	{
		return true;
	}
	
	@Override
	public void onStart(Creature effector, Creature effected, Skill skill)
	{
		if (!effected.isAttackable())
		{
			return;
		}
		
		if (ThreadLocalRandom.current().nextInt(100) > _power)
		{
			return;
		}
		
		if (effected.isCastingNow() && effected.canAbortCast())
		{
			effected.abortCast();
		}
		
		((AttackableAI) effected.getAI()).setFearTime(_time);
		effected.getAI().notifyActionAfraid(effector, true);
	}
}
