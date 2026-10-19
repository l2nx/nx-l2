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
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.holders.RangeChanceHolder;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enchant.EnchantItemGroup;
import app.l2nx.highfive.entity.item.enchant.EnchantRateItem;
import app.l2nx.highfive.entity.item.enchant.EnchantScrollGroup;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author UnAfraid
 */
@Slf4j
public class EnchantItemGroupsData implements IXmlReader {
    private final Map<String, EnchantItemGroup> itemGroups = new HashMap<>();
    private final Map<Integer, EnchantScrollGroup> scrollGroups = new HashMap<>();

    @Getter
    private int maxWeaponEnchant = 0;

    @Getter
    private int maxArmorEnchant = 0;

    @Getter
    private int maxAccessoryEnchant = 0;

    protected EnchantItemGroupsData() {
        load();
    }

    @Override
    public synchronized void load() {
        itemGroups.clear();
        scrollGroups.clear();
        parseDatapackFile("data/EnchantItemGroups.xml");
        log.info("Loaded {} item group templates.", itemGroups.size());
        log.info("Loaded {} scroll group templates.", scrollGroups.size());

        if (PlayerConfig.OVER_ENCHANT_PROTECTION) {
            log.info("Max weapon enchant is set to {}.", maxWeaponEnchant);
            log.info("Max armor enchant is set to {}.", maxArmorEnchant);
            log.info("Max accessory enchant is set to {}.", maxAccessoryEnchant);
        }
    }

    @Override
    public void parseDocument(Document document, File file) {
        for (Node n = document.getFirstChild(); n != null; n = n.getNextSibling()) {
            if ("list".equalsIgnoreCase(n.getNodeName())) {
                for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                    if ("enchantRateGroup".equalsIgnoreCase(d.getNodeName())) {
                        final String name = parseString(d.getAttributes(), "name");
                        final EnchantItemGroup group = new EnchantItemGroup(name);
                        for (Node cd = d.getFirstChild(); cd != null; cd = cd.getNextSibling()) {
                            if ("current".equalsIgnoreCase(cd.getNodeName())) {
                                final String range = parseString(cd.getAttributes(), "enchant");
                                final double chance = parseDouble(cd.getAttributes(), "chance");
                                int min = -1;
                                int max = 0;
                                if (range.contains("-")) {
                                    final String[] split = range.split("-");
                                    if ((split.length == 2)
                                            && StringUtils.isNumeric(split[0])
                                            && StringUtils.isNumeric(split[1])) {
                                        min = Integer.parseInt(split[0]);
                                        max = Integer.parseInt(split[1]);
                                    }
                                } else if (StringUtils.isNumeric(range)) {
                                    min = Integer.parseInt(range);
                                    max = min;
                                }

                                if ((min > -1) && (max > -1)) {
                                    group.addChance(RangeChanceHolder.builder()
                                            .min(min)
                                            .max(max)
                                            .chance(chance)
                                            .build());
                                }

                                // Try to get a generic max value.
                                if (chance > 0) {
                                    if (name.contains("WEAPON")) {
                                        if (maxWeaponEnchant < max) {
                                            maxWeaponEnchant = max;
                                        }
                                    } else if (name.contains("ACCESSORIES")
                                            || name.contains("RING")
                                            || name.contains("EARRING")
                                            || name.contains("NECK")) {
                                        if (maxAccessoryEnchant < max) {
                                            maxAccessoryEnchant = max;
                                        }
                                    } else if (maxArmorEnchant < max) {
                                        maxArmorEnchant = max;
                                    }
                                }
                            }
                        }

                        itemGroups.put(name, group);
                    } else if ("enchantScrollGroup".equals(d.getNodeName())) {
                        final int id = parseInteger(d.getAttributes(), "id");
                        final EnchantScrollGroup group = new EnchantScrollGroup(id);
                        for (Node cd = d.getFirstChild(); cd != null; cd = cd.getNextSibling()) {
                            if ("enchantRate".equalsIgnoreCase(cd.getNodeName())) {
                                final EnchantRateItem rateGroup =
                                        new EnchantRateItem(parseString(cd.getAttributes(), "group"));
                                for (Node z = cd.getFirstChild(); z != null; z = z.getNextSibling()) {
                                    if ("item".equals(z.getNodeName())) {
                                        final NamedNodeMap attrs = z.getAttributes();
                                        if (attrs.getNamedItem("slot") != null) {
                                            rateGroup.addSlot(BodyPart.fromName(parseString(attrs, "slot"))
                                                    .getMask());
                                        }

                                        if (attrs.getNamedItem("magicWeapon") != null) {
                                            rateGroup.setMagicWeapon(parseBoolean(attrs, "magicWeapon"));
                                        }

                                        if (attrs.getNamedItem("itemId") != null) {
                                            rateGroup.addItemId(parseInteger(attrs, "itemId"));
                                        }
                                    }
                                }

                                group.addRateGroup(rateGroup);
                            }
                        }

                        scrollGroups.put(id, group);
                    }
                }
            }
        }

        // In case there is no accessories group set.
        if (maxAccessoryEnchant == 0) {
            maxAccessoryEnchant = maxArmorEnchant;
        }

        // Max enchant values are set to current max enchant + 1.
        maxWeaponEnchant += 1;
        maxArmorEnchant += 1;
        maxAccessoryEnchant += 1;
    }

    /**
     * Retrieves the enchant item group associated with the specified item template and scroll group.
     * @param item the {@link ItemTemplate} representing the item for which the enchant group is requested.
     * @param scrollGroup the scroll group ID to match with the item's group.
     * @return the {@link EnchantItemGroup} associated with the item and scroll group, or {@code null} if not found.
     */
    public EnchantItemGroup getItemGroup(ItemTemplate item, int scrollGroup) {
        final EnchantScrollGroup group = scrollGroups.get(scrollGroup);
        final EnchantRateItem rateGroup = group.getRateGroup(item);
        return rateGroup != null ? itemGroups.get(rateGroup.getName()) : null;
    }

    /**
     * Retrieves the enchant item group by its name.
     * @param name the name of the enchant item group.
     * @return the {@link EnchantItemGroup} associated with the specified name, or {@code null} if not found.
     */
    public EnchantItemGroup getItemGroup(String name) {
        return itemGroups.get(name);
    }

    /**
     * Retrieves the enchant scroll group by its ID.
     * @param id the ID of the scroll group.
     * @return the {@link EnchantScrollGroup} associated with the specified ID, or {@code null} if not found.
     */
    public EnchantScrollGroup getScrollGroup(int id) {
        return scrollGroups.get(id);
    }

    public static EnchantItemGroupsData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final EnchantItemGroupsData INSTANCE = new EnchantItemGroupsData();
    }
}
