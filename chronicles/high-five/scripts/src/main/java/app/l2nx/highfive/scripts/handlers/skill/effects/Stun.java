/*
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
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.effects.EffectFlag;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.util.StatSet;

/**
 * Stun effect implementation.
 * @author mkizub
 */
public class Stun extends AbstractEffect
{
	public Stun(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
	}
	
	@Override
	public int getEffectFlags()
	{
		return EffectFlag.STUNNED.getMask();
	}
	
	@Override
	public EffectType getEffectType()
	{
		return EffectType.STUN;
	}
	
	@Override
	public void onExit(Creature effector, Creature effected, Skill skill)
	{
		if ((effected == null) || effected.isRaid())
		{
			return;
		}
		
		effected.stopStunning(false);
		if (effected.isSummon())
		{
			if ((effector != null) && !effector.isDead())
			{
				if (effector.isPlayable() && (effected.asPlayer().getPvpFlag() == 0))
				{
					effected.getAI().setIntentionMoveTo(effected.asPlayer());
				}
				else
				{
					effected.asSummon().doSummonAttack(effector);
				}
			}
			else
			{
				effected.getAI().setIntentionMoveTo(effected.asPlayer());
			}
		}
	}
	
	@Override
	public boolean canStart(Creature effector, Creature effected, Skill skill)
	{
		// Raids are immune, checked here so that the effect flag is not applied to them either.
		return (effected != null) && !effected.isRaid();
	}
	
	@Override
	public void onStart(Creature effector, Creature effected, Skill skill)
	{
		effected.startStunning();
	}
}
