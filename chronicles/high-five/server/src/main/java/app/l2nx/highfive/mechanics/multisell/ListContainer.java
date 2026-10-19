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
package app.l2nx.highfive.mechanics.multisell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * @author DS
 */
public class ListContainer {
    @Getter
    private final int listId;

    @Setter
    private boolean applyTaxes = false;

    @Setter
    private boolean maintainEnchantment = false;

    @Getter
    @Setter
    private double useRate = 1.0;

    @Getter
    protected List<Entry> entries = new ArrayList<>();

    protected Set<Integer> npcsAllowed = null;

    public ListContainer(int listId) {
        this.listId = listId;
    }

    public boolean getApplyTaxes() {
        return applyTaxes;
    }

    public boolean getMaintainEnchantment() {
        return maintainEnchantment;
    }

    public void allowNpc(int npcId) {
        if (npcsAllowed == null) {
            npcsAllowed = new HashSet<>();
        }

        npcsAllowed.add(npcId);
    }

    public boolean isNpcAllowed(int npcId) {
        return (npcsAllowed != null) && npcsAllowed.contains(npcId);
    }
}
