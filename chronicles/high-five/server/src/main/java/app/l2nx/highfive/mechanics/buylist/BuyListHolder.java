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
package app.l2nx.highfive.mechanics.buylist;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * @author NosBit
 */
public class BuyListHolder {
    @Getter
    private final int listId;

    private final Map<Integer, Product> products = new LinkedHashMap<>();

    @Getter
    private Set<Integer> npcsAllowed = null;

    public BuyListHolder(int listId) {
        this.listId = listId;
    }

    public Collection<Product> getProducts() {
        return products.values();
    }

    public Product getProductByItemId(int itemId) {
        return products.get(itemId);
    }

    public void addProduct(Product product) {
        products.put(product.getItemId(), product);
    }

    public void addAllowedNpc(int npcId) {
        if (npcsAllowed == null) {
            npcsAllowed = new HashSet<>();
        }

        npcsAllowed.add(npcId);
    }

    public boolean isNpcAllowed(int npcId) {
        return (npcsAllowed != null) && npcsAllowed.contains(npcId);
    }
}
