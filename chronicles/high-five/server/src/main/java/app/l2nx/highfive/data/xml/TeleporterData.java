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

import app.l2nx.highfive.entity.actor.enums.player.TeleportType;
import app.l2nx.highfive.entity.teleporter.TeleportHolder;
import app.l2nx.highfive.util.StatSet;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;

/**
 * @author UnAfraid
 */
@Slf4j
public class TeleporterData implements IXmlReader {

    private final Map<Integer, Map<String, TeleportHolder>> teleporters = new ConcurrentHashMap<>();

    protected TeleporterData() {
        load();
    }

    @Override
    public void load() {
        teleporters.clear();
        parseDatapackDirectory("data/teleporters", true);
        log.info("Loaded {} npc teleporters.", teleporters.size());
    }

    @Override
    public void parseDocument(Document document, File file) {
        forEach(
                document,
                "list",
                list -> forEach(list, "npc", npc -> {
                    final Map<String, TeleportHolder> teleList = new HashMap<>();

                    // Parse npc node child.
                    final int npcId = parseInteger(npc.getAttributes(), "id");
                    forEach(npc, node -> {
                        switch (node.getNodeName()) {
                            case "teleport": {
                                final NamedNodeMap nodeAttrs = node.getAttributes();

                                // Parse attributes
                                final TeleportType type = parseEnum(nodeAttrs, TeleportType.class, "type");
                                final String name = parseString(nodeAttrs, "name", type.name());

                                // Parse locations
                                final TeleportHolder holder = new TeleportHolder(name, type);
                                forEach(
                                        node,
                                        "location",
                                        location -> holder.registerLocation(new StatSet(parseAttributes(location))));

                                // Register holder
                                if (teleList.putIfAbsent(name, holder) != null) {
                                    log.warn("Duplicate teleport list ({}) has been found for NPC: {}", name, npcId);
                                }
                                break;
                            }
                            case "npcs": {
                                forEach(node, "npc", npcNode -> {
                                    final int id = parseInteger(npcNode.getAttributes(), "id");
                                    registerTeleportList(id, teleList);
                                });
                                break;
                            }
                        }
                    });
                    registerTeleportList(npcId, teleList);
                }));
    }

    /**
     * Retrieves the total count of teleporters registered in the data.
     * @return the number of teleporters available
     */
    public int getTeleporterCount() {
        return teleporters.size();
    }

    /**
     * Registers teleport data for a specific NPC to the global teleporter list.
     * <p>
     * If a teleporter with the given {@code npcId} already exists, the new {@code teleList} data will be merged into the existing data.
     * </p>
     * @param npcId the template ID of the teleporter NPC
     * @param teleList a map of teleport data associated with the NPC
     */
    private void registerTeleportList(int npcId, Map<String, TeleportHolder> teleList) {
        teleporters.computeIfAbsent(npcId, k -> new ConcurrentHashMap<>()).putAll(teleList);
    }

    /**
     * Retrieves the teleport data associated with a specified NPC and list name.
     * <p>
     * This method searches for the teleport list by {@code npcId} and {@code listName}. If found, the corresponding {@link TeleportHolder} is returned; otherwise, {@code null} is returned.
     * </p>
     * @param npcId the template ID of the teleporter NPC
     * @param listName the name of the teleport list
     * @return the {@link TeleportHolder} for the specified NPC and list name, or {@code null} if not found
     */
    public TeleportHolder getHolder(int npcId, String listName) {
        return teleporters.getOrDefault(npcId, Collections.emptyMap()).get(listName);
    }

    public static TeleporterData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final TeleporterData INSTANCE = new TeleporterData();
    }
}
