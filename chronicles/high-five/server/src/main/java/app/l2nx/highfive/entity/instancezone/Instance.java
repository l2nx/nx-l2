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
package app.l2nx.highfive.entity.instancezone;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.holders.InstanceReenterTimeHolder;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldRegion;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.actor.templates.DoorTemplate;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.network.serverpackets.CreatureSay;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.StatSet;
import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Main class for game instances.
 * @author evill33t, GodKratos
 */
@Slf4j
public class Instance {

    @Getter
    private final int id;

    @Getter
    private int templateId = -1;

    @Getter
    @Setter
    private int ejectTime = GeneralConfig.EJECT_DEAD_PLAYER_TIME;
    /** Allow random walk for NPCs, global parameter. */
    private boolean allowRandomWalk = true;

    @Getter
    private final Collection<Integer> players = ConcurrentHashMap.newKeySet();

    @Getter
    private final Collection<Npc> npcs = ConcurrentHashMap.newKeySet();

    private final Collection<StatSet> doorTemplates = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Door> doors = new ConcurrentHashMap<>();
    private final Collection<StatSet> spawnTemplates = ConcurrentHashMap.newKeySet();

    @Getter
    private Collection<Location> enterLocs = ConcurrentHashMap.newKeySet();

    @Getter
    @Setter
    private Location exitLoc = null;

    @Getter
    private boolean summonAllowed = true;

    @Setter
    private long emptyDestroyTime = -1;

    private long lastLeft = -1;

    @Getter
    private long instanceStartTime = -1;

    @Getter
    private long instanceEndTime = -1;

    @Getter
    @Setter
    private boolean pvP = false;

    @Getter
    private boolean showTimer = false;

    @Getter
    private boolean timerIncrease = true;

    @Getter
    private String timerText = "";

    // Instance reset data
    @Getter
    @Setter
    private InstanceReenterType reenterType = InstanceReenterType.NONE;

    @Getter
    private final List<InstanceReenterTimeHolder> reenterData = new ArrayList<>();

    // Instance remove buffs data
    @Getter
    private InstanceRemoveBuffType removeBuffType = InstanceRemoveBuffType.NONE;

    @Getter
    private final List<Integer> buffExceptionList = new ArrayList<>();

    protected ScheduledFuture<?> checkTimeUpTask = null;
    protected final Map<Integer, ScheduledFuture<?>> ejectDeadTasks = new ConcurrentHashMap<>();

    public Instance(int id) {
        this.id = id;
        instanceStartTime = System.currentTimeMillis();
    }

    /**
     * Sets the status for the instance for summon friend type skills
     * @param value
     */
    public void setAllowSummon(boolean value) {
        summonAllowed = value;
    }

    /**
     * Set the instance duration task
     * @param duration in milliseconds
     */
    public void setDuration(int duration) {
        if (checkTimeUpTask != null) {
            checkTimeUpTask.cancel(true);
        }

        checkTimeUpTask = ThreadPool.schedule(new CheckTimeUp(duration), 500);
        instanceEndTime = System.currentTimeMillis() + duration + 500;
    }

    /**
     * Checks if the player exists within this instance
     * @param objectId
     * @return true if player exists in instance
     */
    public boolean containsPlayer(int objectId) {
        return players.contains(objectId);
    }

    /**
     * Adds the specified player to the instance
     * @param objectId Players object ID
     */
    public void addPlayer(int objectId) {
        players.add(objectId);
    }

    /**
     * Removes the specified player from the instance list.
     * @param objectId the player's object Id
     */
    public void removePlayer(Integer objectId) {
        players.remove(objectId);
        if (!players.isEmpty() || (emptyDestroyTime < 0)) {
            return;
        }

        lastLeft = System.currentTimeMillis();
        setDuration((int) (instanceEndTime - System.currentTimeMillis() - 500));
    }

    public void addNpc(Npc npc) {
        npcs.add(npc);
    }

