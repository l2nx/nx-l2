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
package app.l2nx.highfive.mechanics.stats.functions.formulas;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.mechanics.stats.functions.AbstractFunction;

/**
 * @author UnAfraid
 */
public class FuncAtkAccuracy extends AbstractFunction
{
	private static final FuncAtkAccuracy _faa_instance = new FuncAtkAccuracy();
	
	public static AbstractFunction getInstance()
	{
		return _faa_instance;
	}
	
	private FuncAtkAccuracy()
	{
		super(Stat.ACCURACY_COMBAT, 1, null, 0, null);
	}
	
	@Override
	public double calc(Creature effector, Creature effected, Skill skill, double initVal)
	{
		final int level = effector.getLevel();
		
		// [Square(DEX)] * 6 + level + weapon hitbonus;
		double value = initVal + (Math.sqrt(effector.getDEX()) * 6) + level;
		if (level > 77)
		{
			value += level - 76;
		}
		
		if (level > 69)
		{
			value += level - 69;
		}
		
		return value;
	}
}
