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
package app.l2nx.highfive.entity.item.enchant;

import app.l2nx.highfive.data.holders.RangeChanceHolder;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author UnAfraid
 */
@Slf4j
public class EnchantItemGroup {
    private final List<RangeChanceHolder> chances = new ArrayList<>();

    @Getter
    private final String name;

    public EnchantItemGroup(String name) {
        this.name = name;
    }

    /**
     * @param holder
     */
    public void addChance(RangeChanceHolder holder) {
        chances.add(holder);
    }

    /**
     * @param index
     * @return chance for success rate for current enchant item group.
     */
    public double getChance(int index) {
        if (!chances.isEmpty()) {
            for (RangeChanceHolder holder : chances) {
                if ((holder.min() <= index) && (holder.max() >= index)) {
                    return holder.chance();
                }
            }
            // LOGGER.log(Level.WARNING, getClass().getSimpleName() + ": Couldn't match proper chance for item group: "
            // + _name);
            return chances.get(chances.size() - 1).chance();
        }

        log.warn("Item group: {} doesn't have any chances!", name);
        return -1;
    }
}
