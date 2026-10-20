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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.actor.enums.npc.RaidBossStatus;
import app.l2nx.highfive.entity.actor.instance.RaidBoss;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.StatSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;

/**
 * Raid Boss spawn manager.
 * @author godson
 */
@Slf4j
public class RaidBossSpawnManager {

    protected static final Map<Integer, RaidBoss> bosses = new ConcurrentHashMap<>();
    protected static final Map<Integer, Spawn> spawns = new ConcurrentHashMap<>();
    protected static final Map<Integer, StatSet> storedInfo = new ConcurrentHashMap<>();
    protected static final Map<Integer, ScheduledFuture<?>> schedules = new ConcurrentHashMap<>();

    /**
     * Instantiates a new raid boss spawn manager.
     */
    protected RaidBossSpawnManager() {
        load();
    }

    /**
     * Load.
     */
    public void load() {
        if (!spawns.isEmpty()) {
            for (Spawn spawn : spawns.values()) {
                deleteSpawn(spawn, false);
            }
        }

        bosses.clear();
        spawns.clear();
        storedInfo.clear();
        schedules.clear();

        try (Connection con = DatabaseFactory.getConnection();
                Statement s = con.createStatement();
                ResultSet rs = s.executeQuery("SELECT * FROM raidboss_spawnlist ORDER BY boss_id")) {
            while (rs.next()) {
                final Spawn spawnDat = new Spawn(rs.getInt("boss_id"));
                spawnDat.setXYZ(rs.getInt("loc_x"), rs.getInt("loc_y"), rs.getInt("loc_z"));
                spawnDat.setAmount(rs.getInt("amount"));
                spawnDat.setHeading(rs.getInt("heading"));
                spawnDat.setRespawnDelay(rs.getInt("respawn_delay"), rs.getInt("respawn_random"));
                addNewSpawn(
                        spawnDat,
                        rs.getLong("respawn_time"),
                        rs.getDouble("currentHP"),
                        rs.getDouble("currentMP"),
                        false);
            }

            log.info("Loaded {} Instances", bosses.size());
            log.info("Scheduled {} Instances", schedules.size());
        } catch (SQLException e) {
            log.warn("Could not load raidboss_spawnlist table");
        } catch (Exception e) {
            log.warn("Error while initializing RaidBossSpawnManager: {}", e.getMessage(), e);
        }
    }

    @Slf4j
    private static class SpawnSchedule implements Runnable {

        private final int bossId;

        /**
         * Instantiates a new spawn schedule.
         * @param npcId the npc id
         */
        public SpawnSchedule(int npcId) {
            bossId = npcId;
        }

        @Override
        public void run() {
            final RaidBoss raidboss = (RaidBoss) spawns.get(bossId).doSpawn();
            if (raidboss != null) {
                raidboss.setRaidStatus(RaidBossStatus.ALIVE);

                final StatSet info = new StatSet();
                info.set("currentHP", raidboss.getCurrentHp());
                info.set("currentMP", raidboss.getCurrentMp());
                info.set("respawnTime", 0);
                storedInfo.put(bossId, info);

                log.info("Spawning Raid Boss {}", raidboss.getName());
                bosses.put(bossId, raidboss);
            }

            schedules.remove(bossId);
        }
    }

    /**
     * Update status.
     * @param boss the boss
     * @param isBossDead the is boss dead
     */
    public void updateStatus(RaidBoss boss, boolean isBossDead) {
        final StatSet info = storedInfo.get(boss.getId());
        if (info == null) {
            return;
        }

        if (isBossDead) {
            boss.setRaidStatus(RaidBossStatus.DEAD);

            final int respawnMinDelay =
                    (int) (boss.getSpawn().getRespawnMinDelay() * NpcConfig.RAID_MIN_RESPAWN_MULTIPLIER);
            final int respawnMaxDelay =
                    (int) (boss.getSpawn().getRespawnMaxDelay() * NpcConfig.RAID_MAX_RESPAWN_MULTIPLIER);
            final int respawnDelay = respawnMinDelay < respawnMaxDelay
                    ? ClockBridge.random().nextInt(respawnMinDelay, respawnMaxDelay + 1)
                    : respawnMinDelay;
            final long respawnTime = ClockBridge.wallMillis() + respawnDelay;
            info.set("currentHP", boss.getMaxHp());
            info.set("currentMP", boss.getMaxMp());
            info.set("respawnTime", respawnTime);
            if (!schedules.containsKey(boss.getId()) && ((respawnMinDelay > 0) || (respawnMaxDelay > 0))) {
                final Calendar time = ClockBridge.calendar();
                time.setTimeInMillis(respawnTime);
                log.info("Updated {} respawn time to {}", boss.getName(), time.getTime());
                schedules.put(boss.getId(), ThreadPool.schedule(new SpawnSchedule(boss.getId()), respawnDelay));
                updateDb();
            }
        } else {
            boss.setRaidStatus(RaidBossStatus.ALIVE);

            info.set("currentHP", boss.getCurrentHp());
            info.set("currentMP", boss.getCurrentMp());
            info.set("respawnTime", 0);
        }

        storedInfo.put(boss.getId(), info);
    }

