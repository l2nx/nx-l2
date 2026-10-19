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
package app.l2nx.highfive.entity.item.enchant;

import app.l2nx.highfive.data.xml.EnchantItemData;
import app.l2nx.highfive.data.xml.EnchantItemGroupsData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.type.EtcItemType;
import app.l2nx.highfive.entity.item.type.ItemType;
import app.l2nx.highfive.util.StatSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author UnAfraid, Mobius
 */
@Slf4j
public class EnchantScroll extends AbstractEnchantItem {
    @Getter
    private final boolean weapon;

    @Getter
    private final boolean blessed;

    @Getter
    private final boolean safe;

    private final int scrollGroupId;
    private final Map<Integer, Integer> items = new HashMap<>();

    public EnchantScroll(StatSet set) {
        super(set);
        scrollGroupId = set.getInt("scrollGroupId", 0);

        final ItemType type = getItem().getItemType();
        weapon = (type == EtcItemType.ANCIENT_CRYSTAL_ENCHANT_WP)
                || (type == EtcItemType.BLESS_SCRL_ENCHANT_WP)
                || (type == EtcItemType.SCRL_ENCHANT_WP);
        blessed = (type == EtcItemType.BLESS_SCRL_ENCHANT_AM) || (type == EtcItemType.BLESS_SCRL_ENCHANT_WP);
        safe = (type == EtcItemType.ANCIENT_CRYSTAL_ENCHANT_AM) || (type == EtcItemType.ANCIENT_CRYSTAL_ENCHANT_WP);
    }

    /**
     * Enforces current scroll to use only those items as possible items to enchant
     * @param itemId
     * @param scrollGroupId
     */
    public void addItem(int itemId, int scrollGroupId) {
        items.put(itemId, scrollGroupId > -1 ? scrollGroupId : this.scrollGroupId);
    }

    public Collection<Integer> getItems() {
        return items.keySet();
    }

    /**
     * @param itemToEnchant the item to be enchanted
     * @param supportItem the support item used when enchanting (can be null)
     * @return {@code true} if this scroll can be used with the specified support item and the item to be enchanted, {@code false} otherwise
     */
    @Override
    public boolean isValid(Item itemToEnchant, EnchantSupportItem supportItem) {
        if (!items.isEmpty() && !items.containsKey(itemToEnchant.getId())) {
            return false;
        } else if (supportItem != null) {
            if (blessed) {
                return false;
            } else if (!supportItem.isValid(itemToEnchant, supportItem)) {
                return false;
            } else if (supportItem.isWeapon() != isWeapon()) {
                return false;
            }
        }

        if (items.isEmpty()) {
            for (EnchantScroll scroll : EnchantItemData.getInstance().getScrolls()) {
                if (scroll.getId() == getId()) {
                    continue;
                }

                final Collection<Integer> scrollItems = scroll.getItems();
                if (!scrollItems.isEmpty() && scrollItems.contains(itemToEnchant.getId())) {
                    return false;
                }
            }
        }

        return super.isValid(itemToEnchant, supportItem);
    }

    /**
     * @param player
     * @param enchantItem
     * @return the chance of current scroll's group.
     */
    public double getChance(Player player, Item enchantItem) {
        if (enchantItem == null) {
            return -1;
        }

        final int scrollGroupId = items.getOrDefault(enchantItem.getId(), this.scrollGroupId);
        if (EnchantItemGroupsData.getInstance().getScrollGroup(scrollGroupId) == null) {
            log.warn("Unexistent enchant scroll group specified for enchant scroll: {}", getId());
            return -1;
        }

        final EnchantItemGroup group =
                EnchantItemGroupsData.getInstance().getItemGroup(enchantItem.getTemplate(), scrollGroupId);
        if (group == null) {
            log.warn("Couldn't find enchant item group for scroll: {} requested by: {}", getId(), player);
            return -1;
        }

        return group.getChance(enchantItem.getEnchantLevel());
    }

    /**
     * @param player
     * @param enchantItem
     * @param supportItem
     * @return the total chance for success rate of this scroll
     */
    public EnchantResultType calculateSuccess(Player player, Item enchantItem, EnchantSupportItem supportItem) {
        if (!isValid(enchantItem, supportItem)) {
            return EnchantResultType.ERROR;
        }

        final double chance = getChance(player, enchantItem);
        if (chance == -1) {
            return EnchantResultType.ERROR;
        }

        final double bonusRate = getBonusRate();
        final double supportBonusRate = (supportItem != null) ? supportItem.getBonusRate() : 0;
        final double finalChance = Math.min(chance + bonusRate + supportBonusRate, 100);
        final double random = 100 * ThreadLocalRandom.current().nextDouble();
        final boolean success = (random < finalChance);
        return success ? EnchantResultType.SUCCESS : EnchantResultType.FAILURE;
    }
}