    public void removeNpc(Npc npc) {
        if (npc.getSpawn() != null) {
            npc.getSpawn().stopRespawn();
        }

        npcs.remove(npc);
    }

    /**
     * Adds a door into the instance
     * @param set - StatSet for initializing door
     */
    public void addDoor(StatSet set) {
        if (doorTemplates.contains(set)) {
            log.warn("Door ID {} already exists in instance {}", set.getInt("DoorId"), id);
            return;
        }

        doorTemplates.add(set);
    }

    /**
     * Spawn doors inside instance world.
     */
    public void spawnDoors() {
        for (StatSet template : doorTemplates) {
            // Create new door instance.
            final int doorId = template.getInt("DoorId");
            final StatSet doorTemplate = DoorData.getInstance().getDoorTemplate(doorId);
            final Door newdoor = new Door(new DoorTemplate(doorTemplate));
            newdoor.setInstanceId(id);
            newdoor.setCurrentHp(newdoor.getMaxHp());
            newdoor.spawnMe(
                    newdoor.getTemplate().getX(),
                    newdoor.getTemplate().getY(),
                    newdoor.getTemplate().getZ());
            doors.put(doorId, newdoor);
        }
    }

    public Collection<Door> getDoors() {
        return doors.values();
    }

    public Door getDoor(int id) {
        return doors.get(id);
    }

    /**
     * Sets the spawn location for this instance to be used when enter in instance
     * @param loc
     */
    public void addEnterLoc(Location loc) {
        enterLocs.add(loc);
    }

    public void removePlayers() {
        for (Integer objectId : players) {
            final Player player = World.getPlayer(objectId);
            if ((player != null) && (player.getInstanceId() == id)) {
                player.setInstanceId(0);
                if (exitLoc != null) {
                    player.teleToLocation(exitLoc, true);
                } else {
                    player.teleToLocation(TeleportWhereType.TOWN);
                }
            }
        }

        players.clear();
    }

    public void removeNpcs() {
        for (Npc mob : npcs) {
            if (mob != null) {
                if (mob.getSpawn() != null) {
                    mob.getSpawn().stopRespawn();
                }

                mob.deleteMe();
            }
        }

        npcs.clear();
    }

    public void removeDoors() {
        for (Door door : doors.values()) {
            if (door != null) {
                final WorldRegion region = door.getWorldRegion();
                door.decayMe();

                if (region != null) {
                    region.removeVisibleObject(door);
                }

                World.removeObject(door);
            }
        }

        doors.clear();
    }

    /**
     * Spawns group of instance NPCs
     * @param groupName - name of group from XML definition to spawn
     * @return list of spawned NPCs
     */
    public List<Npc> spawnGroup(String groupName) {
        final List<Npc> spawnedNpcs = new ArrayList<>();
        for (StatSet set : spawnTemplates) {
            if (set.getString("spawnGroup").equals(groupName)) {
                try {
                    final Spawn spawnDat = new Spawn(set.getInt("npcId"));
                    spawnDat.setXYZ(set.getInt("x"), set.getInt("y"), set.getInt("z"));
                    spawnDat.setAmount(1);
                    spawnDat.setHeading(set.getInt("heading"));
                    spawnDat.setRespawnDelay(set.getInt("respawn"), set.getInt("respawnRandom"));
                    spawnDat.setInstanceId(id);
                    spawnDat.setRandomWalking(set.getBoolean("allowRandomWalk"));
                    final Npc spawned = spawnDat.doSpawn(false);
                    if ((set.getInt("delay") >= 0) && spawned.isAttackable()) {
                        spawned.asAttackable().setOnKillDelay(set.getInt("delay"));
                    }

                    if (set.getInt("respawn") == 0) {
                        spawnDat.stopRespawn();
                    } else {
                        spawnDat.startRespawn();
                    }

                    spawnedNpcs.add(spawned);
                } catch (Exception e) {
                    log.warn("Problem with Instance: {}", e.toString());
                }
            }
        }

        return spawnedNpcs;
    }

