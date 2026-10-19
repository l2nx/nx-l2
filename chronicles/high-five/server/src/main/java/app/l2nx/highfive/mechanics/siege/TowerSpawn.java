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
package app.l2nx.highfive.mechanics.siege;

import app.l2nx.highfive.entity.Location;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * @author malyelfik
 */
public class TowerSpawn {
    @Getter
    private final int id;

    @Getter
    private final Location location;

    @Getter
    private List<Integer> zoneList = null;

    @Getter
    @Setter
    private int upgradeLevel = 0;

    public TowerSpawn(int npcId, Location location) {
        this.location = location;
        id = npcId;
    }

    public TowerSpawn(int npcId, Location location, List<Integer> zoneList) {
        this.location = location;
        id = npcId;
        this.zoneList = zoneList;
    }
}
