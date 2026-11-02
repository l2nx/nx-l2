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
package app.l2nx.highfive.entity.actor.holders.npc;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * @author xban1x
 */
@SingleWriter
public class AbsorberInfo {
    private int objectId;
    private double absorbedHp;

    public AbsorberInfo(int objectId, double pAbsorbedHp) {
        this.objectId = objectId;
        absorbedHp = pAbsorbedHp;
    }

    public int getObjectId() {
        WorldPartition.checkOwner();
        return objectId;
    }

    public void setObjectId(int objectId) {
        WorldPartition.checkOwner();
        this.objectId = objectId;
    }

    public double getAbsorbedHp() {
        WorldPartition.checkOwner();
        return absorbedHp;
    }

    public void setAbsorbedHp(double absorbedHp) {
        WorldPartition.checkOwner();
        this.absorbedHp = absorbedHp;
    }

    @Override
    public boolean equals(Object obj) {
        return (this == obj) || ((obj instanceof AbsorberInfo other) && (other.objectId == objectId));
    }

    @Override
    public int hashCode() {
        return objectId;
    }
}
