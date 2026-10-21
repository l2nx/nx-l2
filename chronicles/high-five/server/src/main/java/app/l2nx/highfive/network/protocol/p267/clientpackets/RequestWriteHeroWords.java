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
package app.l2nx.highfive.network.protocol.highfive.clientpackets;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.olympiad.Hero;

/**
 * Format chS c (id) 0xD0 h (subid) 0x0C S the hero's words :)
 * @author -Wooden-
 */
public class RequestWriteHeroWords extends ClientPacket {
    private String heroWords;

    @Override
    protected void readImpl() {
        heroWords = readString();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if ((player == null) || !player.isHero()) {
            return;
        }

        if ((heroWords == null) || (heroWords.length() > 300)) {
            return;
        }

        Hero.getInstance().setHeroMessage(player, heroWords);
    }
}
