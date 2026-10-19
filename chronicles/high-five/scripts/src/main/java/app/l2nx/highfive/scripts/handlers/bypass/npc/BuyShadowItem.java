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
package app.l2nx.highfive.scripts.handlers.bypass.npc;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Merchant;
import app.l2nx.highfive.handler.IBypassHandler;
import app.l2nx.highfive.network.serverpackets.NpcHtmlMessage;

public class BuyShadowItem implements IBypassHandler
{
	private static final String[] COMMANDS =
	{
		"BuyShadowItem"
	};
	
	@Override
	public boolean onCommand(String command, Player player, Creature target)
	{
		if (!(target instanceof Merchant))
		{
			return false;
		}
		
		final NpcHtmlMessage html = new NpcHtmlMessage(target.asNpc().getObjectId());
		if (player.getLevel() < 40)
		{
			html.setFile(player, "data/html/common/shadow_item-lowlevel.htm");
		}
		else if ((player.getLevel() >= 40) && (player.getLevel() < 46))
		{
			html.setFile(player, "data/html/common/shadow_item_d.htm");
		}
		else if ((player.getLevel() >= 46) && (player.getLevel() < 52))
		{
			html.setFile(player, "data/html/common/shadow_item_c.htm");
		}
		else if (player.getLevel() >= 52)
		{
			html.setFile(player, "data/html/common/shadow_item_b.htm");
		}
		
		html.replace("%objectId%", String.valueOf(target.asNpc().getObjectId()));
		player.sendPacket(html);
		
		return true;
	}
	
	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}
}
