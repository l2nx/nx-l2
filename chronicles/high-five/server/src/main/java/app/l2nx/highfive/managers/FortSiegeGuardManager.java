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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.data.holders.SiegeGuardSpawn;
import app.l2nx.highfive.data.xml.FortData;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.mechanics.siege.Fort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FortSiegeGuardManager {

    @Getter
    private final Fort fort;

    @Getter
    private final Map<Integer, List<Spawn>> siegeGuardSpawn = new HashMap<>();

    public FortSiegeGuardManager(Fort fort) {
        this.fort = fort;
    }

    /**
     * Spawn guards.
     */
    public void spawnSiegeGuard() {
        try {
            final List<Spawn> monsterList = siegeGuardSpawn.get(fort.getResidenceId());
            if (monsterList != null) {
                for (Spawn spawnDat : monsterList) {
                    spawnDat.doSpawn(false);
                    if (spawnDat.getRespawnDelay() == 0) {
                        spawnDat.stopRespawn();
                    } else {
                        spawnDat.startRespawn();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error spawning siege guards for fort {}:{}", fort.getName(), e.getMessage(), e);
        }
    }

    /**
     * Unspawn guards.
     */
    public void unspawnSiegeGuard() {
        try {
            final List<Spawn> monsterList = siegeGuardSpawn.get(fort.getResidenceId());
            if (monsterList != null) {
                for (Spawn spawnDat : monsterList) {
                    spawnDat.stopRespawn();
                    if (spawnDat.getLastSpawn() != null) {
                        spawnDat.getLastSpawn().doDie(spawnDat.getLastSpawn());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error unspawning siege guards for fort {}:{}", fort.getName(), e.getMessage(), e);
        }
    }

    /**
     * Load guards.
     */
    void loadSiegeGuard() {
        siegeGuardSpawn.clear();
        try {
            final int fortId = fort.getResidenceId();
            final List<Spawn> siegeGuardSpawns = new ArrayList<>();
            for (SiegeGuardSpawn guard : FortData.getInstance().getFort(fortId).siegeGuards()) {
                final Spawn spawn = new Spawn(guard.npcId());
                spawn.setAmount(1);
                spawn.setXYZ(
                        guard.spawnPoint().x(),
                        guard.spawnPoint().y(),
                        guard.spawnPoint().z());
                spawn.setHeading(guard.spawnPoint().heading());
                spawn.setRespawnDelay(guard.respawnDelay());
                spawn.setLocationId(0);

                siegeGuardSpawns.add(spawn);
            }

            siegeGuardSpawn.put(fortId, siegeGuardSpawns);
        } catch (Exception e) {
            log.warn("Error loading siege guard for fort {}: {}", fort.getName(), e.getMessage(), e);
        }
    }
}
