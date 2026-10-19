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

import app.l2nx.highfive.entity.item.ItemTemplate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * @author UnAfraid
 */
public class EnchantScrollGroup {
    @Getter
    private final int id;

    private List<EnchantRateItem> rateGroups;

    public EnchantScrollGroup(int id) {
        this.id = id;
    }

    /**
     * Adds new rate group.
     * @param group
     */
    public void addRateGroup(EnchantRateItem group) {
        if (rateGroups == null) {
            rateGroups = new ArrayList<>();
        }

        rateGroups.add(group);
    }

    /**
     * @return {@code List} of all enchant rate items, Empty list if none.
     */
    public List<EnchantRateItem> getRateGroups() {
        return rateGroups != null ? rateGroups : Collections.<EnchantRateItem>emptyList();
    }

    /**
     * @param item
     * @return {@link EnchantRateItem}, {@code NULL} in case non of rate items can be used with.
     */
    public EnchantRateItem getRateGroup(ItemTemplate item) {
        for (EnchantRateItem group : getRateGroups()) {
            if (group.validate(item)) {
                return group;
            }
        }

        return null;
    }
}
