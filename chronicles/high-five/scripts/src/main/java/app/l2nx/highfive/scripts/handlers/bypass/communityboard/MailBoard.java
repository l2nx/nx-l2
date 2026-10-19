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
package app.l2nx.highfive.scripts.handlers.bypass.communityboard;

import app.l2nx.highfive.cache.HtmCache;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.CommunityBoardHandler;
import app.l2nx.highfive.handler.IWriteBoardHandler;

/**
 * Mail board.
 * @author Zoey76
 */
public class MailBoard implements IWriteBoardHandler
{
	private static final String[] COMMANDS =
	{
		"_maillist"
	};
	
	@Override
	public String[] getCommandList()
	{
		return COMMANDS;
	}
	
	@Override
	public boolean onCommand(String command, Player player)
	{
		CommunityBoardHandler.getInstance().addBypass(player, "Mail Command", command);
		
		final String html = HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/mail.html");
		CommunityBoardHandler.separateAndSend(html, player);
		return true;
	}
	
	@Override
	public boolean writeCommunityBoardCommand(Player player, String arg1, String arg2, String arg3, String arg4, String arg5)
	{
		// TODO: Implement.
		return false;
	}
}
