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
package app.l2nx.highfive.mechanics.undergroundColiseum;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.UndergroundColiseumConfig;
import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.Team;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.mechanics.variables.PlayerVariables;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExPVPMatchRecord;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExPVPMatchUserDie;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExShowScreenMessage;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;

public class UCArena {
    private static final long MINUTES_IN_MILISECONDS = 10 * 60000;

    @Getter
    private final int id;

    @Getter
    private final int minLevel;

    @Getter
    private final int maxLevel;

    @Getter
    private final UCPoint[] points = new UCPoint[4];

    @Getter
    private final UCTeam[] teams = new UCTeam[2];

    @Getter
    private Npc manager = null;

    private ScheduledFuture<?> taskFuture = null;

    @Getter
    private final List<UCWaiting> waitingList = new CopyOnWriteArrayList<>();

    @Getter
    private final List<UCReward> rewards = new ArrayList<>();

    @Getter
    private boolean battleNow = false;

    public UCArena(int id, int curator, int minLevel, int maxLevel) {
        this.id = id;
        final Spawn spawn = SpawnTable.getInstance().getAnySpawn(curator);
        if (spawn != null) {
            manager = spawn.getLastSpawn();
        }

        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        rewards.clear();
    }

    public void setUCPoint(int index, UCPoint point) {
        if (index > 4) {
            return;
        }

        points[index] = point;
    }

    public void setUCTeam(int index, UCTeam team) {
        if (index > 2) {
            return;
        }

        teams[index] = team;
    }

    public void switchStatus(boolean start) {
        if ((taskFuture == null) && start) {
            runNewTask(false);
        } else {
            if (taskFuture != null) {
                taskFuture.cancel(true);
                taskFuture = null;
            }

            generateWinner();
            removeTeams();
            for (UCTeam team : getTeams()) {
                team.cleanUp();
            }

            for (UCPoint point : getPoints()) {
                point.actionDoors(false);
                point.getPlayers().clear();
            }

            battleNow = false;
        }
    }

    public void runNewTask(boolean isFullTime) {
        final long time = isFullTime ? MINUTES_IN_MILISECONDS : MINUTES_IN_MILISECONDS - 60000L;
        taskFuture = ThreadPool.schedule(new UCRunningTask(this), time);
    }

    public void runTaskNow() {
        if (taskFuture != null) {
            taskFuture.cancel(true);
            taskFuture = null;
        }

        taskFuture = ThreadPool.schedule(new UCRunningTask(this), 0);
    }

    public void generateWinner() {
        final UCTeam blueTeam = teams[0];
        final UCTeam redTeam = teams[1];
        UCTeam winnerTeam = null;

        if ((blueTeam.getStatus() == UCTeam.WIN) || (redTeam.getStatus() == UCTeam.WIN)) {
            winnerTeam = blueTeam.getStatus() == UCTeam.WIN ? blueTeam : redTeam;
        } else {
            if ((blueTeam.getParty() == null) && (redTeam.getParty() != null)) {
                redTeam.setStatus(UCTeam.WIN);
                winnerTeam = redTeam;
            } else if ((redTeam.getParty() == null) && (blueTeam.getParty() != null)) {
                blueTeam.setStatus(UCTeam.WIN);
                winnerTeam = blueTeam;
            } else if ((redTeam.getParty() != null) && (blueTeam.getParty() != null)) {
                if (blueTeam.getKillCount() > redTeam.getKillCount()) {
                    blueTeam.setStatus(UCTeam.WIN);
                    redTeam.setStatus(UCTeam.FAIL);
                    winnerTeam = blueTeam;
                } else if (redTeam.getKillCount() > blueTeam.getKillCount()) {
                    blueTeam.setStatus(UCTeam.FAIL);
                    redTeam.setStatus(UCTeam.WIN);
                    winnerTeam = redTeam;
                } else if (blueTeam.getKillCount() == redTeam.getKillCount()) {
                    if (blueTeam.getRegisterTime() > redTeam.getRegisterTime()) {
                        blueTeam.setStatus(UCTeam.FAIL);
                        redTeam.setStatus(UCTeam.WIN);
                        winnerTeam = redTeam;
                    } else {
                        blueTeam.setStatus(UCTeam.WIN);
                        redTeam.setStatus(UCTeam.FAIL);
                        winnerTeam = blueTeam;
                    }
                }
            }
        }

        if (winnerTeam != null) {
            broadcastRecord(ExPVPMatchRecord.FINISH, (winnerTeam.getIndex() + 1));
        } else {
            broadcastRecord(ExPVPMatchRecord.FINISH, 0);
        }

        blueTeam.setLastParty(redTeam.getParty());
        redTeam.setLastParty(blueTeam.getParty());
    }

    public void broadcastToAll(ServerPacket packet) {
        for (UCTeam team : getTeams()) {
            final Party party = team.getParty();
            if (party != null) {
                for (Player member : party.getMembers()) {
                    if (member != null) {
                        member.sendPacket(packet);
                    }
                }
            }
        }
    }

