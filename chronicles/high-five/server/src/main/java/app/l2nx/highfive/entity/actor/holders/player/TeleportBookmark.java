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

import app.l2nx.highfive.entity.Location;
import lombok.Getter;
import lombok.Setter;

/**
 * @author UnAfraid
 */
public class TeleportBookmark extends Location {
    @Getter
    private final int id;

    @Getter
    @Setter
    private int icon;

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private String tag;

    public TeleportBookmark(int id, int x, int y, int z, int icon, String tag, String name) {
        super(x, y, z);
        this.id = id;
        this.icon = icon;
        this.name = name;
        this.tag = tag;
    }
}
