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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.holders.InstanceReenterTimeHolder;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.IntSnapshot;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
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
import app.l2nx.highfive.network.protocol.p267.serverpackets.CreatureSay;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.util.ArrayUtil;
import app.l2nx.highfive.util.StatSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Main class for game instances.
 * @author evill33t, GodKratos
 */
@Slf4j
@SingleWriter
public class Instance {

    private final int id;

    private int templateId = -1;

    private int ejectTime = GeneralConfig.EJECT_DEAD_PLAYER_TIME;
    /** Allow random walk for NPCs, global parameter. */
    private boolean allowRandomWalk = true;

    // Entry and spawn order: timer notices, ejections and NPC lookups follow it on every run.
    private final IntLinkedOpenHashSet players = new IntLinkedOpenHashSet();

    private final ObjectLinkedOpenHashSet<Npc> npcs = new ObjectLinkedOpenHashSet<>();

    // By door id, so a door the template lists twice spawns once.
    private final Int2ObjectLinkedOpenHashMap<StatSet> doorTemplates = new Int2ObjectLinkedOpenHashMap<>();
    private final Int2ObjectLinkedOpenHashMap<Door> doors = new Int2ObjectLinkedOpenHashMap<>();
    private final List<StatSet> spawnTemplates = new ObjectArrayList<>();

    private final List<Location> enterLocs = new ObjectArrayList<>();

    private Location exitLoc = null;

    private boolean summonAllowed = true;

    private long emptyDestroyTime = -1;

    private long lastLeft = -1;

    private long instanceStartTime = -1;

    private long instanceEndTime = -1;

    private boolean pvP = false;

    private boolean showTimer = false;

    private boolean timerIncrease = true;

    private String timerText;

    private InstanceReenterType reenterType = InstanceReenterType.NONE;

    private final List<InstanceReenterTimeHolder> reenterData = new ObjectArrayList<>();

    private InstanceRemoveBuffType removeBuffType = InstanceRemoveBuffType.NONE;

    private final IntArrayList buffExceptions = new IntArrayList();

    protected ScheduledFuture<?> checkTimeUpTask = null;
    private final Int2ObjectOpenHashMap<ScheduledFuture<?>> ejectDeadTasks = new Int2ObjectOpenHashMap<>();

    public Instance(int id) {
        this.id = id;
        instanceStartTime = ClockBridge.gameMillis();
    }

    public int getId() {
        WorldPartition.checkOwner();
        return id;
    }

    public int getTemplateId() {
        WorldPartition.checkOwner();
        return templateId;
    }

    public int getEjectTime() {
        WorldPartition.checkOwner();
        return ejectTime;
    }

    public void setEjectTime(int ejectTime) {
        WorldPartition.checkOwner();
        this.ejectTime = ejectTime;
    }

    /**
     * @return a copy of the exit location, {@code null} for none
     */
    public Location getExitLoc() {
        WorldPartition.checkOwner();
        return exitLoc == null ? null : exitLoc.clone();
    }

    public void setExitLoc(Location exitLoc) {
        WorldPartition.checkOwner();
        this.exitLoc = exitLoc == null ? null : exitLoc.clone();
    }

    public boolean isSummonAllowed() {
        WorldPartition.checkOwner();
        return summonAllowed;
    }

    public void setEmptyDestroyTime(long emptyDestroyTime) {
        WorldPartition.checkOwner();
        this.emptyDestroyTime = emptyDestroyTime;
    }

    public long getInstanceStartTime() {
        WorldPartition.checkOwner();
        return instanceStartTime;
    }

    public long getInstanceEndTime() {
        WorldPartition.checkOwner();
        return instanceEndTime;
    }

    public boolean isPvP() {
        WorldPartition.checkOwner();
        return pvP;
    }

    public void setPvP(boolean pvP) {
        WorldPartition.checkOwner();
        this.pvP = pvP;
    }

    public boolean isShowTimer() {
        WorldPartition.checkOwner();
        return showTimer;
    }

    public boolean isTimerIncrease() {
        WorldPartition.checkOwner();
        return timerIncrease;
    }

