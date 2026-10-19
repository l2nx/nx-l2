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
package app.l2nx.highfive.entity.zone.type;

import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.residences.AuctionableHall;
import app.l2nx.highfive.entity.residences.ClanHall;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.network.serverpackets.AgitDecoInfo;

/**
 * A clan hall zone
 * @author durgus
 */
public class ClanHallZone extends ResidenceZone
{
	public ClanHallZone(int id)
	{
		super(id);
	}
	
	@Override
	public void setParameter(String name, String value)
	{
		if (name.equals("clanHallId"))
		{
			setResidenceId(Integer.parseInt(value));
			
			// Register self to the correct clan hall.
			final ClanHall hall = ClanHallTable.getInstance().getClanHallById(getResidenceId());
			if (hall != null)
			{
				hall.setZone(this);
			}
			else
			{
				LOGGER.warning("ClanHallZone: Clan hall with id " + getResidenceId() + " does not exist!");
			}
		}
		else
		{
			super.setParameter(name, value);
		}
	}
	
	@Override
	protected void onEnter(Creature creature)
	{
		if (!creature.isPlayer())
		{
			return;
		}
		
		// Set as in clan hall.
		creature.setInsideZone(ZoneId.CLAN_HALL, true);
		final AuctionableHall clanHall = ClanHallTable.getInstance().getAuctionableHallById(getResidenceId());
		if (clanHall == null)
		{
			return;
		}
		
		// Send decoration packet
		creature.sendPacket(new AgitDecoInfo(clanHall));
	}
	
	@Override
	protected void onExit(Creature creature)
	{
		if (creature.isPlayer())
		{
			creature.setInsideZone(ZoneId.CLAN_HALL, false);
		}
	}
}