    public void loadInstanceTemplate(int templateId) {
        // TODO: Cache templates.
        Document document = null;
        final File xml = new File("." + File.separator + "data" + File.separator + "instances" + File.separator
                + InstanceManager.getInstance().getInstanceTemplateFileName(templateId));
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setIgnoringComments(true);
            document = factory.newDocumentBuilder().parse(xml);
            for (Node n = document.getFirstChild(); n != null; n = n.getNextSibling()) {
                if ("instance".equalsIgnoreCase(n.getNodeName())) {
                    parseInstance(n);
                }
            }
        } catch (IOException e) {
            log.warn("can not find {} ! {}", xml.getAbsolutePath(), e.getMessage(), e);
        } catch (Exception e) {
            log.warn("error while loading {} ! {}", xml.getAbsolutePath(), e.getMessage(), e);
        }
    }

    private void parseInstance(Node node) throws Exception {
        Node n = node;
        templateId = Integer.parseInt(n.getAttributes().getNamedItem("id").getNodeValue());
        Node a = n.getAttributes().getNamedItem("ejectTime");
        if (a != null) {
            ejectTime = 1000 * Integer.parseInt(a.getNodeValue());
        }

        a = n.getAttributes().getNamedItem("allowRandomWalk");
        if (a != null) {
            this.allowRandomWalk = Boolean.parseBoolean(a.getNodeValue());
        }

        for (n = n.getFirstChild(); n != null; n = n.getNextSibling()) {
            switch (n.getNodeName().toLowerCase()) {
                case "activitytime": {
                    a = n.getAttributes().getNamedItem("val");
                    if (a != null) {
                        checkTimeUpTask =
                                ThreadPool.schedule(new CheckTimeUp(Integer.parseInt(a.getNodeValue()) * 60000), 15000);
                        instanceEndTime =
                                System.currentTimeMillis() + (Long.parseLong(a.getNodeValue()) * 60000) + 15000;
                    }
                    break;
                }
                case "allowsummon": {
                    a = n.getAttributes().getNamedItem("val");
                    if (a != null) {
                        setAllowSummon(Boolean.parseBoolean(a.getNodeValue()));
                    }
                    break;
                }
                case "emptydestroytime": {
                    a = n.getAttributes().getNamedItem("val");
                    if (a != null) {
                        emptyDestroyTime = Long.parseLong(a.getNodeValue()) * 1000;
                    }
                    break;
                }
                case "showtimer": {
                    a = n.getAttributes().getNamedItem("val");
                    if (a != null) {
                        showTimer = Boolean.parseBoolean(a.getNodeValue());
                    }

                    a = n.getAttributes().getNamedItem("increase");
                    if (a != null) {
                        timerIncrease = Boolean.parseBoolean(a.getNodeValue());
                    }

                    a = n.getAttributes().getNamedItem("text");
                    if (a != null) {
                        timerText = a.getNodeValue();
                    }
                    break;
                }
                case "pvpinstance": {
                    a = n.getAttributes().getNamedItem("val");
                    if (a != null) {
                        setPvP(Boolean.parseBoolean(a.getNodeValue()));
                    }
                    break;
                }
                case "doorlist": {
                    for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                        int doorId = 0;
                        if ("door".equalsIgnoreCase(d.getNodeName())) {
                            doorId = Integer.parseInt(
                                    d.getAttributes().getNamedItem("id").getNodeValue());
                            final StatSet set = new StatSet();
                            set.set("DoorId", doorId);
                            for (Node bean = d.getFirstChild(); bean != null; bean = bean.getNextSibling()) {
                                if ("set".equalsIgnoreCase(bean.getNodeName())) {
                                    final NamedNodeMap attrs = bean.getAttributes();
                                    final String setname =
                                            attrs.getNamedItem("name").getNodeValue();
                                    final String value =
                                            attrs.getNamedItem("val").getNodeValue();
                                    set.set(setname, value);
                                }
                            }

                            addDoor(set);
                        }
                    }
                    break;
                }
                case "spawnlist": {
                    for (Node group = n.getFirstChild(); group != null; group = group.getNextSibling()) {
                        if ("group".equalsIgnoreCase(group.getNodeName())) {
                            final String spawnGroup =
                                    group.getAttributes().getNamedItem("name").getNodeValue();
                            for (Node d = group.getFirstChild(); d != null; d = d.getNextSibling()) {
                                int npcId = 0;
                                int x = 0;
                                int y = 0;
                                int z = 0;
                                int heading = 0;
                                int respawn = 0;
                                int respawnRandom = 0;
                                int delay = -1;
                                Boolean allowRandomWalk = null;
                                if ("npc".equalsIgnoreCase(d.getNodeName())) {
                                    npcId = Integer.parseInt(
                                            d.getAttributes().getNamedItem("id").getNodeValue());
                                    x = Integer.parseInt(
                                            d.getAttributes().getNamedItem("x").getNodeValue());
                                    y = Integer.parseInt(
                                            d.getAttributes().getNamedItem("y").getNodeValue());
                                    z = Integer.parseInt(
                                            d.getAttributes().getNamedItem("z").getNodeValue());
                                    if (d.getAttributes().getNamedItem("heading") != null) {
                                        heading = Integer.parseInt(d.getAttributes()
                                                .getNamedItem("heading")
                                                .getNodeValue());
                                    }

                                    if (d.getAttributes().getNamedItem("respawn") != null) {
                                        respawn = Integer.parseInt(d.getAttributes()
                                                .getNamedItem("respawn")
                                                .getNodeValue());
                                    }

                                    if (d.getAttributes().getNamedItem("onKillDelay") != null) {
                                        delay = Integer.parseInt(d.getAttributes()
                                                .getNamedItem("onKillDelay")
                                                .getNodeValue());
                                    }

                                    if (d.getAttributes().getNamedItem("respawnRandom") != null) {
                                        respawnRandom = Integer.parseInt(d.getAttributes()
                                                .getNamedItem("respawnRandom")
                                                .getNodeValue());
                                    }

                                    if (d.getAttributes().getNamedItem("allowRandomWalk") != null) {
                                        allowRandomWalk = Boolean.parseBoolean(d.getAttributes()
                                                .getNamedItem("allowRandomWalk")
                                                .getNodeValue());
                                    }

                                    final StatSet spawnSet = new StatSet();
                                    spawnSet.set("spawnGroup", spawnGroup);
                                    spawnSet.set("npcId", npcId);
                                    spawnSet.set("x", x);
                                    spawnSet.set("y", y);
                                    spawnSet.set("z", z);
                                    spawnSet.set("heading", heading);
                                    spawnSet.set("delay", delay);
                                    spawnSet.set("respawn", respawn);
                                    spawnSet.set("respawnRandom", respawnRandom);
                                    if (allowRandomWalk == null) {
                                        spawnSet.set("allowRandomWalk", !this.allowRandomWalk);
                                    } else {
                                        spawnSet.set("allowRandomWalk", !allowRandomWalk);
                                    }

                                    spawnTemplates.add(spawnSet);
                                }
                            }
                        }
                    }
                    break;
                }
                case "exitpoint": {
                    final int x =
                            Integer.parseInt(n.getAttributes().getNamedItem("x").getNodeValue());
                    final int y =
                            Integer.parseInt(n.getAttributes().getNamedItem("y").getNodeValue());
                    final int z =
                            Integer.parseInt(n.getAttributes().getNamedItem("z").getNodeValue());
                    exitLoc = new Location(x, y, z);
                    break;
                }
                case "spawnpoints": {
                    enterLocs = new ArrayList<>();
                    for (Node loc = n.getFirstChild(); loc != null; loc = loc.getNextSibling()) {
                        if (loc.getNodeName().equals("Location")) {
                            try {
                                final int x = Integer.parseInt(
                                        loc.getAttributes().getNamedItem("x").getNodeValue());
                                final int y = Integer.parseInt(
                                        loc.getAttributes().getNamedItem("y").getNodeValue());
                                final int z = Integer.parseInt(
                                        loc.getAttributes().getNamedItem("z").getNodeValue());
                                enterLocs.add(new Location(x, y, z));
                            } catch (Exception e) {
                                log.warn("Error parsing instance xml: {}", e.getMessage(), e);
                            }
                        }
                    }
                    break;
                }
                case "reenter": {
                    a = n.getAttributes().getNamedItem("additionStyle");
                    if (a != null) {
                        reenterType = InstanceReenterType.valueOf(a.getNodeValue());
                    }

                    for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                        long time = -1;
                        DayOfWeek day = null;
                        int hour = -1;
                        int minute = -1;
                        if ("reset".equalsIgnoreCase(d.getNodeName())) {
                            a = d.getAttributes().getNamedItem("time");
                            if (a != null) {
                                time = Long.parseLong(a.getNodeValue());
                                if (time > 0) {
                                    reenterData.add(new InstanceReenterTimeHolder(time));
                                    break;
                                }
                            } else if (time == -1) {
                                a = d.getAttributes().getNamedItem("day");
                                if (a != null) {
                                    day = DayOfWeek.valueOf(a.getNodeValue().toUpperCase());
                                }

                                a = d.getAttributes().getNamedItem("hour");
                                if (a != null) {
                                    hour = Integer.parseInt(a.getNodeValue());
                                }

                                a = d.getAttributes().getNamedItem("minute");
                                if (a != null) {
                                    minute = Integer.parseInt(a.getNodeValue());
                                }

                                reenterData.add(new InstanceReenterTimeHolder(day, hour, minute));
                            }
                        }
                    }
                    break;
                }
                case "removebuffs": {
                    a = n.getAttributes().getNamedItem("type");
                    if (a != null) {
                        removeBuffType =
                                InstanceRemoveBuffType.valueOf(a.getNodeValue().toUpperCase());
                    }

                    for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                        if ("skill".equalsIgnoreCase(d.getNodeName())) {
                            a = d.getAttributes().getNamedItem("id");
                            if (a != null) {
                                buffExceptionList.add(Integer.parseInt(a.getNodeValue()));
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    protected void doCheckTimeUp(int value) {
        CreatureSay cs = null;
        int timeLeft;
        int interval;
        int remaining = value;
        if (players.isEmpty() && (emptyDestroyTime == 0)) {
            remaining = 0;
            interval = 500;
        } else if (players.isEmpty() && (emptyDestroyTime > 0)) {
            final Long emptyTimeLeft = (lastLeft + emptyDestroyTime) - System.currentTimeMillis();
            if (emptyTimeLeft <= 0) {
                interval = 0;
                remaining = 0;
            } else if ((remaining > 300000) && (emptyTimeLeft > 300000)) {
                interval = 300000;
                remaining -= 300000;
            } else if ((remaining > 60000) && (emptyTimeLeft > 60000)) {
                interval = 60000;
                remaining -= 60000;
            } else if ((remaining > 30000) && (emptyTimeLeft > 30000)) {
                interval = 30000;
                remaining -= 30000;
            } else {
                interval = 10000;
                remaining -= 10000;
            }
        } else if (remaining > 300000) {
            timeLeft = remaining / 60000;
            interval = 300000;

            final SystemMessage sm = new SystemMessage(
                    SystemMessageId
                            .THIS_DUNGEON_WILL_EXPIRE_IN_S1_MINUTE_S_YOU_WILL_BE_FORCED_OUT_OF_THE_DUNGEON_WHEN_THE_TIME_EXPIRES);
            sm.addString(Integer.toString(timeLeft));
            sm.sendInBroadcast();

            for (Integer playerId : players) {
                final Player player = World.getPlayer(playerId);
                if ((player != null) && player.isOnline() && (player.getInstanceId() == id)) {
                    player.sendPacket(sm);
                }
            }

            remaining -= 300000;
        } else if (remaining > 60000) {
            timeLeft = remaining / 60000;
            interval = 60000;

            final SystemMessage sm = new SystemMessage(
                    SystemMessageId
                            .THIS_DUNGEON_WILL_EXPIRE_IN_S1_MINUTE_S_YOU_WILL_BE_FORCED_OUT_OF_THE_DUNGEON_WHEN_THE_TIME_EXPIRES);
            sm.addString(Integer.toString(timeLeft));
            sm.sendInBroadcast();

            for (Integer playerId : players) {
                final Player player = World.getPlayer(playerId);
                if ((player != null) && player.isOnline() && (player.getInstanceId() == id)) {
                    player.sendPacket(sm);
                }
            }

            remaining -= 60000;
        } else if (remaining > 30000) {
            timeLeft = remaining / 1000;
            interval = 30000;
            cs = new CreatureSay(null, ChatType.ALLIANCE, "Notice", timeLeft + " seconds left.");
            remaining -= 30000;
        } else {
            timeLeft = remaining / 1000;
            interval = 10000;
            cs = new CreatureSay(null, ChatType.ALLIANCE, "Notice", timeLeft + " seconds left.");
            remaining -= 10000;
        }

        if (cs != null) {
            for (Integer objectId : players) {
                final Player player = World.getPlayer(objectId);
                if ((player != null) && (player.getInstanceId() == id)) {
                    player.sendPacket(cs);
                }
            }
        }

        cancelTimer();
        if (remaining >= 10000) {
            checkTimeUpTask = ThreadPool.schedule(new CheckTimeUp(remaining), interval);
        } else {
            checkTimeUpTask = ThreadPool.schedule(new TimeUp(), interval);
        }
    }

    public void cancelTimer() {
        if (checkTimeUpTask != null) {
            checkTimeUpTask.cancel(true);
        }
    }

    public void cancelEjectDeadPlayer(Player player) {
        final ScheduledFuture<?> task = ejectDeadTasks.remove(player.getObjectId());
        if (task != null) {
            task.cancel(true);
        }
    }

    /**
     * This method is called when player dies inside instance.
     * @param player
     */
    public void notifyDeath(Player player) {
        if (!player.isOnEvent() && (ejectTime > 0)) {
            // Send message
            final SystemMessage sm = new SystemMessage(
                    SystemMessageId
                            .IF_YOU_ARE_NOT_RESURRECTED_WITHIN_S1_MINUTES_YOU_WILL_BE_EXPELLED_FROM_THE_INSTANT_ZONE);
            sm.addInt(ejectTime / 60 / 1000);
            player.sendPacket(sm);

            // Start eject task
            ejectDeadTasks.put(
                    player.getObjectId(),
                    ThreadPool.schedule(
                            () -> {
                                if (player.isDead() && (player.getInstanceId() == id)) {
                                    player.setInstanceId(0);
                                    if (exitLoc != null) {
                                        player.teleToLocation(exitLoc, true);
                                    } else {
                                        player.teleToLocation(TeleportWhereType.TOWN);
                                    }
                                }
                            },
                            ejectTime));
        }
    }

    public class CheckTimeUp implements Runnable {
        private final int remaining;

        public CheckTimeUp(int remaining) {
            this.remaining = remaining;
        }

        @Override
        public void run() {
            doCheckTimeUp(remaining);
        }
    }

    public class TimeUp implements Runnable {
        @Override
        public void run() {
            InstanceManager.getInstance().destroyInstance(getId());
        }
    }

    public boolean isRemoveBuffEnabled() {
        return removeBuffType != InstanceRemoveBuffType.NONE;
    }
}