    /**
     * @return the text shown with the timer, {@code null} when the template sets none
     */
    public String getTimerText() {
        WorldPartition.checkOwner();
        return timerText;
    }

    public InstanceReenterType getReenterType() {
        WorldPartition.checkOwner();
        return reenterType;
    }

    public void setReenterType(InstanceReenterType reenterType) {
        WorldPartition.checkOwner();
        this.reenterType = reenterType;
    }

    /**
     * @return a read-only snapshot of the reset times in template order
     */
    public List<InstanceReenterTimeHolder> getReenterData() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(reenterData));
    }

    public InstanceRemoveBuffType getRemoveBuffType() {
        WorldPartition.checkOwner();
        return removeBuffType;
    }

    /**
     * @return whether the template lists the skill for its remove buff type: kept by a whitelist, removed by a
     *     blacklist
     */
    public boolean isBuffException(int skillId) {
        WorldPartition.checkOwner();
        return buffExceptions.contains(skillId);
    }

    /**
     * Sets the status for the instance for summon friend type skills
     * @param value
     */
    public void setAllowSummon(boolean value) {
        WorldPartition.checkOwner();
        summonAllowed = value;
    }

    /**
     * Set the instance duration task
     * @param duration in milliseconds
     */
    public void setDuration(int duration) {
        WorldPartition.checkOwner();
        if (checkTimeUpTask != null) {
            checkTimeUpTask.cancel(true);
        }

        checkTimeUpTask = WorldPartition.loop().schedule(new CheckTimeUp(duration), 500, TimeUnit.MILLISECONDS);
        instanceEndTime = ClockBridge.gameMillis() + duration + 500;
    }

    /**
     * Checks if the player exists within this instance
     * @param objectId
     * @return true if player exists in instance
     */
    public boolean containsPlayer(int objectId) {
        WorldPartition.checkOwner();
        return players.contains(objectId);
    }

    /**
     * Adds the specified player to the instance
     * @param objectId Players object ID
     */
    public void addPlayer(int objectId) {
        WorldPartition.checkOwner();
        players.add(objectId);
    }

    /**
     * Removes the specified player from the instance list.
     * @param objectId the player's object Id
     */
    public void removePlayer(int objectId) {
        WorldPartition.checkOwner();
        players.remove(objectId);
        if (!players.isEmpty() || (emptyDestroyTime < 0)) {
            return;
        }

        lastLeft = ClockBridge.gameMillis();
        setDuration((int) (instanceEndTime - ClockBridge.gameMillis() - 500));
    }

    public int getPlayerCount() {
        WorldPartition.checkOwner();
        return players.size();
    }

    /**
     * Adds the object ids of the players inside to the target in entry order. A caller that walks them passes a
     * {@link ScratchBuffers#intEmpty()} lease.
     */
    public void collectPlayers(IntCollection target) {
        WorldPartition.checkOwner();
        target.addAll(players);
    }

    public void addNpc(Npc npc) {
        WorldPartition.checkOwner();
        npcs.add(npc);
    }

    public void removeNpc(Npc npc) {
        WorldPartition.checkOwner();
        if (npc.getSpawn() != null) {
            npc.getSpawn().stopRespawn();
        }

        npcs.remove(npc);
    }

    /**
     * @return a read-only snapshot of the NPCs in spawn order
     */
    public List<Npc> getNpcs() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(npcs));
    }

    /**
     * @return the first NPC spawned with the template id, {@code null} for none
     */
    public Npc getNpc(int npcId) {
        WorldPartition.checkOwner();
        for (Npc npc : npcs) {
            if (npc.getId() == npcId) {
                return npc;
            }
        }

        return null;
    }

    /**
     * Adds the NPCs of the type with one of the template ids to the target in spawn order.
     * @param ids the template ids to match, none when empty
     * @param aliveOnly whether to skip NPCs without HP
     */
    public <T> void collectNpcs(Class<T> type, int[] ids, boolean aliveOnly, List<? super T> target) {
        WorldPartition.checkOwner();
        if (ids.length > 0) {
            collect(type, ids, aliveOnly, target);
        }
    }

    /**
     * Adds the NPCs of the type with any template id to the target in spawn order.
     * @param aliveOnly whether to skip NPCs without HP
     */
    public <T> void collectNpcs(Class<T> type, boolean aliveOnly, List<? super T> target) {
        WorldPartition.checkOwner();
        collect(type, null, aliveOnly, target);
    }

    private <T> void collect(Class<T> type, int[] ids, boolean aliveOnly, List<? super T> target) {
        for (Npc npc : npcs) {
            if (((ids == null) || ArrayUtil.contains(ids, npc.getId()))
                    && (!aliveOnly || (npc.getCurrentHp() > 0))
                    && type.isInstance(npc)) {
                target.add(type.cast(npc));
            }
        }
    }

    /**
     * Adds a door into the instance
     * @param set - StatSet for initializing door
     */
    public void addDoor(StatSet set) {
        WorldPartition.checkOwner();
        final int doorId = set.getInt("DoorId");
        if (doorTemplates.containsKey(doorId)) {
            log.warn("Door ID {} already exists in instance {}", doorId, id);
            return;
        }

        doorTemplates.put(doorId, set);
    }

    /**
     * Spawn doors inside instance world.
     */
    public void spawnDoors() {
        WorldPartition.checkOwner();
        // The templates change only while the instance template loads, so spawning walks them live.
        for (StatSet template : doorTemplates.values()) {
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

    /**
     * @return a read-only snapshot of the doors in spawn order
     */
    public List<Door> getDoors() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(doors.values()));
    }

    /**
     * Walks the live doors without a copy, since movement and line-of-sight checks ask on every step: the predicate
     * must be a pure filter that opens, closes, spawns or removes no door.
     */
    public boolean anyDoor(Predicate<Door> test) {
        WorldPartition.checkOwner();
        for (Door door : doors.values()) {
            if (test.test(door)) {
                return true;
            }
        }

        return false;
    }

    public Door getDoor(int id) {
        WorldPartition.checkOwner();
        return doors.get(id);
    }

    /**
     * Sets the spawn location for this instance to be used when enter in instance
     * @param loc
     */
    public void addEnterLoc(Location loc) {
        WorldPartition.checkOwner();
        enterLocs.add(loc.clone());
    }

    /**
     * @return a read-only list of copies of the enter locations in template order
     */
    public List<Location> getEnterLocs() {
        WorldPartition.checkOwner();
        return Location.copiesOf(enterLocs);
    }

    public void removePlayers() {
        WorldPartition.checkOwner();
        // A teleport out takes the player off the list through his instance id change.
        try (IntSnapshot walk = ScratchBuffers.intSnapshot(players)) {
            for (int i = 0; i < walk.size(); i++) {
                final Player player = World.getPlayer(walk.getInt(i));
                if (player != null) {
                    eject(player);
                }
            }
        }

        players.clear();
    }

    /** Takes the player out to the exit location, or to town without one; a player outside this instance stays. */
    public void eject(Player player) {
        WorldPartition.checkOwner();
        if (player.getInstanceId() != id) {
            return;
        }

        player.setInstanceId(0);
        if (exitLoc != null) {
            player.teleToLocation(exitLoc.clone(), true);
        } else {
            player.teleToLocation(TeleportWhereType.TOWN);
        }
    }

    public void removeNpcs() {
        WorldPartition.checkOwner();
        try (Snapshot<Npc> walk = ScratchBuffers.snapshot(npcs)) {
            for (Npc mob : walk) {
                if (mob != null) {
                    if (mob.getSpawn() != null) {
                        mob.getSpawn().stopRespawn();
                    }

                    mob.deleteMe();
                }
            }
        }

        npcs.clear();
    }

    public void removeDoors() {
        WorldPartition.checkOwner();
        try (Snapshot<Door> walk = ScratchBuffers.snapshot(doors.values())) {
            for (Door door : walk) {
                if (door != null) {
                    final WorldRegion region = door.getWorldRegion();
                    door.decayMe();

                    if (region != null) {
                        region.removeVisibleObject(door);
                    }

                    World.removeObject(door);
                }
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
        WorldPartition.checkOwner();
        final List<Npc> spawnedNpcs = new ArrayList<>();
        // The templates change only while the instance template loads, so spawning walks them live.
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
        WorldPartition.checkOwner();
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
                        checkTimeUpTask = WorldPartition.loop()
                                .schedule(
                                        new CheckTimeUp(Integer.parseInt(a.getNodeValue()) * 60000),
                                        15000,
                                        TimeUnit.MILLISECONDS);
                        instanceEndTime = ClockBridge.gameMillis() + (Long.parseLong(a.getNodeValue()) * 60000) + 15000;
                    }
                    break;
                }
                case "allowsummon": {
                    a = n.getAttributes().getNamedItem("val");
                    if (a != null) {
                        summonAllowed = Boolean.parseBoolean(a.getNodeValue());
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
                        pvP = Boolean.parseBoolean(a.getNodeValue());
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
                    enterLocs.clear();
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
                                buffExceptions.add(Integer.parseInt(a.getNodeValue()));
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
            final Long emptyTimeLeft = (lastLeft + emptyDestroyTime) - ClockBridge.gameMillis();
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
            sendExpiryNotice(timeLeft);
            remaining -= 300000;
        } else if (remaining > 60000) {
            timeLeft = remaining / 60000;
            interval = 60000;
            sendExpiryNotice(timeLeft);
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
            final CreatureSay notice = cs;
            forEachPlayerInside(false, player -> player.sendPacket(notice));
        }

        cancelTimer();
        if (remaining >= 10000) {
            checkTimeUpTask =
                    WorldPartition.loop().schedule(new CheckTimeUp(remaining), interval, TimeUnit.MILLISECONDS);
        } else {
            checkTimeUpTask = WorldPartition.loop().schedule(new TimeUp(), interval, TimeUnit.MILLISECONDS);
        }
    }

    private void sendExpiryNotice(int minutesLeft) {
        final SystemMessage sm = new SystemMessage(
                SystemMessageId
                        .THIS_DUNGEON_WILL_EXPIRE_IN_S1_MINUTE_S_YOU_WILL_BE_FORCED_OUT_OF_THE_DUNGEON_WHEN_THE_TIME_EXPIRES);
        sm.addString(Integer.toString(minutesLeft));
        sm.sendInBroadcast();
        try {
            forEachPlayerInside(true, player -> player.sendPacket(sm));
        } finally {
            sm.releaseBroadcast();
        }
    }

    private void forEachPlayerInside(boolean onlineOnly, Consumer<Player> action) {
        try (IntSnapshot walk = ScratchBuffers.intSnapshot(players)) {
            for (int i = 0; i < walk.size(); i++) {
                final Player player = World.getPlayer(walk.getInt(i));
                if ((player != null) && (!onlineOnly || player.isOnline()) && (player.getInstanceId() == id)) {
                    action.accept(player);
                }
            }
        }
    }

    public void cancelTimer() {
        WorldPartition.checkOwner();
        if (checkTimeUpTask != null) {
            checkTimeUpTask.cancel(true);
        }
    }

    public void cancelEjectDeadPlayer(Player player) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        if (!player.isOnEvent() && (ejectTime > 0)) {
            // Send message
            final SystemMessage sm = new SystemMessage(
                    SystemMessageId
                            .IF_YOU_ARE_NOT_RESURRECTED_WITHIN_S1_MINUTES_YOU_WILL_BE_EXPELLED_FROM_THE_INSTANT_ZONE);
            sm.addInt(ejectTime / 60 / 1000);
            player.sendPacket(sm);

            // A second death before the first ejection keeps one ejection, timed from the last death.
            cancelEjectDeadPlayer(player);
            final int objectId = player.getObjectId();
            ejectDeadTasks.put(
                    objectId,
                    WorldPartition.loop()
                            .schedule(
                                    () -> {
                                        ejectDeadTasks.remove(objectId);
                                        if (player.isDead()) {
                                            eject(player);
                                        }
                                    },
                                    ejectTime,
                                    TimeUnit.MILLISECONDS));
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
            InstanceManager.getInstance().destroyInstance(id);
        }
    }

    public boolean isRemoveBuffEnabled() {
        WorldPartition.checkOwner();
        return removeBuffType != InstanceRemoveBuffType.NONE;
    }
}
