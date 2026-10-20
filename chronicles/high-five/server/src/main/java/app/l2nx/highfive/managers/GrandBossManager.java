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

import app.l2nx.highfive.data.holders.GrandBossSpawn;
import app.l2nx.highfive.data.holders.SpawnPoint;
import app.l2nx.highfive.data.xml.GrandBossData;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.GrandBoss;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.zone.type.BossZone;
import app.l2nx.highfive.repository.boss.GrandBossStateRepository;
import app.l2nx.highfive.repository.boss.GrandBossZoneCharacterRepository;
import app.l2nx.highfive.repository.boss.model.GrandBossState;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Grand Boss manager.
 * @author DaRkRaGe, Emperorc
 */
@Slf4j
@Component
public class GrandBossManager {
    private static volatile GrandBossManager instance;

    protected static final Map<Integer, GrandBoss> BOSSES = new ConcurrentHashMap<>();

    protected static Map<Integer, StatSet> storedInfo = new HashMap<>();

    private final Map<Integer, Integer> bossStatus = new ConcurrentHashMap<>();

    @Getter
    private final Map<Integer, BossZone> zones = new ConcurrentHashMap<>();

    private final GrandBossStateRepository stateRepository;
    private final GrandBossZoneCharacterRepository zoneCharacterRepository;

    public GrandBossManager(
            GrandBossStateRepository stateRepository, GrandBossZoneCharacterRepository zoneCharacterRepository) {
        this.stateRepository = stateRepository;
        this.zoneCharacterRepository = zoneCharacterRepository;
        instance = this;
    }

    public void load() {
        try {
            final Map<Integer, GrandBossState> states = stateRepository.findAll().stream()
                    .collect(Collectors.toMap(GrandBossState::npcId, Function.identity()));
            for (GrandBossSpawn boss : GrandBossData.getInstance().getBosses()) {
                final int bossId = boss.npcId();
                final NpcTemplate template = NpcData.getInstance().getTemplate(bossId);
                final GrandBossState state = states.get(bossId);
                final int status = state == null ? 0 : state.status();
                final StatSet info = toStatSet(boss, template, state);
                bossStatus.put(bossId, status);
                storedInfo.put(bossId, info);
                log.info("{}({}) status is {}.", template.getName(), bossId, status);
                if (status > 0) {
                    log.info(
                            "Next spawn date of {} is {}.", template.getName(), new Date(info.getLong("respawn_time")));
                }
            }

            log.info("Loaded {} Instances", storedInfo.size());
        } catch (DataAccessException e) {
            log.warn("Could not load grand boss states: {}", e.getMessage(), e);
        }

        ThreadPool.scheduleAtFixedRate(this::storeMe, 5 * 60 * 1000, 5 * 60 * 1000);
    }

    /**
     * A boss without a row was never saved: it stands at its datapack point (none when its script places it) with
     * its template HP/MP. A row without HP/MP or position was saved by status only.
     */
    private static StatSet toStatSet(GrandBossSpawn boss, NpcTemplate template, GrandBossState state) {
        final StatSet info = new StatSet();
        final SpawnPoint datapackPoint =
                boss.spawnPoint() == null ? SpawnPoint.builder().build() : boss.spawnPoint();
        final boolean positionStored = (state != null) && (state.x() != null);
        info.set("loc_x", positionStored ? state.x() : datapackPoint.x());
        info.set("loc_y", positionStored ? state.y() : datapackPoint.y());
        info.set("loc_z", positionStored ? state.z() : datapackPoint.z());
        info.set("heading", positionStored ? state.heading() : datapackPoint.heading());
        info.set("respawn_time", state == null ? 0 : epochMillisOrZero(state.respawnTime()));
        final boolean hpMpStored = (state != null) && (state.currentHp() != null);
        info.set("currentHP", hpMpStored ? state.currentHp() : template.getBaseHpMax());
        info.set("currentMP", hpMpStored ? state.currentMp() : template.getBaseMpMax());
        return info;
    }

    /**
     * Zone Functions
     */
    public void initZones() {
        final Map<Integer, List<Integer>> allowedByZone;
        try {
            allowedByZone = zoneCharacterRepository.findAll();
        } catch (DataAccessException e) {
            log.warn("Could not load grand boss zone entrants: {}", e.getMessage(), e);
            return;
        }

        allowedByZone.keySet().stream()
                .filter(zoneId -> !zones.containsKey(zoneId))
                .forEach(zoneId ->
                        log.warn("Grand boss zone {} is not in the datapack, its entrants are dropped.", zoneId));
        for (Entry<Integer, BossZone> e : zones.entrySet()) {
            e.getValue().setAllowedPlayers(allowedByZone.getOrDefault(e.getKey(), new ArrayList<>()));
        }

        log.info("Initialized {} Grand Boss Zones", zones.size());
    }