    /**
     * Adds the new spawn.
     * @param spawnDat the spawn dat
     * @param respawnTime the respawn time
     * @param currentHP the current hp
     * @param currentMP the current mp
     * @param storeInDb the store in db
     */
    public void addNewSpawn(Spawn spawnDat, long respawnTime, double currentHP, double currentMP, boolean storeInDb) {
        if ((spawnDat == null) || spawns.containsKey(spawnDat.getId())) {
            return;
        }

        final int bossId = spawnDat.getId();
        final long currentTime = ClockBridge.wallMillis();
        SpawnTable.getInstance().addSpawn(spawnDat);
        if ((respawnTime == 0) || (currentTime > respawnTime)) {
            final RaidBoss raidboss = (RaidBoss) spawnDat.doSpawn();
            if (raidboss != null) {
                raidboss.setCurrentHp(currentHP);
                raidboss.setCurrentMp(currentMP);
                raidboss.setRaidStatus(RaidBossStatus.ALIVE);

                bosses.put(bossId, raidboss);

                final StatSet info = new StatSet();
                info.set("currentHP", currentHP);
                info.set("currentMP", currentMP);
                info.set("respawnTime", 0);
                storedInfo.put(bossId, info);
            }
        } else {
            schedules.put(bossId, ThreadPool.schedule(new SpawnSchedule(bossId), respawnTime - currentTime));
        }

        spawns.put(bossId, spawnDat);

        if (storeInDb) {
            try (Connection con = DatabaseFactory.getConnection();
                    PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO raidboss_spawnlist (boss_id,amount,loc_x,loc_y,loc_z,heading,respawn_time,currentHp,currentMp) VALUES(?,?,?,?,?,?,?,?,?)")) {
                ps.setInt(1, spawnDat.getId());
                ps.setInt(2, spawnDat.getAmount());
                ps.setInt(3, spawnDat.getX());
                ps.setInt(4, spawnDat.getY());
                ps.setInt(5, spawnDat.getZ());
                ps.setInt(6, spawnDat.getHeading());
                ps.setLong(7, respawnTime);
                ps.setDouble(8, currentHP);
                ps.setDouble(9, currentMP);
                ps.execute();
            } catch (Exception e) {
                // problem with storing spawn
                log.warn("Could not store raidboss #{} in the DB:{}", bossId, e.getMessage(), e);
            }
        }
    }

    /**
     * Delete spawn.
     * @param spawnDat the spawn dat
     * @param updateDb the update db
     */
    public void deleteSpawn(Spawn spawnDat, boolean updateDb) {
        if (spawnDat == null) {
            return;
        }

        final int bossId = spawnDat.getId();
        if (!spawns.containsKey(bossId)) {
            return;
        }

        SpawnTable.getInstance().removeSpawn(spawnDat);
        spawns.remove(bossId);

        if (bosses.containsKey(bossId)) {
            bosses.remove(bossId);
        }

        if (schedules.containsKey(bossId)) {
            schedules.remove(bossId).cancel(true);
        }

        if (storedInfo.containsKey(bossId)) {
            storedInfo.remove(bossId);
        }

        if (updateDb) {
            try (Connection con = DatabaseFactory.getConnection();
                    PreparedStatement ps = con.prepareStatement("DELETE FROM raidboss_spawnlist WHERE boss_id=?")) {
                ps.setInt(1, bossId);
                ps.execute();
            } catch (Exception e) {
                // problem with deleting spawn
                log.warn("Could not remove raidboss #{} from DB: {}", bossId, e.getMessage(), e);
            }
        }
    }

