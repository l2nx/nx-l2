/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.mechanics.buylist.BuyListHolder;
import app.l2nx.highfive.mechanics.buylist.Product;
import app.l2nx.highfive.repository.item.BuylistStockRepository;
import app.l2nx.highfive.repository.item.model.BuylistStock;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Loads buy lists for NPCs.
 * @author NosBit
 */
@Slf4j
@Component
public class BuyListData implements IXmlReader {

    private static final Pattern XML_FILE_PATTERN = Pattern.compile("\\d+\\.xml");

    private static volatile BuyListData instance;

    private final Map<Integer, BuyListHolder> buyLists = new ConcurrentHashMap<>();

    private final BuylistStockRepository buylistStockRepository;

    public BuyListData(BuylistStockRepository buylistStockRepository) {
        this.buylistStockRepository = buylistStockRepository;
        instance = this;
    }

    @Override
    public synchronized void load() {
        buyLists.clear();
        parseDatapackDirectory("data/buylists", false);
        if (GeneralConfig.CUSTOM_BUYLIST_LOAD) {
            parseDatapackDirectory("data/buylists/custom", false);
        }

        log.info("Loaded {} buyLists.", buyLists.size());

        try {
            for (BuylistStock stock : buylistStockRepository.findAll()) {
                final int buyListId = stock.buyListId();
                final BuyListHolder buyList = getBuyList(buyListId);
                if (buyList == null) {
                    log.warn("BuyList found in database but not loaded from xml! BuyListId: {}", buyListId);
                    continue;
                }

                final int itemId = stock.itemId();
                final Product product = buyList.getProductByItemId(itemId);
                if (product == null) {
                    log.warn(
                            "ItemId found in database but not loaded from xml! BuyListId: {} ItemId: {}",
                            buyListId,
                            itemId);
                    continue;
                }

                final long count = stock.count();
                if (count < product.getMaxCount()) {
                    product.setCount(count);
                    product.restartRestockTask(epochMillisOrZero(stock.nextRestockTime()));
                }
            }
        } catch (DataAccessException e) {
            log.warn("Failed to load buyList data from database.", e);
        }
    }

    public void saveStock(BuylistStock stock) {
        buylistStockRepository.save(stock);
    }

    @Override
    public void parseDocument(Document document, File file) {
        try {
            final int buyListId = Integer.parseInt(file.getName().replace(".xml", ""));
            for (Node node = document.getFirstChild(); node != null; node = node.getNextSibling()) {
                if ("list".equalsIgnoreCase(node.getNodeName())) {
                    final BuyListHolder buyList = new BuyListHolder(buyListId);
                    for (Node list_node = node.getFirstChild();
                            list_node != null;
                            list_node = list_node.getNextSibling()) {
                        if ("item".equalsIgnoreCase(list_node.getNodeName())) {
                            int itemId = -1;
                            long price = -1;
                            long restockDelay = -1;
                            long count = -1;
                            final NamedNodeMap attrs = list_node.getAttributes();
                            Node attr = attrs.getNamedItem("id");
                            itemId = Integer.parseInt(attr.getNodeValue());
                            attr = attrs.getNamedItem("price");
                            if (attr != null) {
                                price = Long.parseLong(attr.getNodeValue());
                            }

                            attr = attrs.getNamedItem("restock_delay");
                            if (attr != null) {
                                restockDelay = Long.parseLong(attr.getNodeValue());
                            }

                            attr = attrs.getNamedItem("count");
                            if (attr != null) {
                                count = Long.parseLong(attr.getNodeValue());
                            }

                            final ItemTemplate item = ItemData.getInstance().getTemplate(itemId);
                            if (item != null) {
                                final int sellPrice = item.getReferencePrice() / 2;
                                if (GeneralConfig.CORRECT_PRICES
                                        && (price > -1)
                                        && (sellPrice > price)
                                        && (buyList.getNpcsAllowed() != null)) {
                                    log.warn(
                                            "Buy price {} is less than sell price {} for ItemID:{} of buylist {}.",
                                            price,
                                            sellPrice,
                                            itemId,
                                            buyList.getListId());
                                    buyList.addProduct(
                                            new Product(buyList.getListId(), item, sellPrice, restockDelay, count));
                                } else {
                                    buyList.addProduct(
                                            new Product(buyList.getListId(), item, price, restockDelay, count));
                                }
                            } else {
                                log.warn(
                                        "Item not found. BuyList:{} ItemID:{} File:{}",
                                        buyList.getListId(),
                                        itemId,
                                        file.getName());
                            }
                        } else if ("npcs".equalsIgnoreCase(list_node.getNodeName())) {
                            for (Node npcs_node = list_node.getFirstChild();
                                    npcs_node != null;
                                    npcs_node = npcs_node.getNextSibling()) {
                                if ("npc".equalsIgnoreCase(npcs_node.getNodeName())) {
                                    buyList.addAllowedNpc(Integer.parseInt(npcs_node.getTextContent()));
                                }
                            }
                        }
                    }

                    buyLists.put(buyList.getListId(), buyList);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load buyList data from xml File:{}", file.getName(), e);
        }
    }

    @Override
    public boolean isValidXmlFile(File file) {
        return (file != null)
                && file.isFile()
                && XML_FILE_PATTERN.matcher(file.getName().toLowerCase()).matches();
    }

    public BuyListHolder getBuyList(int listId) {
        return buyLists.get(listId);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static BuyListData getInstance() {
        final BuyListData current = instance;
        if (current == null) {
            throw new IllegalStateException("BuyListData is used before the Spring context created it.");
        }
        return current;
    }
}