    public void addZone(BossZone zone) {
        zones.put(zone.getId(), zone);
    }

    public BossZone getZone(int zoneId) {
        return zones.get(zoneId);
    }

    public BossZone getZone(Creature creature) {
        for (BossZone zone : zones.values()) {
            if (zone.isCharacterInZone(creature)) {
                return zone;
            }
        }

        return null;
    }

    public BossZone getZone(Location loc) {
        return getZone(loc.getX(), loc.getY(), loc.getZ());
    }

    public BossZone getZone(int x, int y, int z) {
        for (BossZone zone : zones.values()) {
            if (zone.isInsideZone(x, y, z)) {
                return zone;
            }
        }

        return null;
    }

    public boolean checkIfInZone(String zoneType, WorldObject obj) {
        final BossZone temp = getZone(obj.getX(), obj.getY(), obj.getZ());
        return (temp != null) && temp.getName().equalsIgnoreCase(zoneType);
    }

    public boolean checkIfInZone(Player player) {
        return (player != null) && (getZone(player.getX(), player.getY(), player.getZ()) != null);
    }

    public int getStatus(int bossId) {
        final Integer status = bossStatus.get(bossId);
        if (status == null) {
            throw new IllegalArgumentException("Grand boss " + bossId + " is not in GrandBosses.xml");
        }

        return status;
    }

    public void setStatus(int bossId, int status) {
        bossStatus.put(bossId, status);
        log.info(
                "Updated {}({}) status to {}",
                NpcData.getInstance().getTemplate(bossId).getName(),
                bossId,
                status);
        updateDb(bossId, true);
    }

    /**
     * Adds a GrandBoss to the list of bosses.
     * @param boss
     */
    public void addBoss(GrandBoss boss) {
        if (boss != null) {
            BOSSES.put(boss.getId(), boss);
        }
    }

    public GrandBoss getBoss(int bossId) {
        return BOSSES.get(bossId);
    }

    public StatSet getStatSet(int bossId) {
        return storedInfo.get(bossId);
    }

    public void setStatSet(int bossId, StatSet info) {
        storedInfo.put(bossId, info);
        updateDb(bossId, false);
    }

    public boolean storeMe() {
        try {
            final Map<Integer, List<Integer>> allowedByZone = new HashMap<>();
            for (Entry<Integer, BossZone> e : zones.entrySet()) {
                final List<Integer> list = e.getValue().getAllowedPlayers();
                if ((list != null) && !list.isEmpty()) {
                    allowedByZone.put(e.getKey(), list);
                }
            }

            zoneCharacterRepository.replaceAll(allowedByZone);
            for (Integer bossId : storedInfo.keySet()) {
                saveState(bossId, false);
            }
        } catch (DataAccessException e) {
            log.warn("Couldn't store grandbosses to database:{}", e.getMessage(), e);
            return false;
        }

        return true;
    }

    private void updateDb(int bossId, boolean statusOnly) {
        try {
            saveState(bossId, statusOnly);
        } catch (DataAccessException e) {
            log.warn("Couldn't update grandbosses to database:{}", e.getMessage(), e);
        }
    }

    private void saveState(int bossId, boolean statusOnly) {
        final GrandBoss boss = BOSSES.get(bossId);
        final StatSet info = storedInfo.get(bossId);
        if (statusOnly || (boss == null) || (info == null)) {
            stateRepository.saveStatus(bossId, bossStatus.get(bossId));
            return;
        }

        double hp = boss.getCurrentHp();
        double mp = boss.getCurrentMp();
        if (boss.isDead()) {
            hp = boss.getMaxHp();
            mp = boss.getMaxMp();
        }

        stateRepository.save(GrandBossState.builder()
                .npcId(bossId)
                .status(bossStatus.get(bossId))
                .respawnTime(instantOrNull(info.getLong("respawn_time")))
                .currentHp(hp)
                .currentMp(mp)
                .x(boss.getX())
                .y(boss.getY())
                .z(boss.getZ())
                .heading(boss.getHeading())
                .build());
    }

    /**
     * Saves all Grand Boss info and then clears all info from memory, including all schedules.
     */
    public void cleanUp() {
        storeMe();

        BOSSES.clear();
        storedInfo.clear();
        bossStatus.clear();
        zones.clear();
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static GrandBossManager getInstance() {
        final GrandBossManager current = instance;
        if (current == null) {
            throw new IllegalStateException("GrandBossManager is used before the Spring context created it.");
        }
        return current;
    }
}
