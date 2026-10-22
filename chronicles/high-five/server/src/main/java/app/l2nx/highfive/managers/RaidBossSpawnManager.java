/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.data.holders.RaidBossSpawn;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.data.xml.RaidBossSpawnData;
import app.l2nx.highfive.entity.actor.enums.npc.RaidBossStatus;
import app.l2nx.highfive.entity.actor.instance.RaidBoss;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.boss.RaidBossStateRepository;
import app.l2nx.highfive.repository.boss.model.RaidBossState;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Raid Boss spawn manager.
 * @author godson
 */
@Slf4j
@Component
public class RaidBossSpawnManager {
    private static volatile RaidBossSpawnManager instance;

    protected static final Map<Integer, RaidBoss> bosses = new ConcurrentHashMap<>();
    protected static final Map<Integer, Spawn> spawns = new ConcurrentHashMap<>();
    protected static final Map<Integer, StatSet> storedInfo = new ConcurrentHashMap<>();
    protected static final Map<Integer, ScheduledFuture<?>> schedules = new ConcurrentHashMap<>();

    private final RaidBossStateRepository stateRepository;

    public RaidBossSpawnManager(RaidBossStateRepository stateRepository) {
        this.stateRepository = stateRepository;
        instance = this;
    }

    /**
     * Spawns the datapack raid bosses with their stored state; a boss without a stored state was never killed and
     * spawns now at full HP/MP.
     */
    public void load() {
        if (!spawns.isEmpty()) {
            for (Spawn spawn : spawns.values()) {
                deleteSpawn(spawn);
            }
        }

        bosses.clear();
        spawns.clear();
        storedInfo.clear();
        schedules.clear();

        final Map<Integer, RaidBossState> states;
        try {
            states = stateRepository.findAll().stream()
                    .collect(Collectors.toMap(RaidBossState::npcId, Function.identity()));
        } catch (DataAccessException e) {
            log.warn("Could not load raid boss states: {}", e.getMessage(), e);
            return;
        }

        for (RaidBossSpawn boss : RaidBossSpawnData.getInstance().getSpawns()) {
            try {
                final NpcTemplate template = NpcData.getInstance().getTemplate(boss.npcId());
                final Spawn spawnDat = new Spawn(template);
                spawnDat.setXYZ(
                        boss.spawnPoint().x(),
                        boss.spawnPoint().y(),
                        boss.spawnPoint().z());
                spawnDat.setAmount(1);
                spawnDat.setHeading(boss.spawnPoint().heading());
                spawnDat.setRespawnDelay(boss.respawnDelay(), boss.respawnRandom());
                final RaidBossState state = states.get(boss.npcId());
                if (state == null) {
                    addNewSpawn(spawnDat, 0, template.getBaseHpMax(), template.getBaseMpMax());
                } else {
                    addNewSpawn(spawnDat, epochMillisOrZero(state.respawnTime()), state.currentHp(), state.currentMp());
                }
            } catch (Exception e) {
                log.warn("Could not spawn raid boss {}: {}", boss.npcId(), e.getMessage(), e);
            }
        }

        log.info("Loaded {} Instances", bosses.size());
        log.info("Scheduled {} Instances", schedules.size());
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
                schedules.put(
                        boss.getId(),
                        WorldPartition.loop()
                                .schedule(new SpawnSchedule(boss.getId()), respawnDelay, TimeUnit.MILLISECONDS));
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
     */
    public void addNewSpawn(Spawn spawnDat, long respawnTime, double currentHP, double currentMP) {
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
            schedules.put(
                    bossId,
                    WorldPartition.loop()
                            .schedule(new SpawnSchedule(bossId), respawnTime - currentTime, TimeUnit.MILLISECONDS));
        }

        spawns.put(bossId, spawnDat);
    }

    private void deleteSpawn(Spawn spawnDat) {
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
    }

    /**
     * Saves the state of the datapack bosses only: a boss an admin spawned by hand is not kept over a restart.
     */
    private void updateDb() {
        final List<RaidBossState> states = new ArrayList<>();
        for (Entry<Integer, StatSet> entry : storedInfo.entrySet()) {
            final Integer bossId = entry.getKey();
            if ((bossId == null) || (RaidBossSpawnData.getInstance().getSpawn(bossId) == null)) {
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

            states.add(RaidBossState.builder()
                    .npcId(bossId)
                    .respawnTime(instantOrNull(info.getLong("respawnTime")))
                    .currentHp(boss.isDead() ? boss.getMaxHp() : info.getDouble("currentHP"))
                    .currentMp(boss.isDead() ? boss.getMaxMp() : info.getDouble("currentMP"))
                    .build());
        }

        try {
            stateRepository.saveAll(states);
        } catch (DataAccessException e) {
            log.warn("Could not save raid boss states: {}", e.getMessage(), e);
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

    // TODO: callers receive the bean through their constructor (#41).
    public static RaidBossSpawnManager getInstance() {
        final RaidBossSpawnManager current = instance;
        if (current == null) {
            throw new IllegalStateException("RaidBossSpawnManager is used before the Spring context created it.");
        }
        return current;
    }
}