    /**
     * Update database.
     */
    private void updateDb() {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE raidboss_spawnlist SET respawn_time = ?, currentHP = ?, currentMP = ? WHERE boss_id = ?")) {
            for (Entry<Integer, StatSet> entry : storedInfo.entrySet()) {
                final Integer bossId = entry.getKey();
                if (bossId == null) {
                    continue;
                }

                final RaidBoss boss = bosses.get(bossId);
                if (boss == null) {
                    continue;
                }

                if (boss.getRaidStatus() == RaidBossStatus.ALIVE) {
                    updateStatus(boss, false);
                }

                final StatSet info = entry.getValue();
                if (info == null) {
                    continue;
                }

                try {
                    // TODO(Zoey76): Change this to use batch.
                    ps.setLong(1, info.getLong("respawnTime"));
                    ps.setDouble(2, boss.isDead() ? boss.getMaxHp() : info.getDouble("currentHP"));
                    ps.setDouble(3, boss.isDead() ? boss.getMaxMp() : info.getDouble("currentMP"));
                    ps.setInt(4, bossId);
                    ps.executeUpdate();
                    ps.clearParameters();
                } catch (SQLException e) {
                    log.warn("Could not update raidboss_spawnlist table {}", e.getMessage(), e);
                }
            }
        } catch (SQLException e) {
            log.warn("SQL error while updating RaidBoss spawn to database: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets the all raid boss status.
     * @return the all raid boss status
     */
    public String[] getAllRaidBossStatus() {
        final String[] msg = new String[bosses.size()];
        int index = 0;
        for (RaidBoss boss : bosses.values()) {
            msg[index++] = boss.getName() + ": " + boss.getRaidStatus().name();
        }

        return msg;
    }

    /**
     * Gets the raid boss status.
     * @param bossId the boss id
     * @return the raid boss status
     */
    public String getRaidBossStatus(int bossId) {
        String msg = "RaidBoss Status..." + System.lineSeparator();
        final RaidBoss boss = bosses.get(bossId);
        if (boss != null) {
            msg += boss.getName() + ": " + boss.getRaidStatus().name();
        }

        return msg;
    }

    /**
     * Gets the raid boss status id.
     * @param bossId the boss id
     * @return the raid boss status id
     */
    public RaidBossStatus getRaidBossStatusId(int bossId) {
        final RaidBoss boss = bosses.get(bossId);
        if (boss != null) {
            return boss.getRaidStatus();
        } else if (schedules.containsKey(bossId)) {
            return RaidBossStatus.DEAD;
        } else {
            return RaidBossStatus.UNDEFINED;
        }
    }

    /**
     * Notify spawn night boss.
     * @param raidboss the raidboss
     */
    public void notifySpawnNightBoss(RaidBoss raidboss) {
        final StatSet info = new StatSet();
        info.set("currentHP", raidboss.getCurrentHp());
        info.set("currentMP", raidboss.getCurrentMp());
        info.set("respawnTime", 0);
        raidboss.setRaidStatus(RaidBossStatus.ALIVE);

        storedInfo.put(raidboss.getId(), info);

        log.info("Spawning Night Raid Boss {}", raidboss.getName());
        bosses.put(raidboss.getId(), raidboss);
    }

    /**
     * Checks if the boss is defined.
     * @param bossId the boss id
     * @return {@code true} if is defined
     */
    public boolean isDefined(int bossId) {
        return spawns.containsKey(bossId);
    }

    /**
     * Gets the bosses.
     * @return the bosses
     */
    public Map<Integer, RaidBoss> getBosses() {
        return bosses;
    }

    /**
     * Gets the spawns.
     * @return the spawns
     */
    public Map<Integer, Spawn> getSpawns() {
        return spawns;
    }

    /**
     * Gets the stored info.
     * @return the stored info
     */
    public Map<Integer, StatSet> getStoredInfo() {
        return storedInfo;
    }

    /**
     * Saves and clears the raid bosses status, including all schedules.
     */
    public void cleanUp() {
        updateDb();

        bosses.clear();

        for (ScheduledFuture<?> schedule : schedules.values()) {
            schedule.cancel(true);
        }

        schedules.clear();

        storedInfo.clear();
        spawns.clear();
    }

    /**
     * Gets the single instance of RaidBossSpawnManager.
     * @return single instance of RaidBossSpawnManager
     */
    public static RaidBossSpawnManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final RaidBossSpawnManager INSTANCE = new RaidBossSpawnManager();
    }
}
