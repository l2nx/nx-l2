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
package app.l2nx.highfive.mechanics.olympiad;

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.util.StatSet;
import lombok.Getter;
import lombok.Setter;

/**
 * @author DS, Zoey76
 */
public class Participant {
    @Getter
    private final int objectId;

    @Getter
    @Setter
    private Player player;

    @Getter
    private final String name;

    @Getter
    private final int side;

    @Getter
    private final int baseClass;

    @Getter
    @Setter
    private boolean disconnected = false;

    @Getter
    @Setter
    private boolean defaulted = false;

    @Getter
    private final StatSet stats;

    @Getter
    public String clanName;

    @Getter
    public int clanId;

    public Participant(Player plr, int olympiadSide) {
        objectId = plr.getObjectId();
        player = plr;
        name = plr.getName();
        side = olympiadSide;
        baseClass = plr.getBaseClass();
        stats = Olympiad.getNobleStats(objectId);
        clanName = plr.getClan() != null ? plr.getClan().getName() : "";
        clanId = plr.getClanId();
    }

    public Participant(int objId, int olympiadSide) {
        objectId = objId;
        player = null;
        name = "-";
        side = olympiadSide;
        baseClass = 0;
        stats = null;
        clanName = "";
        clanId = 0;
    }

    /**
     * Updates the reference to {@link #player}, if it's null or appears off-line.
     * @return {@code true} if after the update the player isn't null, {@code false} otherwise.
     */
    public boolean updatePlayer() {
        if ((player == null) || !player.isOnline()) {
            player = World.getPlayer(getObjectId());
        }

        return player != null;
    }

    /**
     * @param statName
     * @param increment
     */
    public void updateStat(String statName, int increment) {
        stats.set(statName, Math.max(stats.getInt(statName) + increment, 0));
    }
}
