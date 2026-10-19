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
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * @author DS
 */
public class Entry {
    @Getter
    @Setter
    protected int entryId;

    @Getter
    protected boolean stackable = true;

    @Getter
    protected List<Ingredient> products;

    @Getter
    protected List<Ingredient> ingredients;

    public Entry(int entryId) {
        this.entryId = entryId;
        products = new ArrayList<>();
        ingredients = new ArrayList<>();
    }

    /**
     * This constructor used in PreparedEntry only, ArrayLists not created.
     */
    protected Entry() {}

    public void addProduct(Ingredient product) {
        products.add(product);

        if (!product.isStackable()) {
            stackable = false;
        }
    }

    public void addIngredient(Ingredient ingredient) {
        ingredients.add(ingredient);
    }

    public long getTaxAmount() {
        return 0;
    }
}
