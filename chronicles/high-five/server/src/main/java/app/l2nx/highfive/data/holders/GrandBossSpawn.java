/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.util.StatSet;
import lombok.Builder;

/**
 * @param spawnPoint where the boss appears after a respawn; {@code null} when its script places it
 */
@Builder(toBuilder = true)
public record GrandBossSpawn(int npcId, SpawnPoint spawnPoint) {

    public static GrandBossSpawn of(StatSet set) {
        final boolean placed = set.contains("x") || set.contains("y") || set.contains("z") || set.contains("heading");
        return GrandBossSpawn.builder()
                .npcId(set.getInt("id"))
                .spawnPoint(placed ? SpawnPoint.of(set) : null)
                .build();
    }
}
