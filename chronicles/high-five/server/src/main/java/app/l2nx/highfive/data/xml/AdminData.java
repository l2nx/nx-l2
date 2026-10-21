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

import app.l2nx.highfive.data.holders.AccessLevel;
import app.l2nx.highfive.data.holders.AccessRight;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import app.l2nx.highfive.util.StatSet;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;

/**
 * Loads administrator access levels and commands.
 * @author UnAfraid, Mobius
 */
@Slf4j
public class AdminData implements IXmlReader {

    private final Map<Integer, AccessLevel> accessLevels = new HashMap<>();
    private final Map<String, AccessRight> adminCommandAccessRights = new LinkedHashMap<>();
    private final Map<Player, Boolean> gmList = new ConcurrentHashMap<>();
    private int highestLevel = 0;

    protected AdminData() {
        load();
    }

    @Override
    public synchronized void load() {
        accessLevels.clear();
        adminCommandAccessRights.clear();

        parseDatapackFile("config/AccessLevels.xml");
        log.info("Loaded {} access levels.", accessLevels.size());

        parseDatapackFile("config/AdminCommands.xml");
        log.info("Loaded {} access commands.", adminCommandAccessRights.size());
    }

    @Override
    public void parseDocument(Document document, File file) {
        forEach(document, n -> {
            if ("list".equalsIgnoreCase(n.getNodeName())) {
                forEach(n, d -> {
                    switch (d.getNodeName()) {
                        case "access": {
                            final StatSet set = new StatSet(parseAttributes(d));
                            final AccessLevel level = new AccessLevel(set);
                            if (level.getLevel() > highestLevel) {
                                highestLevel = level.getLevel();
                            }

                            accessLevels.put(level.getLevel(), level);
                            break;
                        }
                        case "admin": {
                            final StatSet set = new StatSet(parseAttributes(d));
                            final AccessRight command = new AccessRight(set);
                            adminCommandAccessRights.put(command.getCommand(), command);
                            break;
                        }
                    }
                });
            }
        });
    }

    /**
     * Returns the access level associated with a specific character access level.
     * @param accessLevel the access level as an integer
     * @return the {@link AccessLevel} instance corresponding to the given character access level
     */
    public AccessLevel getAccessLevel(int accessLevel) {
        if (accessLevel < 0) {
            return accessLevels.get(-1);
        }

        return accessLevels.computeIfAbsent(accessLevel, k -> new AccessLevel());
    }

    /**
     * Gets the master access level, which represents the highest available access level.
     * @return the {@link AccessLevel} instance representing the master access level
     */
    public AccessLevel getMasterAccessLevel() {
        return accessLevels.get(highestLevel);
    }

    /**
     * Checks if an access level exists.
     * @param accessLevel the access level as an integer
     * @return {@code true} if the access level exists, {@code false} otherwise
     */
    public boolean hasAccessLevel(int accessLevel) {
        return accessLevels.containsKey(accessLevel);
    }

    /**
     * Checks if the specified access level has permission to execute a given admin command.
     * @param adminCommand the admin command to check
     * @param accessLevel the {@link AccessLevel} instance to verify
     * @return {@code true} if the access level has access to the admin command, {@code false} otherwise
     */
    public boolean hasAccess(String adminCommand, AccessLevel accessLevel) {
        AccessRight accessRight = adminCommandAccessRights.get(adminCommand);
        if (accessRight == null) {
            // Trying to avoid the spam for next time when the GM would try to use the same command.
            if ((accessLevel.getLevel() > 0) && (accessLevel.getLevel() == highestLevel)) {
                accessRight = new AccessRight(adminCommand, "", true, accessLevel.getLevel());
                adminCommandAccessRights.put(accessRight.getCommand(), accessRight);
                log.info(
                        "No rights defined for admin command {} auto setting accesslevel: {} !",
                        adminCommand,
                        accessLevel.getLevel());
            } else {
                log.info("No rights defined for admin command {} !", adminCommand);
                return false;
            }
        }

        return accessRight.hasAccess(accessLevel);
    }

