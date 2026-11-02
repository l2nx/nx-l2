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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Respawn zone implementation.
 * @author Nyaran
 */
@SingleWriter
public class RespawnZone extends ZoneType {
    private final EnumMap<Race, String> allRespawnPoints = new EnumMap<>(Race.class);

    public RespawnZone(int id) {
        super(id);
    }

    /** Read-only view: the zone loader adds the points, nothing changes them afterwards. */
    public Map<Race, String> getAllRespawnPoints() {
        WorldPartition.checkOwner();
        return Collections.unmodifiableMap(allRespawnPoints);
    }

    @Override
    protected void onEnter(Creature creature) {}

    @Override
    protected void onExit(Creature creature) {}

    public void addRaceRespawnPoint(String race, String point) {
        WorldPartition.checkOwner();
        allRespawnPoints.put(Race.valueOf(race), point);
    }

    public String getRespawnPoint(Player player) {
        WorldPartition.checkOwner();
        return allRespawnPoints.get(player.getRace());
    }
}
