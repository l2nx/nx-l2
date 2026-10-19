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
package app.l2nx.highfive.entity.item;

import app.l2nx.highfive.entity.item.holders.ExtractableProduct;
import app.l2nx.highfive.entity.item.type.ActionType;
import app.l2nx.highfive.entity.item.type.EtcItemType;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is dedicated to the management of EtcItem.
 */
@Slf4j
public class EtcItem extends ItemTemplate {
    @Getter
    private String handlerName;

    @Getter
    private EtcItemType itemType;

    @Getter
    private List<ExtractableProduct> extractableItems;

    @Getter
    private int extractableCountMin;

    @Getter
    private int extractableCountMax;

    @Getter
    private boolean blessed;

    /**
     * Constructor for EtcItem.
     * @param set StatSet designating the set of couples (key,value) for description of the Etc
     */
    public EtcItem(StatSet set) {
        super(set);
    }

    @Override
    public void set(StatSet set) {
        super.set(set);
        itemType = set.getEnum("etcitem_type", EtcItemType.class, EtcItemType.NONE);

        // l2j custom - EtcItemType.SHOT
        switch (getDefaultAction()) {
            case SOULSHOT:
            case SUMMON_SOULSHOT:
            case SUMMON_SPIRITSHOT:
            case SPIRITSHOT: {
                itemType = EtcItemType.SHOT;
                break;
            }
        }

        type1 = ItemTemplate.TYPE1_ITEM_QUESTITEM_ADENA;
        type2 = ItemTemplate.TYPE2_OTHER; // default is other

        if (isQuestItem()) {
            type2 = ItemTemplate.TYPE2_QUEST;
        } else if ((getId() == Inventory.ADENA_ID) || (getId() == Inventory.ANCIENT_ADENA_ID)) {
            type2 = ItemTemplate.TYPE2_MONEY;
        }

        handlerName = set.getString("handler", null); // ! null !

        extractableCountMin = set.getInt("extractableCountMin", 0);
        extractableCountMax = set.getInt("extractableCountMax", 0);
        if (extractableCountMin > extractableCountMax) {
            log.warn("Item {} extractableCountMin is bigger than extractableCountMax!", this);
        }

        blessed = set.getBoolean("blessed", false)
                || (((getDefaultAction() == ActionType.SPIRITSHOT) || (getDefaultAction() == ActionType.SOULSHOT))
                        && (getName() != null)
                        && getName().contains("Blessed"));
    }

    /**
     * @return the ID of the Etc item after applying the mask.
     */
    @Override
    public int getItemMask() {
        return itemType.mask();
    }

    /**
     * @return {@code true} if the item is an etc item, {@code false} otherwise.
     */
    @Override
    public boolean isEtcItem() {
        return true;
    }

    /**
     * @param extractableProduct
     */
    @Override
    public void addCapsuledItem(ExtractableProduct extractableProduct) {
        if (extractableItems == null) {
            extractableItems = new ArrayList<>();
        }

        extractableItems.add(extractableProduct);
    }
}
