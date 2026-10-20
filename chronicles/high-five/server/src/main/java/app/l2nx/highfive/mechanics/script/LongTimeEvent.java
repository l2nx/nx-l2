/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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
package app.l2nx.highfive.mechanics.script;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.sql.AnnouncementsTable;
import app.l2nx.highfive.data.xml.IXmlReader;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.npc.EventDropHolder;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.managers.EventDropManager;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.mechanics.announce.EventAnnouncement;
import app.l2nx.highfive.mechanics.events.Containers;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.OnServerStart;
import app.l2nx.highfive.mechanics.events.listeners.ConsumerEventListener;
import app.l2nx.highfive.threads.ThreadPool;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Parent class for long time events.<br>
 * Maintains config reading, spawn of NPCs, adding of event's drop.
 * @author GKR, Mobius
 */
@Slf4j
public class LongTimeEvent extends Script {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MM yyyy", Locale.US);

    protected String eventName;

    @Getter
    protected Date startDate = null;

    @Getter
    protected Date endDate = null;

    protected Date dropStartDate = null;
    protected Date dropEndDate = null;
    protected boolean initialized = false;

    @Getter
    protected boolean eventPeriod = false;

    // Messages
    protected String onEnterMsg = "";
    protected String endMsg = "";
    protected int enterAnnounceId = -1;

    // NPCs to spawn and their spawn points.
    protected final List<NpcSpawn> spawnList = new ArrayList<>();

    // Drop data for event.
    protected final List<EventDropHolder> dropList = new ArrayList<>();

    // Items to destroy when event ends.
    protected final List<Integer> destroyItemsOnEnd = new ArrayList<>();

    protected class NpcSpawn {
        protected final int npcId;
        protected final Location loc;
        protected final int respawnTime;

        protected NpcSpawn(int spawnNpcId, Location spawnLoc, int spawnRespawnTime) {
            npcId = spawnNpcId;
            loc = spawnLoc;
            respawnTime = spawnRespawnTime;
        }
    }

    public LongTimeEvent() {
        loadConfig();

        if ((startDate != null) && (endDate != null)) {
            final Date now = new Date(ClockBridge.wallMillis());
            if (isWithinRange(now)) {
                startEvent();
                log.info("Event {} active till {}", eventName, endDate);
            } else if (startDate.after(now)) {
                final long delay = startDate.getTime() - ClockBridge.wallMillis();
                ThreadPool.schedule(new ScheduleStart(), delay);
                log.info("Event {} will be started at {}", eventName, startDate);
            } else {
                // Destroy items that must exist only on event period.
                destroyItemsOnEnd();
                log.info("Event {} has passed... Ignored ", eventName);
            }
        }

        initialized = true;
    }

    /**
     * Checks if the given date is within the event period.
     * @param date the date to check
     * @return {@code true} if the date is within range
     */
    private boolean isWithinRange(Date date) {
        return (date.equals(startDate) || date.after(startDate)) && (date.equals(endDate) || date.before(endDate));
    }

    private boolean isWithinDropPeriod(Date date) {
        return (date.equals(dropStartDate) || date.after(dropStartDate))
                && (date.equals(dropEndDate) || date.before(dropEndDate));
    }

    /**
     * Checks if the event period is valid (start before end).
     * @return {@code true} if the period is valid
     */
    private boolean isValidPeriod() {
        return (startDate != null) && (endDate != null) && startDate.before(endDate);
    }

