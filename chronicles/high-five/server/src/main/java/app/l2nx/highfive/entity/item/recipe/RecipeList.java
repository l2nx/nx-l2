/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.entity.item.recipe;

import app.l2nx.highfive.data.holders.RecipeHolder;
import app.l2nx.highfive.data.holders.RecipeStatHolder;
import app.l2nx.highfive.util.StatSet;
import lombok.Getter;

/**
 * This class describes a Recipe used by Dwarf to craft Item. All RecipeList are made of RecipeHolder (1 line of the recipe : Item-Quantity needed).
 */
public class RecipeList {
    /** The table containing all RecipeHolder (1 line of the recipe : Item-Quantity needed) of the RecipeList */
    @Getter
    private RecipeHolder[] recipes;

    /** The table containing all RecipeStatHolder for the statUse parameter of the RecipeList */
    @Getter
    private RecipeStatHolder[] statUse;

    /** The table containing all RecipeStatHolder for the altStatChange parameter of the RecipeList */
    @Getter
    private RecipeStatHolder[] altStatChange;

    /** The Identifier of the Instance */
    @Getter
    private final int id;

    /** The crafting level needed to use this RecipeList */
    @Getter
    private final int level;

    /** The Identifier of the RecipeList */
    @Getter
    private final int recipeId;

    /** The name of the RecipeList */
    @Getter
    private final String recipeName;

    /** The crafting success rate when using the RecipeList */
    @Getter
    private final int successRate;

    /** The Identifier of the Item crafted with this RecipeList */
    @Getter
    private final int itemId;

    /** The quantity of Item crafted when using this RecipeList */
    @Getter
    private final int count;

    /** The Identifier of the Rare Item crafted with this RecipeList */
    @Getter
    private int rareItemId;

    /** The quantity of Rare Item crafted when using this RecipeList */
    @Getter
    private int rareCount;

    /** The chance of Rare Item crafted when using this RecipeList */
    @Getter
    private int rarity;

    /** If this a common or a dwarven recipe */
    @Getter
    private final boolean dwarvenRecipe;

    /**
     * Constructor of RecipeList (create a new Recipe).
     * @param set
     * @param haveRare
     */
    public RecipeList(StatSet set, boolean haveRare) {
        recipes = new RecipeHolder[0];
        statUse = new RecipeStatHolder[0];
        altStatChange = new RecipeStatHolder[0];
        id = set.getInt("id");
        level = set.getInt("craftLevel");
        recipeId = set.getInt("recipeId");
        recipeName = set.getString("recipeName");
        successRate = set.getInt("successRate");
        itemId = set.getInt("itemId");
        count = set.getInt("count");
        if (haveRare) {
            rareItemId = set.getInt("rareItemId");
            rareCount = set.getInt("rareCount");
            rarity = set.getInt("rarity");
        }

        dwarvenRecipe = set.getBoolean("isDwarvenRecipe");
    }

    /**
     * Add a RecipeHolder to the RecipeList (add a line Item-Quantity needed to the Recipe).
     * @param recipe
     */
    public void addRecipe(RecipeHolder recipe) {
        final int len = recipes.length;
        final RecipeHolder[] tmp = new RecipeHolder[len + 1];
        System.arraycopy(recipes, 0, tmp, 0, len);
        tmp[len] = recipe;
        recipes = tmp;
    }

    /**
     * Add a RecipeStatHolder of the statUse parameter to the RecipeList.
     * @param statUse
     */
    public void addStatUse(RecipeStatHolder statUse) {
        final int len = this.statUse.length;
        final RecipeStatHolder[] tmp = new RecipeStatHolder[len + 1];
        System.arraycopy(this.statUse, 0, tmp, 0, len);
        tmp[len] = statUse;
        this.statUse = tmp;
    }

    /**
     * Add a RecipeStatHolder of the altStatChange parameter to the RecipeList.
     * @param statChange
     */
    public void addAltStatChange(RecipeStatHolder statChange) {
        final int len = altStatChange.length;
        final RecipeStatHolder[] tmp = new RecipeStatHolder[len + 1];
        System.arraycopy(altStatChange, 0, tmp, 0, len);
        tmp[len] = statChange;
        altStatChange = tmp;
    }
}
