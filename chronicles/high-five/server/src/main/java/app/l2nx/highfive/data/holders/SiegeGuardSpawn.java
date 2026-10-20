/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.util.StatSet;
import lombok.Builder;

/** @param respawnDelay seconds */
@Builder(toBuilder = true)
public record SiegeGuardSpawn(int npcId, SpawnPoint spawnPoint, int respawnDelay) {

    public static SiegeGuardSpawn of(StatSet set) {
        return SiegeGuardSpawn.builder()
                .npcId(set.getInt("npcId"))
                .spawnPoint(SpawnPoint.of(set))
                .respawnDelay(set.getInt("respawnDelay"))
                .build();
    }
}
