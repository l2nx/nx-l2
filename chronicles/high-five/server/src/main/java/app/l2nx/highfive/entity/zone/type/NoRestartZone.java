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

import app.l2nx.highfive.WorldLoader;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.ZoneType;
import lombok.Getter;
import lombok.Setter;

/**
 * A simple no restart zone
 * @author GKR
 */
public class NoRestartZone extends ZoneType {
    @Getter
    @Setter
    private int restartAllowedTime = 0;

    @Getter
    @Setter
    private int restartTime = 0;

    public NoRestartZone(int id) {
        super(id);
    }

    @Override
    public void setParameter(String name, String value) {
        if (name.equalsIgnoreCase("restartAllowedTime")) {
            restartAllowedTime = Integer.parseInt(value) * 1000;
        } else if (name.equalsIgnoreCase("restartTime")) {
            restartTime = Integer.parseInt(value) * 1000;
        } else if (name.equalsIgnoreCase("instanceId")) {
            // Do nothing.
        } else {
            super.setParameter(name, value);
        }
    }

    @Override
    protected void onEnter(Creature creature) {
        if (creature.isPlayer()) {
            creature.setInsideZone(ZoneId.NO_RESTART, true);
        }
    }

    @Override
    protected void onExit(Creature creature) {
        if (creature.isPlayer()) {
            creature.setInsideZone(ZoneId.NO_RESTART, false);
        }
    }

    @Override
    public void onPlayerLoginInside(Player player) {
        if (!isEnabled()) {
            return;
        }

        if (((ClockBridge.wallMillis() - player.getLastAccess()) > restartTime)
                && ((ClockBridge.gameMillis() - WorldLoader.getStartTime()) > restartAllowedTime)) {
            player.teleToLocation(TeleportWhereType.TOWN);
        }
    }
}
