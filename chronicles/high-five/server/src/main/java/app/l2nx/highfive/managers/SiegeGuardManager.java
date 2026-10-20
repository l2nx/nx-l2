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
import app.l2nx.highfive.data.xml.CastleData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.repository.residence.model.CastleMercenary;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SiegeGuardManager {

    @Getter
    private final Castle castle;

    @Getter
    private final List<Spawn> siegeGuardSpawn = new ArrayList<>();

    public SiegeGuardManager(Castle castle) {
        this.castle = castle;
    }

    /**
     * Hire merc.
     * @param player
     * @param npcId
     */
    public void hireMerc(Player player, int npcId) {
        if (player == null) {
            return;
        }

        hireMerc(player.getX(), player.getY(), player.getZ(), player.getHeading(), npcId);
    }

    /**
     * Hire merc.
     * @param x
     * @param y
     * @param z
     * @param heading
     * @param npcId
     */
    public void hireMerc(int x, int y, int z, int heading, int npcId) {
        MercTicketManager.getInstance()
                .saveMercenary(CastleMercenary.builder()
                        .castleId(castle.getResidenceId())
                        .npcId(npcId)
                        .x(x)
                        .y(y)
                        .z(z)
                        .heading(heading)
                        .build());
    }

    /**
     * Remove a single mercenary, identified by the npcId and location. Presumably, this is used when a castle lord picks up a previously dropped ticket
     * @param npcId
     * @param x
     * @param y
     * @param z
     */
    public void removeMerc(int npcId, int x, int y, int z) {
        MercTicketManager.getInstance().deleteMercenary(npcId, x, y, z);
    }

    /**
     * Remove mercs.
     */
    public void removeMercs() {
        MercTicketManager.getInstance().deleteMercenaries(castle.getResidenceId());
    }

    /**
     * Spawn guards.
     */
    public void spawnSiegeGuard() {
        try {
            int hiredCount = 0;
            final int hiredMax = MercTicketManager.getInstance().getMaxAllowedMerc(castle.getResidenceId());
            final boolean isHired = castle.getOwnerId() > 0;
            loadSiegeGuard();
            for (Spawn spawn : siegeGuardSpawn) {
                if (spawn != null) {
                    spawn.init();
                    if (isHired) {
                        spawn.stopRespawn();
                        if (++hiredCount > hiredMax) {
                            return;
                        }
                    } else if (spawn.getRespawnDelay() == 0) {
                        spawn.stopRespawn();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error spawning siege guards for castle {}", castle.getName(), e);
        }
    }

    /**
     * Unspawn guards.
     */
    public void unspawnSiegeGuard() {
        for (Spawn spawn : siegeGuardSpawn) {
            if ((spawn != null) && (spawn.getLastSpawn() != null)) {
                spawn.stopRespawn();
                spawn.getLastSpawn().deleteMe();
            }
        }

        siegeGuardSpawn.clear();
    }

    private void loadSiegeGuard() {
        try {
            if (castle.getOwnerId() > 0) {
                for (CastleMercenary mercenary :
                        MercTicketManager.getInstance().findMercenaries(castle.getResidenceId())) {
                    addSpawn(mercenary.npcId(), mercenary.x(), mercenary.y(), mercenary.z(), mercenary.heading(), 0);
                }
            } else {
                for (SiegeGuardSpawn guard : CastleData.getInstance()
                        .getCastle(castle.getResidenceId())
                        .siegeGuards()) {
                    addSpawn(
                            guard.npcId(),
                            guard.spawnPoint().x(),
                            guard.spawnPoint().y(),
                            guard.spawnPoint().z(),
                            guard.spawnPoint().heading(),
                            guard.respawnDelay());
                }
            }
        } catch (Exception e) {
            log.warn("Error loading siege guard for castle {}: {}", castle.getName(), e.getMessage(), e);
        }
    }

    private void addSpawn(int npcId, int x, int y, int z, int heading, int respawnDelay)
            throws ClassNotFoundException, NoSuchMethodException {
        final Spawn spawn = new Spawn(npcId);
        spawn.setAmount(1);
        spawn.setXYZ(x, y, z);
        spawn.setHeading(heading);
        spawn.setRespawnDelay(respawnDelay);
        spawn.setLocationId(0);
        siegeGuardSpawn.add(spawn);
    }
}
