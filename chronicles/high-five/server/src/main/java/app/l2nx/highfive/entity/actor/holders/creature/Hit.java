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
package app.l2nx.highfive.entity.actor.holders.creature;

import app.l2nx.highfive.entity.WorldObject;
import lombok.Getter;

/**
 * @author UnAfraid
 */
public class Hit {
    private static final int HITFLAG_USESS = 0x10;
    private static final int HITFLAG_CRIT = 0x20;
    private static final int HITFLAG_SHLD = 0x40;
    private static final int HITFLAG_MISS = 0x80;

    @Getter
    private final int targetId;

    @Getter
    private final int damage;

    @Getter
    private int flags = 0;

    public Hit(WorldObject target, int damage, boolean miss, boolean crit, byte shld, boolean soulshot, int ssGrade) {
        targetId = target.getObjectId();
        this.damage = damage;

        if (soulshot) {
            flags |= HITFLAG_USESS | ssGrade;
        }

        if (crit) {
            flags |= HITFLAG_CRIT;
        }

        if (shld > 0) {
            flags |= HITFLAG_SHLD;
        }

        if (miss) {
            flags |= HITFLAG_MISS;
        }
    }
}
