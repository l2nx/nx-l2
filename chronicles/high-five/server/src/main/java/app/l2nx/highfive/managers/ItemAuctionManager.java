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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.entity.item.auction.ItemAuctionInstance;
import app.l2nx.highfive.entity.item.auction.ItemAuctionState;
import app.l2nx.highfive.repository.auction.ItemAuctionRepository;
import app.l2nx.highfive.repository.auction.model.ItemAuctionBidRow;
import app.l2nx.highfive.repository.auction.model.ItemAuctionRow;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author Forsaiken
 */
@Slf4j
@Component
public class ItemAuctionManager {
    private static volatile ItemAuctionManager instance;

    private final Map<Integer, ItemAuctionInstance> managerInstances = new HashMap<>();

    private final ItemAuctionRepository itemAuctionRepository;

    public ItemAuctionManager(ItemAuctionRepository itemAuctionRepository) {
        this.itemAuctionRepository = itemAuctionRepository;
        instance = this;
    }

    public void load() {
        if (!GeneralConfig.ALT_ITEM_AUCTION_ENABLED) {
            log.info("Disabled.");
            return;
        }

        final File file = new File(ServerConfig.DATAPACK_ROOT, "data/ItemAuctions.xml");
        if (!file.exists()) {
            log.warn("Missing ItemAuctions.xml!");
            return;
        }

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setIgnoringComments(true);

        try {
            final Document document = factory.newDocumentBuilder().parse(file);
            for (Node na = document.getFirstChild(); na != null; na = na.getNextSibling()) {
                if ("list".equalsIgnoreCase(na.getNodeName())) {
                    for (Node nb = na.getFirstChild(); nb != null; nb = nb.getNextSibling()) {
                        if ("instance".equalsIgnoreCase(nb.getNodeName())) {
                            final NamedNodeMap nab = nb.getAttributes();
                            final int instanceId =
                                    Integer.parseInt(nab.getNamedItem("id").getNodeValue());

                            if (managerInstances.containsKey(instanceId)) {
                                throw new Exception("Dublicated instanceId " + instanceId);
                            }

                            managerInstances.put(instanceId, new ItemAuctionInstance(instanceId, nb));
                        }
                    }
                }
            }

            log.info("Loaded {} instance(s).", managerInstances.size());
        } catch (Exception e) {
            log.error("Failed loading auctions from xml.", e);
        }
    }

    public void shutdown() {
        for (ItemAuctionInstance instance : managerInstances.values()) {
            instance.shutdown();
        }
    }

    public ItemAuctionInstance getManagerInstance(int instanceId) {
        return managerInstances.get(instanceId);
    }

    public List<Integer> findAuctionIds(int instanceId) {
        return itemAuctionRepository.findIdsByInstance(instanceId);
    }

    public Optional<ItemAuctionRow> findAuction(int auctionId) {
        return itemAuctionRepository.find(auctionId);
    }

    public List<ItemAuctionBidRow> findBids(int auctionId) {
        return itemAuctionRepository.findBids(auctionId);
    }

    public int createAuction(ItemAuctionRow auction) {
        return itemAuctionRepository.create(auction);
    }

    public void updateAuctionState(int auctionId, ItemAuctionState state) {
        try {
            itemAuctionRepository.updateState(auctionId, state);
        } catch (DataAccessException e) {
            log.warn("Failed storing the state of auction {}:", auctionId, e);
        }
    }

    public void saveBid(int auctionId, ItemAuctionBidRow bid) {
        try {
            itemAuctionRepository.saveBid(auctionId, bid);
        } catch (DataAccessException e) {
            log.warn("Failed storing a bid of auction {}:", auctionId, e);
        }
    }

    public void deleteBid(int auctionId, int characterId) {
        try {
            itemAuctionRepository.deleteBid(auctionId, characterId);
        } catch (DataAccessException e) {
            log.warn("Failed deleting a bid of auction {}:", auctionId, e);
        }
    }

    public void deleteAuction(int auctionId) {
        try {
            itemAuctionRepository.delete(auctionId);
        } catch (DataAccessException e) {
            log.error("ItemAuctionManagerInstance: Failed deleting auction: {}", auctionId, e);
        }
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static ItemAuctionManager getInstance() {
        final ItemAuctionManager current = instance;
        if (current == null) {
            throw new IllegalStateException("ItemAuctionManager is used before the Spring context created it.");
        }
        return current;
    }
}
