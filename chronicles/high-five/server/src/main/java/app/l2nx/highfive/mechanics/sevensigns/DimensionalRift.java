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
package app.l2nx.highfive.mechanics.sevensigns;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.managers.DimensionalRiftManager;
import app.l2nx.highfive.managers.ScriptManager;
import app.l2nx.highfive.mechanics.script.Quest;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.network.serverpackets.Earthquake;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kombat, BazookaRpm
 */
public class DimensionalRift {
    @Getter
    protected byte type;

    protected Party party;
    protected List<Byte> completedRooms = new ArrayList<>();
    private static final long FIVE_SECONDS = 5000;
    protected byte currentJump = 1; // Initialize to 1 to count the initial room.

    private Timer teleporterTimer;
    private TimerTask teleporterTimerTask;

    @Getter
    @Setter
    private Timer spawnTimer;

    @Getter
    @Setter
    private TimerTask spawnTimerTask;

    private Future<?> earthQuakeTask;

    @Getter
    protected byte currentRoom = -1;

    @Getter
    protected Collection<Player> deadMemberList = ConcurrentHashMap.newKeySet();

    @Getter
    protected Collection<Player> revivedAtWaitingRoom = ConcurrentHashMap.newKeySet();

    private boolean bossRoom = false;

    public DimensionalRift(Party party, byte type, byte room) {
        DimensionalRiftManager.getInstance().getRoom(type, room).setPartyInside(true);

        this.type = type;
        this.party = party;
        currentRoom = room;

        final Location coords = getRoomCoord(room);
        party.setDimensionalRift(this);
        for (Player p : party.getMembers()) {
            final Quest riftQuest = ScriptManager.getInstance().getQuest(635);
            if (riftQuest != null) {
                QuestState qs = p.getQuestState(riftQuest.getName());
                if (qs == null) {
                    qs = riftQuest.newQuestState(p);
                }

                if (!qs.isStarted()) {
                    qs.startQuest();
                }
            }

            p.teleToLocation(coords);
        }

        createSpawnTimer(currentRoom);
        createTeleporterTimer(true);
    }

    protected void createTeleporterTimer(boolean reasonTP) {
        if (party == null) {
            return;
        }

        if (teleporterTimerTask != null) {
            teleporterTimerTask.cancel();
            teleporterTimerTask = null;
        }

        if (teleporterTimer != null) {
            teleporterTimer.cancel();
            teleporterTimer = null;
        }

        if (earthQuakeTask != null) {
            earthQuakeTask.cancel(false);
            earthQuakeTask = null;
        }

        teleporterTimer = new Timer();
        teleporterTimerTask = new TimerTask() {
            @Override
            public void run() {
                if (currentRoom > -1) {
                    DimensionalRiftManager.getInstance()
                            .getRoom(type, currentRoom)
                            .unspawn()
                            .setPartyInside(false);
                }

                // Check if there are still available jumps.
                if (reasonTP && (currentJump < getMaxJumps()) && (party.getMemberCount() > deadMemberList.size())) {
                    currentJump++;

                    completedRooms.add(currentRoom);
                    currentRoom = -1;

                    for (Player p : party.getMembers()) {
                        if (!revivedAtWaitingRoom.contains(p)) {
                            teleportToNextRoom(p);
                        }
                    }

                    createTeleporterTimer(true);
                    createSpawnTimer(currentRoom);
                } else {
                    for (Player p : party.getMembers()) {
                        if (!revivedAtWaitingRoom.contains(p)) {
                            teleportToWaitingRoom(p);
                        }
                    }

                    killRift();
                    cancel();
                }
            }
        };

        if (reasonTP) {
            final long jumpTime = calcTimeToNextJump();
            teleporterTimer.schedule(teleporterTimerTask, jumpTime); // Teleporter task, 8-10 minutes

            earthQuakeTask = ThreadPool.schedule(
                    () -> {
                        for (Player p : party.getMembers()) {
                            if (!revivedAtWaitingRoom.contains(p)) {
                                p.sendPacket(new Earthquake(p.getX(), p.getY(), p.getZ(), 65, 9));
                            }
                        }
                    },
                    jumpTime - 7000);
        } else {
            teleporterTimer.schedule(teleporterTimerTask, FIVE_SECONDS); // incorrect party member invited.
        }
    }

    public void createSpawnTimer(byte room) {
        if (spawnTimerTask != null) {
            spawnTimerTask.cancel();
            spawnTimerTask = null;
        }

        if (spawnTimer != null) {
            spawnTimer.cancel();
            spawnTimer = null;
        }

        spawnTimer = new Timer();
        spawnTimerTask = new TimerTask() {
            @Override
            public void run() {
                DimensionalRiftManager.getInstance().getRoom(type, room).spawn();
            }
        };

        spawnTimer.schedule(spawnTimerTask, GeneralConfig.RIFT_SPAWN_DELAY);
    }

    public void partyMemberInvited() {
        createTeleporterTimer(false);
    }

    public void partyMemberExited(Player player) {
        deadMemberList.remove(player);

        revivedAtWaitingRoom.remove(player);

        if ((party.getMemberCount() < GeneralConfig.RIFT_MIN_PARTY_SIZE) || (party.getMemberCount() == 1)) {
            for (Player p : party.getMembers()) {
                teleportToWaitingRoom(p);
            }

            killRift();
        }
    }

