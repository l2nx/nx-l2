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
package app.l2nx.highfive.mechanics.stats;

import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.entity.actor.Creature;
import java.io.File;
import java.util.NoSuchElementException;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author DS
 */
@Slf4j
public enum BaseStat {
    STR(new STR()),
    INT(new INT()),
    DEX(new DEX()),
    WIT(new WIT()),
    CON(new CON()),
    MEN(new MEN()),
    NONE(new NONE());

    public static final int MAX_STAT_VALUE = 100;

    protected static final double[] STRbonus = new double[MAX_STAT_VALUE];
    protected static final double[] INTbonus = new double[MAX_STAT_VALUE];
    protected static final double[] DEXbonus = new double[MAX_STAT_VALUE];
    protected static final double[] WITbonus = new double[MAX_STAT_VALUE];
    protected static final double[] CONbonus = new double[MAX_STAT_VALUE];
    protected static final double[] MENbonus = new double[MAX_STAT_VALUE];

    private final IBaseStatFunction stat;

    public String getValue() {
        return stat.getClass().getSimpleName();
    }

    private BaseStat(IBaseStatFunction s) {
        stat = s;
    }

    public double calcBonus(Creature actor) {
        if (actor != null) {
            return stat.calcBonus(actor);
        }

        return 1;
    }

    public static BaseStat valueOfXml(String name) {
        final String internName = name.intern();
        for (BaseStat s : values()) {
            if (s.getValue().equalsIgnoreCase(internName)) {
                return s;
            }
        }

        throw new NoSuchElementException("Unknown name '" + internName + "' for enum BaseStats");
    }

    protected static class STR implements IBaseStatFunction {
        @Override
        public double calcBonus(Creature actor) {
            return STRbonus[actor.getSTR()];
        }
    }

    protected static class INT implements IBaseStatFunction {
        @Override
        public double calcBonus(Creature actor) {
            return INTbonus[actor.getINT()];
        }
    }

    protected static class DEX implements IBaseStatFunction {
        @Override
        public double calcBonus(Creature actor) {
            return DEXbonus[actor.getDEX()];
        }
    }

    protected static class WIT implements IBaseStatFunction {
        @Override
        public double calcBonus(Creature actor) {
            return WITbonus[actor.getWIT()];
        }
    }

    protected static class CON implements IBaseStatFunction {
        @Override
        public double calcBonus(Creature actor) {
            return CONbonus[actor.getCON()];
        }
    }

    protected static class MEN implements IBaseStatFunction {
        @Override
        public double calcBonus(Creature actor) {
            return MENbonus[actor.getMEN()];
        }
    }

    protected static class NONE implements IBaseStatFunction {
        @Override
        public double calcBonus(Creature actor) {
            return 1f;
        }
    }

    static {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setIgnoringComments(true);
        final File file = new File(ServerConfig.DATAPACK_ROOT, "data/stats/statBonus.xml");
        Document document = null;

        if (file.exists()) {
            try {
                document = factory.newDocumentBuilder().parse(file);
            } catch (Exception e) {
                log.warn("[BaseStats] Could not parse file: {}", e.getMessage(), e);
            }

            if (document != null) {
                String statName;
                int val;
                double bonus;
                NamedNodeMap attrs;
                for (Node list = document.getFirstChild(); list != null; list = list.getNextSibling()) {
                    if ("list".equalsIgnoreCase(list.getNodeName())) {
                        for (Node stat = list.getFirstChild(); stat != null; stat = stat.getNextSibling()) {
                            statName = stat.getNodeName();
                            for (Node value = stat.getFirstChild(); value != null; value = value.getNextSibling()) {
                                if ("stat".equalsIgnoreCase(value.getNodeName())) {
                                    attrs = value.getAttributes();
                                    try {
                                        val = Integer.parseInt(
                                                attrs.getNamedItem("value").getNodeValue());
                                        bonus = Double.parseDouble(
                                                attrs.getNamedItem("bonus").getNodeValue());
                                    } catch (Exception e) {
                                        log.error(
                                                "[BaseStats] Invalid stats value: {}, skipping", value.getNodeValue());
                                        continue;
                                    }

                                    if ("STR".equalsIgnoreCase(statName)) {
                                        STRbonus[val] = bonus;
                                    } else if ("INT".equalsIgnoreCase(statName)) {
                                        INTbonus[val] = bonus;
                                    } else if ("DEX".equalsIgnoreCase(statName)) {
                                        DEXbonus[val] = bonus;
                                    } else if ("WIT".equalsIgnoreCase(statName)) {
                                        WITbonus[val] = bonus;
                                    } else if ("CON".equalsIgnoreCase(statName)) {
                                        CONbonus[val] = bonus;
                                    } else if ("MEN".equalsIgnoreCase(statName)) {
                                        MENbonus[val] = bonus;
                                    } else {
                                        log.error("[BaseStats] Invalid stats name: {}, skipping", statName);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            throw new Error("[BaseStats] File not found: " + file.getName());
        }
    }
}
