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
package app.l2nx.highfive.data;

import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.entity.actor.instance.Merchant;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * @author KenM
 */
@Slf4j
public class MerchantPriceConfigTable {

    public static MerchantPriceConfigTable getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static final String MPCS_FILE = "MerchantPriceConfig.xml";

    private final Map<Integer, MerchantPriceConfig> mpcs = new HashMap<>();
    private MerchantPriceConfig defaultMpc;

    public MerchantPriceConfig getMerchantPriceConfig(Merchant npc) {
        for (MerchantPriceConfig mpc : mpcs.values()) {
            if (ZoneManager.getInstance().getRegion(npc).getZones().containsKey(mpc.getZoneId())) {
                return mpc;
            }
        }

        return defaultMpc;
    }

    public MerchantPriceConfig getMerchantPriceConfig(int id) {
        return mpcs.get(id);
    }

    public void loadXML() throws SAXException, IOException, ParserConfigurationException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setIgnoringComments(true);
        final File file = new File(ServerConfig.DATAPACK_ROOT + "/data/" + MPCS_FILE);
        if (file.exists()) {
            int defaultPriceConfigId;
            final Document document = factory.newDocumentBuilder().parse(file);

            Node n = document.getDocumentElement();
            final Node dpcNode = n.getAttributes().getNamedItem("defaultPriceConfig");
            if (dpcNode == null) {
                throw new IllegalStateException("merchantPriceConfig must define an 'defaultPriceConfig'");
            }

            defaultPriceConfigId = Integer.parseInt(dpcNode.getNodeValue());

            MerchantPriceConfig mpc;
            for (n = n.getFirstChild(); n != null; n = n.getNextSibling()) {
                mpc = parseMerchantPriceConfig(n);
                if (mpc != null) {
                    mpcs.put(mpc.getId(), mpc);
                }
            }

            final MerchantPriceConfig defaultMpc = getMerchantPriceConfig(defaultPriceConfigId);
            if (defaultMpc == null) {
                throw new IllegalStateException("'defaultPriceConfig' points to an non-loaded priceConfig");
            }

            this.defaultMpc = defaultMpc;
        }
    }

    private MerchantPriceConfig parseMerchantPriceConfig(Node n) {
        if (n.getNodeName().equals("priceConfig")) {
            final int id;
            final int baseTax;
            int castleId = -1;
            int zoneId = -1;
            final String name;

            Node node = n.getAttributes().getNamedItem("id");
            if (node == null) {
                throw new IllegalStateException("Must define the priceConfig 'id'");
            }

            id = Integer.parseInt(node.getNodeValue());

            node = n.getAttributes().getNamedItem("name");
            if (node == null) {
                throw new IllegalStateException("Must define the priceConfig 'name'");
            }

            name = node.getNodeValue();

            node = n.getAttributes().getNamedItem("baseTax");
            if (node == null) {
                throw new IllegalStateException("Must define the priceConfig 'baseTax'");
            }

            baseTax = Integer.parseInt(node.getNodeValue());

            node = n.getAttributes().getNamedItem("castleId");
            if (node != null) {
                castleId = Integer.parseInt(node.getNodeValue());
            }

            node = n.getAttributes().getNamedItem("zoneId");
            if (node != null) {
                zoneId = Integer.parseInt(node.getNodeValue());
            }

            return new MerchantPriceConfig(id, name, baseTax, castleId, zoneId);
        }

        return null;
    }

    public void loadInstances() {
        try {
            loadXML();
            log.info("Loaded {} merchant price configs.", mpcs.size());
        } catch (Exception e) {
            log.error("Failed loading MerchantPriceConfigTable. Reason: {}", e.getMessage(), e);
        }
    }

    public void updateReferences() {
        for (MerchantPriceConfig mpc : mpcs.values()) {
            mpc.updateReferences();
        }
    }

    public static class MerchantPriceConfig {
        @Getter
        private final int id;

        @Getter
        private final String name;

        @Getter
        private final int baseTax;

        private final int castleId;
        private Castle castle;

        @Getter
        private final int zoneId;

        public MerchantPriceConfig(int id, String name, int baseTax, int castleId, int zoneId) {
            this.id = id;
            this.name = name;
            this.baseTax = baseTax;
            this.castleId = castleId;
            this.zoneId = zoneId;
        }

        /**
         * @return Returns the baseTax / 100.0.
         */
        public double getBaseTaxRate() {
            return baseTax / 100.0;
        }

        /**
         * @return Returns the castle.
         */
        public Castle getCastle() {
            return castle;
        }

        public boolean hasCastle() {
            return castle != null;
        }

        public double getCastleTaxRate() {
            return hasCastle() ? castle.getTaxRate() : 0.0;
        }

        public int getTotalTax() {
            return hasCastle() ? (castle.getTaxPercent() + baseTax) : baseTax;
        }

        public double getTotalTaxRate() {
            return getTotalTax() / 100.0;
        }

        public void updateReferences() {
            if (castleId > 0) {
                castle = CastleManager.getInstance().getCastleById(castleId);
            }
        }
    }

    private static class SingletonHolder {
        protected static final MerchantPriceConfigTable INSTANCE = new MerchantPriceConfigTable();
    }
}
