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
package app.l2nx.highfive.entity.actor.transform;

import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.util.StatSet;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * @author UnAfraid
 */
public class TransformLevelData {
    @Getter
    private final int level;

    @Getter
    private final double levelMod;

    private Map<Integer, Double> stats;

    public TransformLevelData(StatSet set) {
        level = set.getInt("val");
        levelMod = set.getDouble("levelMod");
        addStats(Stat.MAX_HP, set.getDouble("hp"));
        addStats(Stat.MAX_MP, set.getDouble("mp"));
        addStats(Stat.MAX_CP, set.getDouble("cp"));
        addStats(Stat.REGENERATE_HP_RATE, set.getDouble("hpRegen"));
        addStats(Stat.REGENERATE_MP_RATE, set.getDouble("mpRegen"));
        addStats(Stat.REGENERATE_CP_RATE, set.getDouble("cpRegen"));
    }

    private void addStats(Stat stat, double value) {
        if (stats == null) {
            stats = new HashMap<>();
        }

        stats.put(stat.ordinal(), value);
    }

    public double getStats(Stat stat) {
        if ((stats == null) || !stats.containsKey(stat.ordinal())) {
            return 0;
        }

        return stats.get(stat.ordinal());
    }
}
