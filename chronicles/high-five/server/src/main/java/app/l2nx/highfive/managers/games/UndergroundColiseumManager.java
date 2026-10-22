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
package app.l2nx.highfive.managers.games;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.UndergroundColiseumConfig;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.data.xml.IXmlReader;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.managers.GlobalVariablesManager;
import app.l2nx.highfive.mechanics.undergroundColiseum.UCArena;
import app.l2nx.highfive.mechanics.undergroundColiseum.UCBestTeam;
import app.l2nx.highfive.mechanics.undergroundColiseum.UCPoint;
import app.l2nx.highfive.mechanics.undergroundColiseum.UCReward;
import app.l2nx.highfive.mechanics.undergroundColiseum.UCTeam;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.world.UndergroundColiseumBestTeamRepository;
import app.l2nx.highfive.repository.world.model.UndergroundColiseumBestTeam;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

@Slf4j
@Component
public class UndergroundColiseumManager implements IXmlReader {
    private static volatile UndergroundColiseumManager instance;

    private final Map<Integer, UCArena> arenas = new HashMap<>(5);

    @Getter
    private boolean started = false;

    private long periodStartTime;
    private long periodEndTime;
    private ScheduledFuture<?> regTask = null;
    private final Map<Integer, UCBestTeam> bestTeams = new HashMap<>(5);

    private final UndergroundColiseumBestTeamRepository bestTeamRepository;

    public UndergroundColiseumManager(UndergroundColiseumBestTeamRepository bestTeamRepository) {
        this.bestTeamRepository = bestTeamRepository;
        instance = this;
    }

    @Override
    public void load() {
        periodStartTime = GlobalVariablesManager.getInstance().getLong("UC_START_TIME", 0);
        periodEndTime = GlobalVariablesManager.getInstance().getLong("UC_STOP_TIME", 0);

        final long curerntTime = ClockBridge.wallMillis();
        if ((periodStartTime < curerntTime) && (periodEndTime < curerntTime)) {
            generateNewDate();
        }

        parseDatapackFile("data/UndergroundColiseum.xml");

        log.info("Loaded {} coliseum arenas.", arenas.size());

        if ((periodStartTime < curerntTime) && (periodEndTime > curerntTime)) {
            switchStatus(true);
        } else {
            final long nextTime = periodStartTime - curerntTime;
            regTask = WorldPartition.loop().schedule(new UCRegistrationTask(true), nextTime, TimeUnit.MILLISECONDS);
            log.info("Starts at {}", DateFormatUtils.format(periodStartTime, "dd/MM/yyyy HH:mm:ss"));
        }

        restoreBestTeams();
    }

