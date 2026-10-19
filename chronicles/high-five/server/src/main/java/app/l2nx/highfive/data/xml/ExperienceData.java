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
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.config.PlayerConfig;
import java.io.File;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;

/**
 * This class holds the Experience points for each level for players and pets.
 * <p>
 * It provides functionality to load experience data from an XML file and retrieve the experience points required for a given level. It also maintains the maximum levels for players and pets.
 * </p>
 * <p>
 * <strong>XML Structure:</strong>
 * </p>
 * The XML file must have a root element containing the `maxLevel` and `maxPetLevel` attributes, along with nested `experience` elements that define the experience points for each level.
 * @author Mobius
 */
@Slf4j
public class ExperienceData implements IXmlReader {

    private long[] expTable;

    @Getter
    private byte maxLevel;

    @Getter
    private byte maxPetLevel;

    protected ExperienceData() {
        load();
    }

    @Override
    public void load() {
        parseDatapackFile("data/stats/players/experience.xml");
        log.info("Loaded {} levels.", (expTable.length - 1));
        log.info("Max Player Level is {}.", (maxLevel - 1));
        log.info("Max Pet Level is {}.", (maxPetLevel - 1));
    }

    @Override
    public void parseDocument(Document document, File file) {
        forEach(document, tableNode -> {
            final NamedNodeMap tableAttr = tableNode.getAttributes();
            maxLevel = (byte) (Byte.parseByte(tableAttr.getNamedItem("maxLevel").getNodeValue()) + 1);
            maxPetLevel =
                    (byte) (Byte.parseByte(tableAttr.getNamedItem("maxPetLevel").getNodeValue()) + 1);
            if (maxLevel > PlayerConfig.PLAYER_MAXIMUM_LEVEL) {
                maxLevel = PlayerConfig.PLAYER_MAXIMUM_LEVEL;
            }

            if (maxPetLevel > (maxLevel + 1)) {
                maxPetLevel = (byte) (maxLevel + 1); // Pet level should not exceed owner level.
            }

            // Initialize the array with size maxLevel + 1 (level 0 is unused).
            expTable = new long[maxLevel + 1];

            forEach(tableNode, "experience", experienceNode -> {
                final NamedNodeMap attrs = experienceNode.getAttributes();
                final int level = parseInteger(attrs, "level");
                if ((level > PlayerConfig.PLAYER_MAXIMUM_LEVEL) || (level > maxLevel)) {
                    return;
                }

                expTable[level] = parseLong(attrs, "tolevel");
            });
        });
    }

    /**
     * Retrieves the experience points required to reach the specified level.
     * @param level the level for which experience points are required
     * @return the experience points needed to reach the specified level; if the specified level exceeds {@code Config.PLAYER_MAXIMUM_LEVEL}, returns the experience points for {@code Config.PLAYER_MAXIMUM_LEVEL}
     */
    public long getExpForLevel(int level) {
        if (level > PlayerConfig.PLAYER_MAXIMUM_LEVEL) {
            return expTable[PlayerConfig.PLAYER_MAXIMUM_LEVEL];
        }

        return expTable[level];
    }

    public static ExperienceData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final ExperienceData INSTANCE = new ExperienceData();
    }
}
