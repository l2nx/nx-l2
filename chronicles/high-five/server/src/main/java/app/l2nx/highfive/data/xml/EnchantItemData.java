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

import app.l2nx.highfive.entity.item.enchant.EnchantScroll;
import app.l2nx.highfive.entity.item.enchant.EnchantSupportItem;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.util.StatSet;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author UnAfraid
 */
@Slf4j
public class EnchantItemData implements IXmlReader {
    private final Map<Integer, EnchantScroll> scrolls = new HashMap<>();
    private final Map<Integer, EnchantSupportItem> supports = new HashMap<>();

    public EnchantItemData() {
        load();
    }

    @Override
    public synchronized void load() {
        scrolls.clear();
        supports.clear();
        parseDatapackFile("data/EnchantItemData.xml");
        log.info("Loaded {} enchant scrolls.", scrolls.size());
        log.info("Loaded {} support items.", supports.size());
    }

    @Override
    public void parseDocument(Document document, File file) {
        StatSet set;
        Node att;
        NamedNodeMap attrs;
        for (Node n = document.getFirstChild(); n != null; n = n.getNextSibling()) {
            if ("list".equalsIgnoreCase(n.getNodeName())) {
                for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                    if ("enchant".equalsIgnoreCase(d.getNodeName())) {
                        attrs = d.getAttributes();
                        set = new StatSet();
                        for (int i = 0; i < attrs.getLength(); i++) {
                            att = attrs.item(i);
                            set.set(att.getNodeName(), att.getNodeValue());
                        }

                        try {
                            final EnchantScroll item = new EnchantScroll(set);
                            for (Node cd = d.getFirstChild(); cd != null; cd = cd.getNextSibling()) {
                                if ("item".equalsIgnoreCase(cd.getNodeName())) {
                                    item.addItem(
                                            parseInteger(cd.getAttributes(), "id"),
                                            parseInteger(cd.getAttributes(), "altScrollGroupId", -1));
                                }
                            }

                            scrolls.put(item.getId(), item);
                        } catch (NullPointerException e) {
                            log.warn("Unexistent enchant scroll: {} defined in enchant data!", set.getString("id"));
                        } catch (IllegalAccessError e) {
                            log.warn(
                                    "Wrong enchant scroll item type: {} defined in enchant data!", set.getString("id"));
                        }
                    } else if ("support".equalsIgnoreCase(d.getNodeName())) {
                        attrs = d.getAttributes();
                        set = new StatSet();
                        for (int i = 0; i < attrs.getLength(); i++) {
                            att = attrs.item(i);
                            set.set(att.getNodeName(), att.getNodeValue());
                        }

                        try {
                            final EnchantSupportItem item = new EnchantSupportItem(set);
                            supports.put(item.getId(), item);
                        } catch (NullPointerException e) {
                            log.warn(
                                    "Unexistent enchant support item: {} defined in enchant data!",
                                    set.getString("id"));
                        } catch (IllegalAccessError e) {
                            log.warn(
                                    "Wrong enchant support item type: {} defined in enchant data!",
                                    set.getString("id"));
                        }
                    }
                }
            }
        }
    }

    /**
     * Retrieves all registered enchant scrolls.
     * @return a collection of all {@link EnchantScroll} instances currently stored.
     */
    public Collection<EnchantScroll> getScrolls() {
        return scrolls.values();
    }

    /**
     * Retrieves the enchant scroll template associated with the specified item.
     * @param item the {@link Item} representing the scroll to retrieve.
     * @return the {@link EnchantScroll} template for the specified scroll item, or {@code null} if the item is not found.
     */
    public EnchantScroll getEnchantScroll(Item item) {
        if (item == null) {
            return null;
        }

        return scrolls.get(item.getId());
    }

    /**
     * Retrieves the support item template associated with the specified item.
     * @param item the {@link Item} representing the support item to retrieve.
     * @return the {@link EnchantSupportItem} template for the specified support item, or {@code null} if the item is not found.
     */
    public EnchantSupportItem getSupportItem(Item item) {
        if (item == null) {
            return null;
        }

        return supports.get(item.getId());
    }

    public static EnchantItemData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final EnchantItemData INSTANCE = new EnchantItemData();
    }
}
