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

import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.SiegeFlag;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.util.StatSet;

/**
 * Headquarter Create effect implementation.
 * @author Adry_85
 */
public class HeadquarterCreate extends AbstractEffect
{
	private static final int HQ_NPC_ID = 35062;
	private final boolean _isAdvanced;
	
	public HeadquarterCreate(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
		
		_isAdvanced = params.getBoolean("isAdvanced", false);
	}
	
	@Override
	public boolean isInstant()
	{
		return true;
	}
	
	@Override
	public void onStart(Creature effector, Creature effected, Skill skill)
	{
		final Player player = effector.asPlayer();
		if (!player.isClanLeader())
		{
			return;
		}
		
		final SiegeFlag flag = new SiegeFlag(player, NpcData.getInstance().getTemplate(HQ_NPC_ID), _isAdvanced, false);
		flag.setTitle(player.getClan().getName());
		flag.setCurrentHpMp(flag.getMaxHp(), flag.getMaxMp());
		flag.setHeading(player.getHeading());
		flag.spawnMe(player.getX(), player.getY(), player.getZ() + 50);
		// already call addFlag method in SiegeFlag constructor
	}
}
