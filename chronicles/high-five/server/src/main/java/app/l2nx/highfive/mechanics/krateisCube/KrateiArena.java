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
package app.l2nx.highfive.mechanics.krateisCube;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.custom.PremiumSystemConfig;
import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.actor.instance.KrateisMatchManager;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.managers.games.KrateisCubeManager;
import app.l2nx.highfive.mechanics.script.Quest;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExPCCafePointInfo;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExPVPMatchCCMyRecord;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExPVPMatchCCRecord;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExPVPMatchCCRetire;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * @author LordWinter
 */
public class KrateiArena {
    @Getter
    private final int id;

    @Getter
    private final int minLevel;

    @Getter
    private final int maxLevel;

    @Getter
    private final int minPlayers;

    @Getter
    private final int maxPlayers;

    @Getter
    private KrateisMatchManager manager = null;

    @Getter
    private final Map<Player, KrateiCubePlayer> players = new ConcurrentHashMap<>();

    @Getter
    private final List<Skill> buffList = new ArrayList<>();

    @Getter
    private final List<Door> doorListA = new ArrayList<>();

    @Getter
    private final List<Door> doorListB = new ArrayList<>();

    @Getter
    private final List<Location> waitLoc = new ArrayList<>();

    @Getter
    private final List<Location> battleLoc = new ArrayList<>();

    @Getter
    private final List<Location> watcherLoc = new ArrayList<>();

    @Getter
    private final List<KrateisReward> rewards = new ArrayList<>();

    private final List<Npc> watchers = new ArrayList<>();

    @Getter
    private StatSet params;

    private ScheduledFuture<?> eventTask = null;
    private ScheduledFuture<?> timeTask = null;
    private ScheduledFuture<?> watcherTask = null;
    private ScheduledFuture<?> doorTask = null;

    private boolean doorsRotation = false;
    private boolean watcherRotation = false;

    @Getter
    private boolean battleNow = false;

    @Getter
    private boolean activeNow = false;

    public KrateiArena(int id, int manager, int minLevel, int maxLevel, int minPlayers, int maxPlayers) {
        this.id = id;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        rewards.clear();

        final Spawn spawn = SpawnTable.getInstance().getAnySpawn(manager);
        if (spawn != null) {
            this.manager = (KrateisMatchManager) spawn.getLastSpawn();
            this.manager.setArenaId(getId());
        }
    }

    public void setIsBattleNow(boolean value) {
        battleNow = value;
    }

    public void setReward(KrateisReward ucReward) {
        rewards.add(ucReward);
    }

    public void addBuff(Skill skill) {
        buffList.add(skill);
    }

    public void addDoorA(Door door) {
        doorListA.add(door);
    }

    public void addDoorB(Door door) {
        doorListB.add(door);
    }

    public void addWaitLoc(Location loc) {
        waitLoc.add(loc);
    }

    public void addBattleLoc(Location loc) {
        battleLoc.add(loc);
    }

    public void addWatcherLoc(Location loc) {
        watcherLoc.add(loc);
    }

    public void addParam(StatSet params) {
        this.params = params;
    }

    public boolean addRegisterPlayer(Player player) {
        if (!players.containsKey(player)) {
            final KrateiCubePlayer pl = players.computeIfAbsent(player, _ -> new KrateiCubePlayer(player));
            players.put(player, pl);
            pl.setIsRegister(true);
            player.setKrateiArena(this);
            player.setRegisteredOnEvent(true);
            return true;
        }

        return false;
    }

    public boolean removePlayer(Player player) {
        if (players.containsKey(player)) {
            players.remove(player);
            player.setKrateiArena(null);
            player.setRegisteredOnEvent(false);
            return true;
        }

        return false;
    }

    public boolean isRegisterPlayer(Player player) {
        return players.containsKey(player);
    }

    public void startEvent() {
        if (eventTask != null) {
            eventTask.cancel(false);
        }

        if (timeTask != null) {
            timeTask.cancel(false);
        }

        if (watcherTask != null) {
            watcherTask.cancel(false);
        }

        if (doorTask != null) {
            doorTask.cancel(false);
        }

        doorsRotation = true;
        changeDoorStatus(true);

        if (!getWatcherLoc().isEmpty()) {
            for (Location loc : getWatcherLoc()) {
                if (loc != null) {
                    final Npc npc = Quest.addSpawn(18601, loc, false, 0);
                    npc.disableCoreAI(true);
                    npc.setImmobilized(true);
                    watchers.add(npc);
                }
            }

            watcherRotation = true;
        }

        teleportAllPlayers();
        final long time = 1200000;
        eventTask = ThreadPool.schedule(this::endEvent, time);
        timeTask = ThreadPool.schedule(this::timeLeftInfo, time - 11000);
        watcherTask = ThreadPool.schedule(this::changeWatchers, getParams().getInt("watcherRotationTime") * 1000);
        activeNow = true;
    }

