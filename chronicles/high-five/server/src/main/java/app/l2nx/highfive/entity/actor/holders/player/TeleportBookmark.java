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
package app.l2nx.highfive.entity.actor.holders.player;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * @author UnAfraid
 */
@SingleWriter
public class TeleportBookmark extends Location {
    private final int id;
    private int icon;
    private String name;
    private String tag;

    public TeleportBookmark(int id, int x, int y, int z, int icon, String tag, String name) {
        super(x, y, z);
        this.id = id;
        this.icon = icon;
        this.name = name;
        this.tag = tag;
    }

    public int getId() {
        WorldPartition.checkOwner();
        return id;
    }

    public int getIcon() {
        WorldPartition.checkOwner();
        return icon;
    }

    public void setIcon(int icon) {
        WorldPartition.checkOwner();
        this.icon = icon;
    }

    public String getName() {
        WorldPartition.checkOwner();
        return name;
    }

    public void setName(String name) {
        WorldPartition.checkOwner();
        this.name = name;
    }

    public String getTag() {
        WorldPartition.checkOwner();
        return tag;
    }

    public void setTag(String tag) {
        WorldPartition.checkOwner();
        this.tag = tag;
    }
}
