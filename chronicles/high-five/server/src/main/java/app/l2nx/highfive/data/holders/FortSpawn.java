/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.util.StatSet;
import lombok.Builder;

/**
 * @param type 0 always spawned, 1 commanders (despawned during the siege), 2 despawned before the siege, 3 special
 *     envoys (spawned after the fort is taken)
 * @param castleId the castle a special envoy offers a contract with; {@code null} for the other types
 */
@Builder(toBuilder = true)
public record FortSpawn(int type, int npcId, SpawnPoint spawnPoint, Integer castleId) {

    public static FortSpawn of(StatSet set) {
        return FortSpawn.builder()
                .type(set.getInt("type"))
                .npcId(set.getInt("npcId"))
                .spawnPoint(SpawnPoint.of(set))
                .castleId(set.contains("castleId") ? set.getInt("castleId") : null)
                .build();
    }
}
