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
import app.l2nx.highfive.handler.IParseBoardHandler;

/**
 * Homepage board.
 * @author Zoey76
 */
public class HomepageBoard implements IParseBoardHandler {
    private static final String[] COMMANDS = {"_bbslink"};

    @Override
    public String[] getCommandList() {
        return COMMANDS;
    }

    @Override
    public boolean onCommand(String command, Player player) {
        CommunityBoardHandler.separateAndSend(
                HtmCache.getInstance().getHtm(player, "data/html/CommunityBoard/homepage.html"), player);
        return true;
    }
}
