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
package app.l2nx.highfive.scripts.handlers.chat.commands.user;

import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.zone.type.RespawnZone;
import app.l2nx.highfive.handler.IUserCommandHandler;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.SystemMessage;

/**
 * Loc user command.
 */
public class Loc implements IUserCommandHandler
{
	private static final int[] COMMAND_IDS =
	{
		0
	};
	
	@Override
	public boolean onCommand(int id, Player player)
	{
		int region;
		final RespawnZone zone = ZoneManager.getInstance().getZone(player, RespawnZone.class);
		if (zone != null)
		{
			region = MapRegionData.getInstance().getRestartRegionLocId(player, zone.getAllRespawnPoints().get(Race.HUMAN));
		}
		else
		{
			region = MapRegionData.getInstance().getMapRegionLocId(player);
		}
		
		SystemMessage sm;
		if (region > 0)
		{
			sm = new SystemMessage(region);
			if (sm.getSystemMessageId().getParamCount() == 3)
			{
				sm.addInt(player.getX());
				sm.addInt(player.getY());
				sm.addInt(player.getZ());
			}
		}
		else
		{
			sm = new SystemMessage(SystemMessageId.CURRENT_LOCATION_S1);
			sm.addString(player.getX() + ", " + player.getY() + ", " + player.getZ());
		}
		
		player.sendPacket(sm);
		return true;
	}
	
	@Override
	public int[] getCommandList()
	{
		return COMMAND_IDS;
	}
}
