/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.util.StatSet;
import lombok.Builder;

/**
 * @param respawnDelay seconds
 * @param respawnRandom seconds, the random spread around {@code respawnDelay}
 */
@Builder(toBuilder = true)
public record RaidBossSpawn(int npcId, SpawnPoint spawnPoint, int respawnDelay, int respawnRandom) {

    public static RaidBossSpawn of(StatSet set) {
        return RaidBossSpawn.builder()
                .npcId(set.getInt("id"))
                .spawnPoint(SpawnPoint.of(set))
                .respawnDelay(set.getInt("respawnDelay"))
                .respawnRandom(set.getInt("respawnRandom"))
                .build();
    }
}