    /**
     * Determines if an admin command requires confirmation before execution.
     * @param command the admin command to check
     * @return {@code true} if the command requires confirmation, {@code false} otherwise
     */
    public boolean requireConfirm(String command) {
        final AccessRight accessRight = adminCommandAccessRights.get(command);
        if (accessRight == null) {
            log.info("No rights defined for admin command {}.", command);
            return false;
        }

        return accessRight.requireConfirm();
    }

    /**
     * Retrieves a list of all Game Masters (GMs).
     * @param includeHidden {@code true} to include hidden GMs, {@code false} to exclude them
     * @return a {@link List} of {@link Player} instances representing the GMs
     */
    public Collection<Player> getAllGms(boolean includeHidden) {
        final List<Player> result = new ArrayList<>();
        for (Entry<Player, Boolean> entry : gmList.entrySet()) {
            if (includeHidden || !entry.getValue()) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    /**
     * Retrieves a list of all Game Master (GM) names.
     * @param includeHidden {@code true} to include hidden GMs, {@code false} to exclude them
     * @return a {@link List} of GM names as {@link String}
     */
    public Collection<String> getAllGmNames(boolean includeHidden) {
        final List<String> result = new ArrayList<>();
        for (Entry<Player, Boolean> entry : gmList.entrySet()) {
            if (!entry.getValue()) {
                result.add(entry.getKey().getName());
            } else if (includeHidden) {
                result.add(entry.getKey().getName() + " (invis)");
            }
        }

        return result;
    }

    /**
     * Adds a GM to the GM list.
     * @param player the {@link Player} instance representing the GM
     * @param hidden {@code true} if the GM is hidden, {@code false} otherwise
     */
    public void addGm(Player player, boolean hidden) {
        gmList.put(player, hidden);
    }

    /**
     * Removes a GM from the GM list.
     * @param player the {@link Player} instance to remove
     */
    public void deleteGm(Player player) {
        gmList.remove(player);
    }

    /**
     * Checks if any GM is currently online.
     * @param includeHidden {@code true} to include hidden GMs, {@code false} to exclude them
     * @return {@code true} if a GM is online, {@code false} otherwise
     */
    public boolean isGmOnline(boolean includeHidden) {
        for (Entry<Player, Boolean> entry : gmList.entrySet()) {
            if (includeHidden || !entry.getValue()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Sends the list of visible GMs to a specific player.
     * @param player the {@link Player} instance to receive the GM list
     */
    public void sendListToPlayer(Player player) {
        if (isGmOnline(player.isGM())) {
            player.sendPacket(SystemMessageId.GM_LIST);
            for (String name : getAllGmNames(player.isGM())) {
                final SystemMessage sm = new SystemMessage(SystemMessageId.GM_C1);
                sm.addString(name);
                player.sendPacket(sm);
            }
        } else {
            player.sendPacket(
                    SystemMessageId
                            .THERE_ARE_NO_GMS_CURRENTLY_VISIBLE_IN_THE_PUBLIC_LIST_AS_THEY_MAY_BE_PERFORMING_OTHER_FUNCTIONS_AT_THE_MOMENT);
        }
    }

    /**
     * Broadcasts a packet to all GMs.
     * @param packet the {@link ServerPacket} to broadcast
     */
    public void broadcastToGMs(ServerPacket packet) {
        for (Player player : getAllGms(true)) {
            player.sendPacket(packet);
        }
    }

    /**
     * Broadcasts a message to all GMs.
     * @param message the message to broadcast as a {@link String}
     */
    public void broadcastMessageToGMs(String message) {
        for (Player player : getAllGms(true)) {
            player.sendMessage(message);
        }
    }

    /**
     * @return all admin access rights
     */
    public Collection<AccessRight> getAdminCommandAccessRights() {
        return adminCommandAccessRights.values();
    }

    public static AdminData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final AdminData INSTANCE = new AdminData();
    }
}