    public void endEvent() {
        if (eventTask != null) {
            eventTask.cancel(true);
            eventTask = null;
        }

        if (watcherTask != null) {
            watcherTask.cancel(true);
            watcherTask = null;
        }

        if (doorTask != null) {
            doorTask.cancel(true);
            doorTask = null;
        }

        watcherRotation = false;
        changeDoorStatus(false);

        if (!watchers.isEmpty()) {
            for (Npc npc : watchers) {
                if (npc != null) {
                    npc.deleteMe();
                }
            }

            watchers.clear();
        }

        final Set<Player> participants = getPlayers().keySet();
        if ((participants != null) && !participants.isEmpty()) {
            for (Player player : participants) {
                if (player != null) {
                    removeAllEffects(player);
                    player.teleToLocation(-70381, -70937, -1428, 0, true);
                    player.setKrateiArena(null);
                    player.setRegisteredOnEvent(false);
                    player.sendPacket(ExPVPMatchCCRetire.STATIC);
                    player.setCanRevive(true);
                }
            }

            checkRewardPlayers(getPlayers());
        }

        players.clear();
        battleNow = false;
        activeNow = false;
        KrateisCubeManager.getInstance().setIsActivate(false);
    }

    private void changeWatchers() {
        if (watcherTask != null) {
            watcherTask.cancel(true);
            watcherTask = null;
        }

        if (!watchers.isEmpty()) {
            for (Npc npc : watchers) {
                if (npc != null) {
                    npc.deleteMe();
                }
            }

            watchers.clear();
        }

        if (!getWatcherLoc().isEmpty()) {
            for (Location loc : getWatcherLoc()) {
                if (loc != null) {
                    final Npc npc = Quest.addSpawn(watcherRotation ? 18602 : 18601, loc, false, 0);
                    npc.disableCoreAI(true);
                    npc.setImmobilized(true);
                    watchers.add(npc);
                }
            }
        }

        if (watcherRotation) {
            watcherRotation = false;
        } else {
            watcherRotation = true;
        }

        watcherTask = ThreadPool.schedule(this::changeWatchers, getParams().getInt("watcherRotationTime") * 1000);
    }

