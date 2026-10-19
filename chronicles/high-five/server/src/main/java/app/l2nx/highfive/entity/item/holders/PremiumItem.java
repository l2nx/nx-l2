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
package app.l2nx.highfive.entity.item.holders;

import lombok.Getter;

/**
 ** @author Gnacik
 */
public class PremiumItem {
    @Getter
    private final int itemId;

    @Getter
    private long count;

    @Getter
    private final String sender;

    public PremiumItem(int itemid, long count, String sender) {
        itemId = itemid;
        this.count = count;
        this.sender = sender;
    }

    public void updateCount(long newcount) {
        count = newcount;
    }
}
