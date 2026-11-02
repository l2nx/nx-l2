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
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.ZoneRespawn;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * @author xban1x
 */
@SingleWriter
public abstract class ResidenceZone extends ZoneRespawn {
    private int residenceId;

    protected ResidenceZone(int id) {
        super(id);
    }

    public int getResidenceId() {
        WorldPartition.checkOwner();
        return residenceId;
    }

    /** Unchecked: the zone loader sets it from the checked {@code setParameter}. */
    protected final void setResidenceId(int residenceId) {
        this.residenceId = residenceId;
    }

    public void banishForeigners(int owningClanId) {
        WorldPartition.checkOwner();
        try (Snapshot<Player> players = ScratchBuffers.empty()) {
            collectPlayersInside(players);
            for (Player temp : players) {
                if ((owningClanId != 0) && (temp.getClanId() == owningClanId)) {
                    continue;
                }

                temp.teleToLocation(getBanishSpawnLoc(), true);
            }
        }
    }
}
