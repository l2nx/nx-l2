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
package app.l2nx.highfive.entity.teleporter;

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * @author UnAfraid
 */
public class TeleportLocation extends Location {
    @Getter
    private final int id;

    @Getter
    private final String name;

    @Getter
    private final int feeId;

    @Getter
    private final long feeCount;

    @Getter
    private final List<Integer> castleId;

    public TeleportLocation(int id, StatSet set) {
        super(set);
        this.id = id;
        name = set.getString("name", null);
        feeId = set.getInt("feeId", Inventory.ADENA_ID);
        feeCount = set.getLong("feeCount", 0);

        final String castleIds = set.getString("castleId", "");
        if (castleIds.isEmpty()) {
            this.castleId = Collections.emptyList();
        } else if (!castleIds.contains(";")) {
            this.castleId = Collections.singletonList(Integer.parseInt(castleIds));
        } else {
            this.castleId = new ArrayList<>();
            for (String castleId : castleIds.split(";")) {
                this.castleId.add(Integer.parseInt(castleId));
            }
        }
    }
}