    private Map<Player, Integer> getParticipants() {
        final Map<Player, Integer> participants = new HashMap<>();
        if (!getPlayers().isEmpty()) {
            for (Player player : getPlayers().keySet()) {
                if ((player != null) && !participants.containsKey(player)) {
                    participants.put(player, getPoints(player));
                }
            }

            if (participants.isEmpty()) {
                return null;
            }
        }

        final Map<Player, Integer> sortedMap = participants.entrySet().stream()
                .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, _) -> e1, LinkedHashMap::new));
        return sortedMap;
    }

    private void teleportAllPlayers() {
        if (!getPlayers().isEmpty()) {
            final Map<Player, Integer> participants = getParticipants();
            if (participants == null) {
                return;
            }

            final ExPVPMatchCCRecord p = new ExPVPMatchCCRecord(ExPVPMatchCCRecord.UPDATE, participants);
            for (Player player : getPlayers().keySet()) {
                if (player != null) {
                    player.teleToLocation(
                            getBattleLoc()
                                    .get(ClockBridge.random()
                                            .nextInt(getBattleLoc().size())),
                            true);
                    addEffects(player);
                    player.sendPacket(new ExPVPMatchCCMyRecord(getPoints(player)));
                    player.sendPacket(p);
                    player.setCanRevive(false);
                }
            }
        }
    }

    public void teleportToBattle(Player player) {
        if (player != null) {
            if (getPlayers().containsKey(player)) {
                player.teleToLocation(
                        getBattleLoc()
                                .get(ClockBridge.random().nextInt(getBattleLoc().size())),
                        true);
            }
        } else {
            if (!getPlayers().isEmpty()) {
                for (Player pl : getPlayers().keySet()) {
                    if (pl != null) {
                        teleportToWaitingRoom(pl, false);
                    }
                }
            }
        }
    }

    public void teleportToWaitingRoom(Player player, boolean restoreEffects) {
        if (getPlayers().containsKey(player)) {
            if (restoreEffects) {
                respawnEffects(player);
            }

            player.teleToLocation(
                    getWaitLoc().get(ClockBridge.random().nextInt(getWaitLoc().size())), true);
        }
    }

    private void changeDoorStatus(boolean active) {
        if (doorTask != null) {
            doorTask.cancel(false);
        }

        if (doorsRotation) {
            doorsRotation = false;
        } else {
            doorsRotation = true;
        }

        if (!getDoorListA().isEmpty() && !getDoorListB().isEmpty()) {
            final List<Door> doorsA = (doorsRotation) ? getDoorListA() : getDoorListB();
            final List<Door> doorsB = (doorsRotation) ? getDoorListB() : getDoorListA();
            if (active) {
                changeDoorsStatus(doorsA, true);
                changeDoorsStatus(doorsB, false);
            } else {
                changeDoorsStatus(doorsA, false);
                changeDoorsStatus(doorsB, false);
            }
        }

        doorTask =
                ThreadPool.schedule(() -> changeDoorStatus(active), (getParams().getInt("doorRotationTime") * 1000));
    }

    private void changeDoorsStatus(List<Door> doors, boolean isOpen) {
        for (Door door : doors) {
            if (door != null) {
                if (isOpen) {
                    door.openMe();
                } else {
                    door.closeMe();
                }
            }
        }
    }

    public void addEffects(Player player) {
        player.setCurrentHp(player.getMaxHp());
        player.setCurrentCp(player.getMaxCp());
        player.setCurrentMp(player.getMaxMp());

        if (player.getSummon() != null) {
            player.getSummon().setCurrentHp(player.getSummon().getMaxHp());
            player.getSummon().setCurrentCp(player.getSummon().getMaxCp());
            player.getSummon().setCurrentMp(player.getSummon().getMaxMp());
        }

        if (!getBuffList().isEmpty()) {
            for (Skill skill : getBuffList()) {
                if (skill != null) {
                    skill.applyEffects(player, player);
                    if (player.getSummon() != null) {
                        skill.applyEffects(player.getSummon(), player.getSummon());
                    }
                }
            }
        }

        player.broadcastUserInfo();

        if (player.getSummon() != null) {
            player.getSummon().broadcastInfo();
        }
    }

    private void removeAllEffects(Player player) {
        player.getEffectList().stopAllEffects();
        player.stopAllEffects();
        if (player.getSummon() != null) {
            player.getSummon().stopAllEffects();
        }

        player.broadcastUserInfo();

        if (player.getSummon() != null) {
            player.getSummon().broadcastInfo();
        }
    }

    private void respawnEffects(Player player) {
        removeAllEffects(player);

        if (player.isDead()) {
            player.doRevive(100.);
        }

        player.broadcastStatusUpdate();
        player.broadcastUserInfo();
    }

    private void checkRewardPlayers(Map<Player, KrateiCubePlayer> players) {
        final List<KrateiCubePlayer> playerList = new ArrayList<>();
        for (KrateiCubePlayer info : players.values()) {
            if (info != null) {
                playerList.add(info);
            }
        }

        final Comparator<KrateiCubePlayer> statsComparator = new SortPointInfo();
        playerList.sort(statsComparator);

        ExPVPMatchCCRecord p = null;
        final Map<Player, Integer> participants = getParticipants();
        if (participants != null) {
            p = new ExPVPMatchCCRecord(ExPVPMatchCCRecord.INITIALIZE, participants);
        }

        double dif = 0.05;
        int pos = 0;
        for (KrateiCubePlayer pl : playerList) {
            if ((pl != null) && (pl.getPlayer() != null)) {
                pos++;
                if (p != null) {
                    pl.getPlayer().sendPacket(p);
                }

                if (pl.getPoints() >= getParams().getInt("minPoints")) {
                    final int count = (int) (pl.getPoints() * dif * (1.0 + ((players.size() / pos) * 0.04)));
                    dif -= 0.0016;
                    if (count > 0) {
                        final int exp = count * getParams().getInt("expModifier");
                        final int sp = count * getParams().getInt("spModifier");
                        pl.getPlayer().addExpAndSp(exp, sp);
                        if (!getRewards().isEmpty()) {
                            for (KrateisReward reward : getRewards()) {
                                if (reward != null) {
                                    long amount = reward.allowMidifier() ? count : reward.amount();
                                    if (reward.id() == -100) {
                                        if ((pl.getPlayer().getPcCafePoints() + amount)
                                                > PremiumSystemConfig.PC_CAFE_MAX_POINTS) {
                                            amount = PremiumSystemConfig.PC_CAFE_MAX_POINTS
                                                    - pl.getPlayer().getPcCafePoints();
                                        }

                                        final SystemMessage sm =
                                                new SystemMessage(SystemMessageId.YOU_HAVE_ACQUIRED_S1_PC_CAFE_POINTS);
                                        sm.addInt((int) amount);
                                        pl.getPlayer().sendPacket(sm);
                                        pl.getPlayer().setPcCafePoints((int)
                                                (pl.getPlayer().getPcCafePoints() + amount));
                                        pl.getPlayer()
                                                .sendPacket(new ExPCCafePointInfo(
                                                        pl.getPlayer().getPcCafePoints(), (int) amount, 1));
                                    } else if (reward.id() == -200) {
                                        if (pl.getPlayer().getClan() != null) {
                                            pl.getPlayer().getClan().addReputationScore((int) amount);
                                        }
                                    } else if (reward.id() == -300) {
                                        pl.getPlayer()
                                                .setFame((int) (pl.getPlayer().getFame() + amount));
                                        final SystemMessage sm =
                                                new SystemMessage(SystemMessageId.YOU_HAVE_ACQUIRED_S1_REPUTATION);
                                        sm.addInt((int) amount);
                                        pl.getPlayer().sendPacket(sm);
                                        pl.getPlayer().updateUserInfo();
                                    } else if (reward.id() > 0) {
                                        pl.getPlayer()
                                                .addItem(
                                                        ItemProcessType.REWARD,
                                                        reward.id(),
                                                        amount,
                                                        pl.getPlayer(),
                                                        true);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        playerList.clear();
    }

    private class SortPointInfo implements Comparator<KrateiCubePlayer> {
        @Override
        public int compare(KrateiCubePlayer o1, KrateiCubePlayer o2) {
            return Integer.compare(o2.getPoints(), o1.getPoints());
        }
    }

    public void addPoints(Player player, boolean isPlayer) {
        final KrateiCubePlayer krateiPlayer = getPlayers().get(player);
        if (krateiPlayer != null) {
            krateiPlayer.addPoints(krateiPlayer.getPoints()
                    + (isPlayer
                            ? getParams().getInt("pointPerPlayer")
                            : getParams().getInt("pointPerMob")));
            krateiPlayer.getPlayer().sendPacket(new ExPVPMatchCCMyRecord(krateiPlayer.getPoints()));

            final Map<Player, Integer> participants = getParticipants();
            if (participants != null) {
                final ExPVPMatchCCRecord p = new ExPVPMatchCCRecord(ExPVPMatchCCRecord.UPDATE, participants);
                if (!getPlayers().isEmpty()) {
                    for (Player pl : getPlayers().keySet()) {
                        if (pl != null) {
                            pl.sendPacket(p);
                        }
                    }
                }
            }
        }
    }

    public int getPoints(Player player) {
        int kills = 0;
        final KrateiCubePlayer pl = getPlayers().get(player);
        if (pl != null) {
            kills = pl.getPoints();
        }

        return kills;
    }

    private void timeLeftInfo() {
        if (timeTask != null) {
            timeTask.cancel(true);
            timeTask = null;
        }

        if (!getPlayers().isEmpty()) {
            ThreadPool.schedule(new MessageTask(getPlayers(), 10, 1), 0);
        }
    }

    public void waitTimeInfo() {
        if (!getPlayers().isEmpty()) {
            ThreadPool.schedule(new MessageTask(getPlayers(), 10, 0), 0);
        }
    }

    public class MessageTask implements Runnable {
        private final Map<Player, KrateiCubePlayer> players;
        private int time;
        private final int type;

        public MessageTask(Map<Player, KrateiCubePlayer> players, int time, int type) {
            this.players = players;
            this.time = time;
            this.type = type;
        }

        @Override
        public void run() {
            if (((players != null) && !players.isEmpty()) && (time > 0)) {
                SystemMessage msg = null;
                switch (type) {
                    case 0: {
                        msg = new SystemMessage(SystemMessageId.THE_MATCH_WILL_START_IN_S1_SECOND_S).addInt(time);
                        break;
                    }
                    case 1: {
                        msg = new SystemMessage(SystemMessageId.THE_GAME_WILL_END_IN_S1_SECOND_S_2).addInt(time);
                        break;
                    }
                }

                for (Player player : players.keySet()) {
                    if ((player != null) && (msg != null)) {
                        player.sendPacket(msg);
                    }
                }

                time--;
                if (time > 0) {
                    ThreadPool.schedule(new MessageTask(players, time, type), 1000);
                }
            }
        }
    }

    public void setRespawnTask(Player player) {
        if (player != null) {
            ThreadPool.schedule(new RespawnTask(player, getParams().getInt("respawnTime")), 0);
        }
    }

    private class RespawnTask implements Runnable {
        private final Player player;
        private int time;

        public RespawnTask(Player player, int time) {
            this.player = player;
            this.time = time;
        }

        @Override
        public void run() {
            if ((player != null) && (time > 0)) {
                player.sendPacket(new SystemMessage(
                                SystemMessageId.RESURRECTION_WILL_TAKE_PLACE_IN_THE_WAITING_ROOM_AFTER_S1_SECONDS)
                        .addInt(time));
                time--;
                if (time > 0) {
                    ThreadPool.schedule(new RespawnTask(player, time), 1000);
                } else {
                    respawnEffects(player);
                    player.teleToLocation(
                            getWaitLoc()
                                    .get(ClockBridge.random()
                                            .nextInt(getWaitLoc().size())),
                            true);
                }
            }
        }
    }
}