    public void prepareStart() {
        battleNow = true;
        broadcastToAll(new ExShowScreenMessage(
                NpcStringId.MATCH_BEGINS_IN_S1_MINUTE_S_PLEASE_GATHER_AROUND_THE_ADMINISTRATOR, 2, 5000, "1"));
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
        }

        broadcastToAll(new ExShowScreenMessage(NpcStringId.S1_SECOND_S_REMAINING, 2, 5000, "30"));
        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
        }

        broadcastToAll(new ExShowScreenMessage(NpcStringId.S1_SECOND_S_REMAINING, 2, 3000, "10"));

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
        }

        broadcastToAll(new ExShowScreenMessage(NpcStringId.S1_SECOND_S_REMAINING, 2, 1000, "5"));

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        broadcastToAll(new ExShowScreenMessage(NpcStringId.S1_SECOND_S_REMAINING, 2, 1000, "4"));

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        broadcastToAll(new ExShowScreenMessage(NpcStringId.S1_SECOND_S_REMAINING, 2, 1000, "3"));

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        broadcastToAll(new ExShowScreenMessage(NpcStringId.S1_SECOND_S_REMAINING, 2, 1000, "2"));

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        broadcastToAll(new ExShowScreenMessage(NpcStringId.S1_SECOND_S_REMAINING, 2, 1000, "1"));

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        boolean isValid = true;
        for (UCTeam team : teams) {
            if (team.getParty() == null) {
                isValid = false;
                continue;
            }

            if (team.getParty().getMemberCount() < UndergroundColiseumConfig.UC_PARTY_SIZE) {
                isValid = false;
                continue;
            }

            for (Player pl : team.getParty().getMembers()) {
                if ((pl != null) && (pl.calculateDistance3D(manager) > PlayerConfig.ALT_PARTY_RANGE)) {
                    isValid = false;
                    break;
                }
            }
        }

        if (!isValid) {
            broadcastToAll(new ExShowScreenMessage(
                    NpcStringId.THE_MATCH_IS_AUTOMATICALLY_CANCELED_BECAUSE_YOU_ARE_TOO_FAR_FROM_THE_ADMISSION_MANAGER,
                    2,
                    5000));
            for (UCTeam team : teams) {
                team.setParty(null);
                team.setRegisterTime(0);
            }

            battleNow = false;
            runNewTask(false);
            return;
        }

        runNewTask(true);
        splitMembersAndTeleport();
        startFight();
    }

    public void splitMembersAndTeleport() {
        final UCPoint[] positions = getPoints();
        for (UCPoint point : positions) {
            point.getPlayers().clear();
        }

        broadcastRecord(ExPVPMatchRecord.START, 0);

        for (UCTeam team : getTeams()) {
            final Party party = team.getParty();
            if (party != null) {
                int i = 0;
                for (Player player : party.getMembers()) {
                    if (player != null) {
                        player.setUCState(Player.UC_STATE_POINT);
                        positions[i].teleportPlayer(player);
                        i++;
                        if (i >= 3) {
                            i = 0;
                        }
                    }
                }
            }
        }

        broadcastRecord(ExPVPMatchRecord.UPDATE, 0);
    }

    public void broadcastRecord(int type, int teamType) {
        final ExPVPMatchRecord packet = new ExPVPMatchRecord(type, teamType, this);
        final ExPVPMatchUserDie packet2 = type == ExPVPMatchRecord.UPDATE ? new ExPVPMatchUserDie(this) : null;
        for (UCTeam team : getTeams()) {
            final Party party = team.getParty();
            if (party != null) {
                for (Player member : party.getMembers()) {
                    if (member != null) {
                        member.sendPacket(packet);
                        if (packet2 != null) {
                            member.sendPacket(packet2);
                        }
                    }
                }
            }
        }
    }

    public void startFight() {
        for (UCTeam team : teams) {
            team.spawnTower();
            for (Player player : team.getParty().getMembers()) {
                if (player != null) {
                    player.setTeam(team.getIndex() == 1 ? Team.BLUE : Team.RED);
                }
            }
        }
    }

    public void removeTeams() {
        for (UCTeam team : teams) {
            if (team.getParty() != null) {
                for (Player player : team.getParty().getMembers()) {
                    if (player == null) {
                        continue;
                    }

                    player.setTeam(Team.NONE);
                    player.cleanUCStats();
                    player.setUCState(Player.UC_STATE_NONE);
                    if (player.isDead()) {
                        UCTeam.resPlayer(player);
                    }

                    final String restore = player.getVariables().getString(PlayerVariables.RESTORE_LOCATION, "");
                    if (!restore.isEmpty()) {
                        final String[] split = restore.split(";");
                        player.teleToLocation(
                                Integer.parseInt(split[0]),
                                Integer.parseInt(split[1]),
                                Integer.parseInt(split[2]),
                                true);
                    } else {
                        player.teleToLocation(TeleportWhereType.TOWN);
                    }
                }
            }
        }
    }

    public void setIsBattleNow(boolean value) {
        battleNow = value;
    }

    public void setReward(UCReward ucReward) {
        rewards.add(ucReward);
    }
}
