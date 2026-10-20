/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.util.StatSet;
import lombok.Builder;

/**
 * @param type 0 town (while the castle has an owner), 1 fortress and 2 castle (during the territory war), 3 ward
 *     spawn place ({@code npcId} unused)
 */
@Builder(toBuilder = true)
public record TerritorySpawn(int type, int npcId, SpawnPoint spawnPoint) {

    public static TerritorySpawn of(StatSet set) {
        return TerritorySpawn.builder()
                .type(set.getInt("type"))
                .npcId(set.getInt("npcId"))
                .spawnPoint(SpawnPoint.of(set))
                .build();
    }
}
