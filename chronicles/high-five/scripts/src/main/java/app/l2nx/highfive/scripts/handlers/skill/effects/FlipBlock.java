/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.scripts.handlers.skill.effects;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.ArenaParticipantsHolder;
import app.l2nx.highfive.entity.actor.instance.Block;
import app.l2nx.highfive.managers.HandysBlockCheckerManager;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.util.StatSet;

/**
 * Flip Block effect implementation.
 * @author Mobius
 */
public class FlipBlock extends AbstractEffect
{
	public FlipBlock(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
	}
	
	@Override
	public boolean isInstant()
	{
		return true;
	}
	
	@Override
	public void onStart(Creature effector, Creature effected, Skill skill)
	{
		final Block block = effected instanceof Block ? (Block) effected : null;
		final Player player = effector.isPlayer() ? effector.asPlayer() : null;
		if ((block == null) || (player == null))
		{
			return;
		}
		
		final int arena = player.getBlockCheckerArena();
		if (arena != -1)
		{
			final ArenaParticipantsHolder holder = HandysBlockCheckerManager.getInstance().getHolder(arena);
			if (holder == null)
			{
				return;
			}
			
			final int team = holder.getPlayerTeam(player);
			final int color = block.getColorEffect();
			if ((team == 0) && (color == 0x00))
			{
				block.changeColor(player, holder, team);
			}
			else if ((team == 1) && (color == 0x53))
			{
				block.changeColor(player, holder, team);
			}
		}
	}
}
