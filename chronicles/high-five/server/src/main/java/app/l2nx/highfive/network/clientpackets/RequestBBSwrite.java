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
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.CommunityBoardHandler;

/**
 * RequestBBSwrite client packet implementation.
 * @author -Wooden-
 */
public class RequestBBSwrite extends ClientPacket {
    private String url;
    private String arg1;
    private String arg2;
    private String arg3;
    private String arg4;
    private String arg5;

    @Override
    protected void readImpl() {
        url = readString();
        arg1 = readString();
        arg2 = readString();
        arg3 = readString();
        arg4 = readString();
        arg5 = readString();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        CommunityBoardHandler.getInstance().handleWriteCommand(player, url, arg1, arg2, arg3, arg4, arg5);
    }
}