    /**
     * Parses a date range string into start and end dates.
     * @param dateRange the date range string
     * @param format the date format to use
     * @return
     */
    private Date[] parseDateRange(String dateRange, DateTimeFormatter format) {
        final String[] dates = dateRange.split("-");
        if (dates.length == 2) {
            try {
                final Date start = Date.from(LocalDate.parse(dates[0].trim(), format)
                        .atStartOfDay(ClockBridge.wallClock().getZone())
                        .toInstant());
                final Date end = Date.from(LocalDate.parse(dates[1].trim(), format)
                        .atStartOfDay(ClockBridge.wallClock().getZone())
                        .toInstant());
                return new Date[] {start, end};
            } catch (Exception e) {
                log.warn("Invalid Date Format: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * Load event configuration file
     */
    private void loadConfig() {
        new IXmlReader() {
            @Override
            public void load() {
                parseDatapackFile("data/scripts/events/" + getName() + "/config.xml");
            }

            @Override
            public void parseDocument(Document document, File file) {
                if (!document.getDocumentElement().getNodeName().equalsIgnoreCase("event")) {
                    throw new NullPointerException("WARNING!!! " + getName() + " event: bad config file!");
                }

                eventName = document.getDocumentElement()
                        .getAttributes()
                        .getNamedItem("name")
                        .getNodeValue();
                final String currentYear = String.valueOf(ClockBridge.calendar().get(Calendar.YEAR));
                final String period = document.getDocumentElement()
                        .getAttributes()
                        .getNamedItem("active")
                        .getNodeValue();
                String dropPeriod =
                        document.getDocumentElement().getAttributes().getNamedItem("dropPeriod") != null
                                ? document.getDocumentElement()
                                        .getAttributes()
                                        .getNamedItem("dropPeriod")
                                        .getNodeValue()
                                : null;

                // If no drop period is defined, use the active period as the drop period.
                if ((dropPeriod == null) || dropPeriod.isEmpty()) {
                    dropPeriod = period; // Use the same period as the active period.
                }

                // Handle active period
                if (period.length() == 21) {
                    final Date[] range = parseDateRange(period, DATE_FORMAT);
                    if (range != null) {
                        startDate = range[0];
                        endDate = range[1];
                    }
                } else if (period.length() == 11) {
                    final String[] parts = period.split("-");
                    final String start = parts[0] + " " + currentYear;
                    final String end = parts[1] + " " + currentYear;
                    final Date[] range = parseDateRange(start + "-" + end, DATE_FORMAT);
                    if (range != null) {
                        startDate = range[0];
                        endDate = range[1];
                    }
                }

                // Handle drop period (may be different from event period).
                if (dropPeriod.length() == 21) {
                    final Date[] dropRange = parseDateRange(dropPeriod, DATE_FORMAT);
                    if (dropRange != null) {
                        dropStartDate = dropRange[0];
                        dropEndDate = dropRange[1];
                    }
                } else if (dropPeriod.length() == 11) {
                    final String[] parts = dropPeriod.split("-");
                    final String start = parts[0] + " " + currentYear;
                    final String end = parts[1] + " " + currentYear;
                    final Date[] dropRange = parseDateRange(start + "-" + end, DATE_FORMAT);
                    if (dropRange != null) {
                        dropStartDate = dropRange[0];
                        dropEndDate = dropRange[1];
                    }
                }

                // Check that drop period is inside active period.
                if (((dropStartDate != null) && dropStartDate.before(startDate))
                        || ((dropEndDate != null) && dropEndDate.after(endDate))) {
                    throw new NullPointerException(
                            "WARNING!!! " + getName() + " event: drop period must be within the active period");
                }

                if (!isValidPeriod()) {
                    throw new NullPointerException("WARNING!!! " + getName() + " event: illegal event period");
                }

                final Date today = new Date(ClockBridge.wallMillis());

                if (startDate.after(today) || isWithinRange(today)) {
                    for (Node n = document.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling()) {
                        // Loading droplist
                        if (n.getNodeName().equalsIgnoreCase("droplist")) {
                            for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                                if (d.getNodeName().equalsIgnoreCase("add")) {
                                    try {
                                        final int itemId = Integer.parseInt(d.getAttributes()
                                                .getNamedItem("item")
                                                .getNodeValue());
                                        final int minCount = Integer.parseInt(d.getAttributes()
                                                .getNamedItem("min")
                                                .getNodeValue());
                                        final int maxCount = Integer.parseInt(d.getAttributes()
                                                .getNamedItem("max")
                                                .getNodeValue());
                                        final String chance = d.getAttributes()
                                                .getNamedItem("chance")
                                                .getNodeValue();
                                        final double finalChance = !chance.isEmpty() && chance.endsWith("%")
                                                ? Double.parseDouble(chance.substring(0, chance.length() - 1))
                                                : 0;
                                        final Node minLevelNode =
                                                d.getAttributes().getNamedItem("minLevel");
                                        final int minLevel = minLevelNode == null
                                                ? 1
                                                : Integer.parseInt(minLevelNode.getNodeValue());
                                        final Node maxLevelNode =
                                                d.getAttributes().getNamedItem("maxLevel");
                                        final int maxLevel = maxLevelNode == null
                                                ? Integer.MAX_VALUE
                                                : Integer.parseInt(maxLevelNode.getNodeValue());
                                        final Node monsterIdsNode =
                                                d.getAttributes().getNamedItem("monsterIds");
                                        final Set<Integer> monsterIds = new HashSet<>();
                                        if (monsterIdsNode != null) {
                                            for (String id : monsterIdsNode
                                                    .getNodeValue()
                                                    .split(",")) {
                                                monsterIds.add(Integer.parseInt(id));
                                            }
                                        }

                                        if (ItemData.getInstance().getTemplate(itemId) == null) {
                                            log.warn(
                                                    "{} event: {} is wrong item id, item was not added in droplist",
                                                    getName(),
                                                    itemId);
                                            continue;
                                        }

                                        if (minCount > maxCount) {
                                            log.warn(
                                                    "{} event: item {} - min greater than max, item was not added in"
                                                            + " droplist",
                                                    getName(),
                                                    itemId);
                                            continue;
                                        }

                                        if ((finalChance < 0) || (finalChance > 100)) {
                                            log.warn(
                                                    "{} event: item {} - incorrect drop chance, item was not added in"
                                                            + " droplist",
                                                    getName(),
                                                    itemId);
                                            continue;
                                        }

                                        dropList.add(new EventDropHolder(
                                                itemId,
                                                minCount,
                                                maxCount,
                                                finalChance,
                                                minLevel,
                                                maxLevel,
                                                monsterIds));
                                    } catch (NumberFormatException nfe) {
                                        log.warn(
                                                "Wrong number format in config.xml droplist block for {} event",
                                                getName());
                                    }
                                }
                            }
                        } else if (n.getNodeName().equalsIgnoreCase("spawnlist")) {
                            // Loading spawnlist
                            for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                                if (d.getNodeName().equalsIgnoreCase("add")) {
                                    try {
                                        final int npcId = Integer.parseInt(d.getAttributes()
                                                .getNamedItem("npc")
                                                .getNodeValue());
                                        if (NpcData.getInstance().getTemplate(npcId) == null) {
                                            log.warn(
                                                    "{} event: {} is wrong NPC id, NPC was not added in spawnlist",
                                                    getName(),
                                                    npcId);
                                            continue;
                                        }

                                        final int xPos = Integer.parseInt(d.getAttributes()
                                                .getNamedItem("x")
                                                .getNodeValue());
                                        final int yPos = Integer.parseInt(d.getAttributes()
                                                .getNamedItem("y")
                                                .getNodeValue());
                                        final int zPos = Integer.parseInt(d.getAttributes()
                                                .getNamedItem("z")
                                                .getNodeValue());
                                        final Node headingNode =
                                                d.getAttributes().getNamedItem("heading");
                                        final String headingValue =
                                                headingNode == null ? null : headingNode.getNodeValue();
                                        final int heading = headingValue != null ? Integer.parseInt(headingValue) : 0;
                                        final Node respawnTimeNode =
                                                d.getAttributes().getNamedItem("respawnTime");
                                        final String respawnTimeValue =
                                                respawnTimeNode == null ? null : respawnTimeNode.getNodeValue();
                                        final int respawnTime =
                                                respawnTimeValue != null ? Integer.parseInt(respawnTimeValue) : 0;
                                        spawnList.add(new NpcSpawn(
                                                npcId, new Location(xPos, yPos, zPos, heading), respawnTime * 1000));
                                    } catch (NumberFormatException nfe) {
                                        log.warn(
                                                "Wrong number format in config.xml spawnlist block for {} event",
                                                getName());
                                    }
                                }
                            }
                        } else if (n.getNodeName().equalsIgnoreCase("messages")) {
                            // Loading Messages
                            for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                                if (d.getNodeName().equalsIgnoreCase("add")) {
                                    final String msgType = d.getAttributes()
                                            .getNamedItem("type")
                                            .getNodeValue();
                                    final String msgText = d.getAttributes()
                                            .getNamedItem("text")
                                            .getNodeValue();
                                    if ((msgType != null) && (msgText != null)) {
                                        if (msgType.equalsIgnoreCase("onEnd")) {
                                            endMsg = msgText;
                                        } else if (msgType.equalsIgnoreCase("onEnter")) {
                                            onEnterMsg = msgText;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Load destroy item list at all times.
                for (Node n = document.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (n.getNodeName().equalsIgnoreCase("destroyItemsOnEnd")) {
                        for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                            if (d.getNodeName().equalsIgnoreCase("item")) {
                                try {
                                    final int itemId = Integer.parseInt(
                                            d.getAttributes().getNamedItem("id").getNodeValue());
                                    if (ItemData.getInstance().getTemplate(itemId) == null) {
                                        log.warn("{} event: Item {} does not exist.", getName(), itemId);
                                        continue;
                                    }

                                    destroyItemsOnEnd.add(itemId);
                                } catch (NumberFormatException nfe) {
                                    log.warn(
                                            "Wrong number format in config.xml destroyItemsOnEnd block for {} event",
                                            getName());
                                }
                            }
                        }
                    }
                }
            }
        }.load();
    }

    protected class ScheduleStart implements Runnable {
        @Override
        public void run() {
            startEvent();
        }
    }

    protected void startEvent() {
        // Set Active.
        eventPeriod = true;

        // Add event drops if within the drop period.
        if ((dropStartDate != null)
                && (dropEndDate != null)
                && isWithinDropPeriod(new Date(ClockBridge.wallMillis()))) {
            EventDropManager.getInstance().addDrops(this, dropList);
        }

        if (!spawnList.isEmpty()) {
            if (initialized) {
                // Add spawns on event start.
                spawnNpcs();
            } else // Add spawns on server start.
            {
                Containers.Global()
                        .addListener(new ConsumerEventListener(
                                Containers.Global(), EventType.ON_SERVER_START, spawnNpcs, this));
            }
        }

        // Event enter announcement.
        if (!onEnterMsg.isEmpty()) {
            // Send message on begin.
            World.broadcastToAllOnlinePlayers(onEnterMsg);

            // Add announce for entering players.
            final EventAnnouncement announce = new EventAnnouncement(startDate, endDate, onEnterMsg);
            AnnouncementsTable.getInstance().addAnnouncement(announce);
            enterAnnounceId = announce.getId();
        }

        // Schedule event end.
        final Long millisToEventEnd = endDate.getTime() - ClockBridge.wallMillis();
        ThreadPool.schedule(new ScheduleEnd(), millisToEventEnd);
    }

    /**
     * Event spawns must initialize after server loads scripts.
     */
    private final Consumer<OnServerStart> spawnNpcs = _ -> {
        spawnNpcs();
        Containers.Global().removeListenerIf(EventType.ON_SERVER_START, listener -> listener.getOwner() == this);
    };

    protected void spawnNpcs() {
        final Long millisToEventEnd = endDate.getTime() - ClockBridge.wallMillis();
        for (NpcSpawn npcSpawn : spawnList) {
            final Npc npc = addSpawn(
                    npcSpawn.npcId,
                    npcSpawn.loc.getX(),
                    npcSpawn.loc.getY(),
                    npcSpawn.loc.getZ(),
                    npcSpawn.loc.getHeading(),
                    false,
                    millisToEventEnd,
                    false);
            if (npcSpawn.respawnTime > 0) {
                final Spawn spawn = npc.getSpawn();
                spawn.setRespawnDelay(npcSpawn.respawnTime);
                spawn.startRespawn();
                ThreadPool.schedule(spawn::stopRespawn, millisToEventEnd - npcSpawn.respawnTime);
            }
        }
    }

    protected class ScheduleEnd implements Runnable {
        @Override
        public void run() {
            stopEvent();
        }
    }

    protected void stopEvent() {
        // Set Active.
        eventPeriod = false;

        // Stop event drops.
        EventDropManager.getInstance().removeDrops(this);

        // Destroy items that must exist only on event period.
        destroyItemsOnEnd();

        // Send message on end.
        if (!endMsg.isEmpty()) {
            World.broadcastToAllOnlinePlayers(endMsg);
        }

        // Remove announce for entering players.
        if (enterAnnounceId != -1) {
            AnnouncementsTable.getInstance().deleteAnnouncement(enterAnnounceId);
        }
    }

    protected void destroyItemsOnEnd() {
        if (!destroyItemsOnEnd.isEmpty()) {
            for (int itemId : destroyItemsOnEnd) {
                // Remove item from online players.
                for (Player player : World.getPlayers()) {
                    if (player != null) {
                        player.destroyItemByItemId(ItemProcessType.DESTROY, itemId, -1, player, true);
                    }
                }

                // Update database.
                try {
                    ItemManager.getInstance().deleteItemsOfTemplate(itemId);
                } catch (DataAccessException e) {
                    log.warn("Could not delete the items {} of the ended event: {}", itemId, e.getMessage(), e);
                }
            }
        }
    }
}