    public void manualTeleport(Player player, Npc npc) {
        final Party party = player.getParty();
        if ((party == null) || !party.isInDimensionalRift()) {
            return;
        }

        if (player.getObjectId() != party.getLeaderObjectId()) {
            DimensionalRiftManager.getInstance()
                    .showHtmlFile(player, "data/html/seven_signs/rift/NotPartyLeader.htm", npc);
            return;
        }

        // Check if the maximum jump limit has been reached.
        if (currentJump >= getMaxJumps()) {
            DimensionalRiftManager.getInstance()
                    .showHtmlFile(player, "data/html/seven_signs/rift/AlreadyTeleported.htm", npc);
            return;
        }

        // Increase the jump counter.
        currentJump++;

        DimensionalRiftManager.getInstance()
                .getRoom(type, currentRoom)
                .unspawn()
                .setPartyInside(false);
        completedRooms.add(currentRoom);
        currentRoom = -1;

        for (Player p : this.party.getMembers()) {
            teleportToNextRoom(p);
        }

        DimensionalRiftManager.getInstance().getRoom(type, currentRoom).setPartyInside(true);

        createSpawnTimer(currentRoom);
        createTeleporterTimer(true);
    }

    public void manualExitRift(Player player, Npc npc) {
        final Party party = player.getParty();
        if ((party == null) || !party.isInDimensionalRift()) {
            return;
        }

        if (player.getObjectId() != party.getLeaderObjectId()) {
            DimensionalRiftManager.getInstance()
                    .showHtmlFile(player, "data/html/seven_signs/rift/NotPartyLeader.htm", npc);
            return;
        }

        for (Player p : party.getMembers()) {
            teleportToWaitingRoom(p);
        }

        killRift();
    }

    protected void teleportToNextRoom(Player player) {
        if (currentRoom == -1) {
            List<Byte> emptyRooms;
            do {
                emptyRooms = DimensionalRiftManager.getInstance().getFreeRooms(type);

                // Do not tp in the same room a second time
                emptyRooms.removeAll(completedRooms);

                // If no room left, find any empty
                if (emptyRooms.isEmpty()) {
                    emptyRooms = DimensionalRiftManager.getInstance().getFreeRooms(type);
                }

                currentRoom = emptyRooms.get(ThreadLocalRandom.current().nextInt(emptyRooms.size()));
            } while (DimensionalRiftManager.getInstance()
                    .getRoom(type, currentRoom)
                    .isPartyInside());
        }

        DimensionalRiftManager.getInstance().getRoom(type, currentRoom).setPartyInside(true);
        checkBossRoom(currentRoom);
        player.teleToLocation(getRoomCoord(currentRoom));
    }

    protected void teleportToWaitingRoom(Player player) {
        DimensionalRiftManager.getInstance().teleportToWaitingRoom(player);
        final Quest riftQuest = ScriptManager.getInstance().getQuest(635);
        if (riftQuest != null) {
            final QuestState qs = player.getQuestState(riftQuest.getName());
            if ((qs != null) && qs.isCond(1)) {
                qs.exitQuest(true, true);
            }
        }
    }

    public void killRift() {
        completedRooms.clear();

        if (party != null) {
            party.setDimensionalRift(null);
        }

        party = null;
        revivedAtWaitingRoom = null;
        deadMemberList = null;

        if (earthQuakeTask != null) {
            earthQuakeTask.cancel(false);
            earthQuakeTask = null;
        }

        DimensionalRiftManager.getInstance()
                .getRoom(type, currentRoom)
                .unspawn()
                .setPartyInside(false);
        DimensionalRiftManager.getInstance().killRift(this);
    }

    public Timer getTeleportTimer() {
        return teleporterTimer;
    }

    public TimerTask getTeleportTimerTask() {
        return teleporterTimerTask;
    }

    public void setTeleportTimer(Timer t) {
        teleporterTimer = t;
    }

    public void setTeleportTimerTask(TimerTask tt) {
        teleporterTimerTask = tt;
    }

    private long calcTimeToNextJump() {
        final int minTime = GeneralConfig.RIFT_AUTO_JUMPS_TIME_MIN;
        final int maxTime = GeneralConfig.RIFT_AUTO_JUMPS_TIME_MAX;
        final int time =
                (minTime < maxTime ? ThreadLocalRandom.current().nextInt(minTime, maxTime + 1) : minTime) * 1000;

        if (bossRoom) {
            return (long) (time * GeneralConfig.RIFT_BOSS_ROOM_TIME_MUTIPLY);
        }

        return time;
    }

    public void memberDead(Player player) {
        if (!deadMemberList.contains(player)) {
            deadMemberList.add(player);
        }
    }

    public void memberRessurected(Player player) {
        deadMemberList.remove(player);
    }

    public void usedTeleport(Player player) {
        if (!revivedAtWaitingRoom.contains(player)) {
            revivedAtWaitingRoom.add(player);
        }

        if (!deadMemberList.contains(player)) {
            deadMemberList.add(player);
        }

        if ((party.getMemberCount() - revivedAtWaitingRoom.size()) < GeneralConfig.RIFT_MIN_PARTY_SIZE) {
            // int pcm = _party.getMemberCount();
            // int rev = revivedInWaitingRoom.size();
            // int min = Config.RIFT_MIN_PARTY_SIZE;

            for (Player p : party.getMembers()) {
                if ((p != null) && !revivedAtWaitingRoom.contains(p)) {
                    teleportToWaitingRoom(p);
                }
            }

            killRift();
        }
    }

    public void checkBossRoom(byte room) {
        bossRoom = DimensionalRiftManager.getInstance().getRoom(type, room).isBossRoom();
    }

    public Location getRoomCoord(byte room) {
        return DimensionalRiftManager.getInstance().getRoom(type, room).getTeleportCoorinates();
    }

    public byte getMaxJumps() {
        if ((GeneralConfig.RIFT_MAX_JUMPS <= 8) && (GeneralConfig.RIFT_MAX_JUMPS >= 1)) {
            return (byte) GeneralConfig.RIFT_MAX_JUMPS;
        }

        return 4;
    }
}