    @Override
    public void parseDocument(Document document, File file) {
        NamedNodeMap map;
        for (Node n = document.getFirstChild(); n != null; n = n.getNextSibling()) {
            if ("list".equalsIgnoreCase(n.getNodeName())) {
                for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                    if ("arena".equalsIgnoreCase(d.getNodeName())) {
                        map = d.getAttributes();
                        final int id = Integer.parseInt(map.getNamedItem("id").getNodeValue());
                        final int minLevel =
                                Integer.parseInt(map.getNamedItem("minLvl").getNodeValue());
                        final int maxLevel =
                                Integer.parseInt(map.getNamedItem("maxLvl").getNodeValue());
                        final int curator =
                                Integer.parseInt(map.getNamedItem("curator").getNodeValue());

                        final UCArena arena = new UCArena(id, curator, minLevel, maxLevel);
                        int index = 0;
                        int index2 = 0;

                        for (Node und = d.getFirstChild(); und != null; und = und.getNextSibling()) {
                            if ("tower".equalsIgnoreCase(und.getNodeName())) {
                                map = und.getAttributes();

                                final int npcId =
                                        Integer.parseInt(map.getNamedItem("id").getNodeValue());
                                final int x =
                                        Integer.parseInt(map.getNamedItem("x").getNodeValue());
                                final int y =
                                        Integer.parseInt(map.getNamedItem("y").getNodeValue());
                                final int z =
                                        Integer.parseInt(map.getNamedItem("z").getNodeValue());

                                final UCTeam team = new UCTeam(index, arena, x, y, z, npcId);
                                arena.setUCTeam(index, team);

                                index++;
                            } else if ("spawn".equalsIgnoreCase(und.getNodeName())) {
                                map = und.getAttributes();
                                final List<Door> doors = new ArrayList<>();
                                final String doorList = map.getNamedItem("doors") != null
                                        ? map.getNamedItem("doors").getNodeValue()
                                        : "";
                                if (!doorList.isEmpty()) {
                                    final String[] doorSplint = doorList.split(",");
                                    for (String doorId : doorSplint) {
                                        final Door door = DoorData.getInstance().getDoor(Integer.parseInt(doorId));
                                        if (door != null) {
                                            doors.add(door);
                                        }
                                    }
                                }

                                final int x =
                                        Integer.parseInt(map.getNamedItem("x").getNodeValue());
                                final int y =
                                        Integer.parseInt(map.getNamedItem("y").getNodeValue());
                                final int z =
                                        Integer.parseInt(map.getNamedItem("z").getNodeValue());

                                final UCPoint point = new UCPoint(doors, new Location(x, y, z));
                                arena.setUCPoint(index2, point);

                                index2++;
                            } else if ("rewards".equalsIgnoreCase(und.getNodeName())) {
                                for (Node c = und.getFirstChild(); c != null; c = c.getNextSibling()) {
                                    if ("item".equalsIgnoreCase(c.getNodeName())) {
                                        final int itemId = Integer.parseInt(c.getAttributes()
                                                .getNamedItem("id")
                                                .getNodeValue());
                                        final long amount = Long.parseLong(c.getAttributes()
                                                .getNamedItem("amount")
                                                .getNodeValue());
                                        final boolean useModifier =
                                                c.getAttributes().getNamedItem("useModifers") != null
                                                        ? Boolean.parseBoolean(c.getAttributes()
                                                                .getNamedItem("useModifers")
                                                                .getNodeValue())
                                                        : false;
                                        arena.setReward(UCReward.builder()
                                                .id(itemId)
                                                .amount(amount)
                                                .allowMidifier(useModifier)
                                                .build());
                                    }
                                }
                            }
                        }

                        arenas.put(arena.getId(), arena);
                    }
                }
            }
        }
    }

    private void restoreBestTeams() {
        bestTeams.clear();
        try {
            for (UndergroundColiseumBestTeam team : bestTeamRepository.findAll()) {
                bestTeams.put(
                        team.arenaId(),
                        new UCBestTeam(team.arenaId(), team.leaderId(), team.leaderName(), team.wins()));
            }
        } catch (DataAccessException e) {
            log.warn("Could not load underground coliseum best teams: {}", e.getMessage());
        }
    }

    private void saveBestTeam(UCBestTeam team) {
        try {
            bestTeamRepository.save(UndergroundColiseumBestTeam.builder()
                    .arenaId(team.getArenaId())
                    .leaderId(team.getLeaderId())
                    .wins(team.getWins())
                    .build());
        } catch (DataAccessException e) {
            log.warn(
                    "Could not save underground coliseum best team of arena {}: {}", team.getArenaId(), e.getMessage());
        }
    }

    public UCBestTeam getBestTeam(int arenaId) {
        return bestTeams.get(arenaId);
    }

    public void updateBestTeam(int arenaId, Player leader, int wins) {
        UCBestTeam team = getBestTeam(arenaId);
        if (team == null) {
            team = new UCBestTeam(arenaId, leader.getObjectId(), leader.getName(), wins);
            bestTeams.put(arenaId, team);
        } else {
            team.setLeader(leader.getObjectId(), leader.getName());
            team.setWins(wins);
        }

        saveBestTeam(team);
    }

    private void generateNewDate() {
        periodStartTime = UndergroundColiseumConfig.UC_START_SCHEDULE
                .next()
                .orElseThrow(() -> new IllegalStateException(
                        "BattleStartRrule has no further start: " + UndergroundColiseumConfig.UC_START_SCHEDULE))
                .toEpochMilli();
        periodEndTime = periodStartTime + (UndergroundColiseumConfig.UC_TIME_PERIOD * 3600000);
        GlobalVariablesManager.getInstance().set("UC_START_TIME", periodStartTime);
        GlobalVariablesManager.getInstance().set("UC_STOP_TIME", periodEndTime);
    }

    public UCArena getArena(int id) {
        return arenas.get(id);
    }

    public void setStarted(boolean started) {
        this.started = started;
        for (UCArena arena : getAllArenas()) {
            arena.switchStatus(started);
        }

        if (UndergroundColiseumConfig.UC_ALLOW_ANNOUNCE) {
            if (this.started) {
                World.broadcastToAllOnlinePlayers("Underground Coliseum has started!");
            } else {
                World.broadcastToAllOnlinePlayers("Underground Coliseum has stopped!");
            }
        }
    }

    public Collection<UCArena> getAllArenas() {
        return arenas.values();
    }

    private void switchStatus(boolean isStart) {
        if (regTask != null) {
            regTask.cancel(false);
            regTask = null;
        }

        setStarted(isStart);
        if (isStart) {
            final long nextTime = periodEndTime - ClockBridge.wallMillis();
            regTask = WorldPartition.loop().schedule(new UCRegistrationTask(false), nextTime, TimeUnit.MILLISECONDS);
            log.info("Ends at {}", DateFormatUtils.format(periodEndTime, "dd/MM/yyyy HH:mm:ss"));
        } else {
            generateNewDate();
            final long nextTime = periodStartTime - ClockBridge.wallMillis();
            regTask = WorldPartition.loop().schedule(new UCRegistrationTask(true), nextTime, TimeUnit.MILLISECONDS);
            log.info("Starts at {}", DateFormatUtils.format(periodStartTime, "dd/MM/yyyy HH:mm:ss"));
        }
    }

    public class UCRegistrationTask implements Runnable {
        private final boolean status;

        public UCRegistrationTask(boolean status) {
            this.status = status;
        }

        @Override
        public void run() {
            switchStatus(status);
        }
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static UndergroundColiseumManager getInstance() {
        final UndergroundColiseumManager current = instance;
        if (current == null) {
            throw new IllegalStateException("UndergroundColiseumManager is used before the Spring context created it.");
        }
        return current;
    }
}
