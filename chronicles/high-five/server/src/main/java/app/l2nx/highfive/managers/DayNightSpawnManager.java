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

import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.instance.RaidBoss;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * @author godson
 */
@Slf4j
public class DayNightSpawnManager {

    private final List<Spawn> dayCreatures = new ArrayList<>();
    private final List<Spawn> nightCreatures = new ArrayList<>();
    private final Map<Spawn, RaidBoss> bosses = new ConcurrentHashMap<>();

    protected DayNightSpawnManager() {
        // Prevent external initialization.
    }

    public void addDayCreature(Spawn spawnDat) {
        dayCreatures.add(spawnDat);
    }

    public void addNightCreature(Spawn spawnDat) {
        nightCreatures.add(spawnDat);
    }

    /**
     * Spawn Day Creatures, and Unspawn Night Creatures
     */
    public void spawnDayCreatures() {
        spawnCreatures(nightCreatures, dayCreatures, "night", "day");
    }

    /**
     * Spawn Night Creatures, and Unspawn Day Creatures
     */
    public void spawnNightCreatures() {
        spawnCreatures(dayCreatures, nightCreatures, "day", "night");
    }

    /**
     * Manage Spawn/Respawn
     * @param unSpawnCreatures List with spawns must be unspawned
     * @param spawnCreatures List with spawns must be spawned
     * @param unspawnLogInfo String for log info for unspawned Npc
     * @param spawnLogInfo String for log info for spawned Npc
     */
    private void spawnCreatures(
            List<Spawn> unSpawnCreatures, List<Spawn> spawnCreatures, String unspawnLogInfo, String spawnLogInfo) {
        try {
            if (!unSpawnCreatures.isEmpty()) {
                int i = 0;
                for (Spawn spawn : unSpawnCreatures) {
                    if (spawn == null) {
                        continue;
                    }

                    spawn.stopRespawn();
                    final Npc last = spawn.getLastSpawn();
                    if (last != null) {
                        last.deleteMe();
                        i++;
                    }
                }

                log.info("Removed {} {} creatures", i, unspawnLogInfo);
            }

            int i = 0;
            for (Spawn spawnDat : spawnCreatures) {
                if (spawnDat == null) {
                    continue;
                }

                spawnDat.startRespawn();
                spawnDat.doSpawn();
                i++;
            }

            log.info("Spawned {} {} creatures", i, spawnLogInfo);
        } catch (Exception e) {
            log.warn("Error while spawning creatures: {}", e.getMessage(), e);
        }
    }

    private void changeMode(int mode) {
        if (nightCreatures.isEmpty() && dayCreatures.isEmpty() && bosses.isEmpty()) {
            return;
        }

        switch (mode) {
            case 0: {
                spawnDayCreatures();
                break;
            }
            case 1: {
                spawnNightCreatures();
                break;
            }
            default: {
                log.warn("Wrong mode sent");
                break;
            }
        }
    }

    public DayNightSpawnManager trim() {
        ((ArrayList<?>) nightCreatures).trimToSize();
        ((ArrayList<?>) dayCreatures).trimToSize();
        return this;
    }

    public void notifyChangeMode() {
        try {
            changeMode(GameTimeTaskManager.getInstance().isNight() ? 1 : 0);
        } catch (Exception e) {
            log.warn("Error while notifyChangeMode(): {}", e.getMessage(), e);
        }
    }

    public void cleanUp() {
        nightCreatures.clear();
        dayCreatures.clear();
        bosses.clear();
    }

    public static DayNightSpawnManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final DayNightSpawnManager INSTANCE = new DayNightSpawnManager();
    }
}
