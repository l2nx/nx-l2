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
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.data.xml.IXmlReader;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.managers.GlobalVariablesManager;
import app.l2nx.highfive.mechanics.undergroundColiseum.UCArena;
import app.l2nx.highfive.mechanics.undergroundColiseum.UCBestTeam;
import app.l2nx.highfive.mechanics.undergroundColiseum.UCPoint;
import app.l2nx.highfive.mechanics.undergroundColiseum.UCReward;
import app.l2nx.highfive.mechanics.undergroundColiseum.UCTeam;
import app.l2nx.highfive.threads.ThreadPool;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

@Slf4j
public class UndergroundColiseumManager implements IXmlReader {

    private final Map<Integer, UCArena> arenas = new HashMap<>(5);

    @Getter
    private boolean started = false;

    private long periodStartTime;
    private long periodEndTime;
    private ScheduledFuture<?> regTask = null;
    private final Map<Integer, UCBestTeam> bestTeams = new HashMap<>(5);

    protected UndergroundColiseumManager() {
        load();
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
            regTask = ThreadPool.schedule(new UCRegistrationTask(true), nextTime);
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
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement statement =
                        con.prepareStatement("SELECT * FROM underground_coliseum ORDER BY arenaId");
                ResultSet rset = statement.executeQuery()) {
            while (rset.next()) {
                final int arenaId = rset.getInt("arenaId");
                final String leader = rset.getString("leader");
                final int wins = rset.getInt("wins");
                bestTeams.put(arenaId, new UCBestTeam(arenaId, leader, wins));
            }
        } catch (SQLException e) {
            log.warn("Could not load underground_coliseum table");
        } catch (Exception e) {
            log.warn("Error while initializing UndergroundColiseumManager: {}", e.getMessage());
        }
    }

    private void saveBestTeam(UCBestTeam team, boolean isNew) {
        if (isNew) {
            try (Connection con = DatabaseFactory.getConnection();
                    PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO underground_coliseum (`arenaId`, `leader`, `wins`) VALUES (?,?,?) ")) {
                ps.setInt(1, team.getArenaId());
                ps.setString(2, team.getLeaderName());
                ps.setInt(3, team.getWins());
                ps.executeUpdate();
            } catch (SQLException e) {
                log.warn("Could not save underground_coliseum: {}", e.getMessage());
            }
        } else {
            try (Connection con = DatabaseFactory.getConnection()) {
                final PreparedStatement stmt =
                        con.prepareStatement("UPDATE underground_coliseum SET leader = ?, wins = ?  WHERE arenaId = ?");
                stmt.setInt(1, team.getArenaId());
                stmt.setInt(2, team.getWins());
                stmt.setInt(3, team.getArenaId());
                stmt.execute();
                stmt.close();
            } catch (Exception e) {
                log.warn(
                        "Could not clean status for underground_coliseum areanaId: {} in database!", team.getArenaId());
            }
        }
    }

    public UCBestTeam getBestTeam(int arenaId) {
        return bestTeams.get(arenaId);
    }

    public void updateBestTeam(int arenaId, String name, int wins) {
        if (bestTeams.containsKey(arenaId)) {
            final UCBestTeam team = getBestTeam(arenaId);
            if (team != null) {
                team.setLeader(name);
                team.setWins(wins);
                saveBestTeam(team, false);
            }
        } else {
            final UCBestTeam team = new UCBestTeam(arenaId, name, wins);
            bestTeams.put(arenaId, team);
            saveBestTeam(team, true);
        }
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
            regTask = ThreadPool.schedule(new UCRegistrationTask(false), nextTime);
            log.info("Ends at {}", DateFormatUtils.format(periodEndTime, "dd/MM/yyyy HH:mm:ss"));
        } else {
            generateNewDate();
            final long nextTime = periodStartTime - ClockBridge.wallMillis();
            regTask = ThreadPool.schedule(new UCRegistrationTask(true), nextTime);
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

    public static UndergroundColiseumManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final UndergroundColiseumManager INSTANCE = new UndergroundColiseumManager();
    }
}
