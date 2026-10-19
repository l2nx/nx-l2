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
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.item.auction.ItemAuctionInstance;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author Forsaiken
 */
@Slf4j
public class ItemAuctionManager {

    private final Map<Integer, ItemAuctionInstance> managerInstances = new HashMap<>();
    private final AtomicInteger auctionIds;

    protected ItemAuctionManager() {
        auctionIds = new AtomicInteger(1);

        if (!GeneralConfig.ALT_ITEM_AUCTION_ENABLED) {
            log.info("Disabled.");
            return;
        }

        try (Connection con = DatabaseFactory.getConnection();
                Statement s = con.createStatement();
                ResultSet rs =
                        s.executeQuery("SELECT auctionId FROM item_auction ORDER BY auctionId DESC LIMIT 0, 1")) {
            if (rs.next()) {
                auctionIds.set(rs.getInt(1) + 1);
            }
        } catch (SQLException e) {
            log.error("Failed loading auctions.", e);
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

                            managerInstances.put(instanceId, new ItemAuctionInstance(instanceId, auctionIds, nb));
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

    public int getNextAuctionId() {
        return auctionIds.getAndIncrement();
    }

    public static void deleteAuction(int auctionId) {
        try (Connection con = DatabaseFactory.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM item_auction WHERE auctionId=?")) {
                ps.setInt(1, auctionId);
                ps.execute();
            }

            try (PreparedStatement ps = con.prepareStatement("DELETE FROM item_auction_bid WHERE auctionId=?")) {
                ps.setInt(1, auctionId);
                ps.execute();
            }
        } catch (SQLException e) {
            log.error("ItemAuctionManagerInstance: Failed deleting auction: {}", auctionId, e);
        }
    }

    /**
     * Gets the single instance of {@code ItemAuctionManager}.
     * @return single instance of {@code ItemAuctionManager}
     */
    public static ItemAuctionManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final ItemAuctionManager INSTANCE = new ItemAuctionManager();
    }
}
