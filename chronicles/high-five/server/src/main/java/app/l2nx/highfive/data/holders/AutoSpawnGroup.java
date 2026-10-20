/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.util.StatSet;
import java.util.List;
import lombok.Builder;

/**
 * A spawn of {@code AutoSpawnHandler}. A negative delay selects the handler default; a {@code despawnDelaySeconds} of
 * 0 keeps the NPC until it dies, and {@code respawnDelaySeconds} is then its respawn after a kill.
 */
@Builder(toBuilder = true)
public record AutoSpawnGroup(
        int npcId,
        int count,
        int initialDelaySeconds,
        int respawnDelaySeconds,
        int despawnDelaySeconds,
        boolean broadcastSpawn,
        boolean randomSpawn,
        List<SpawnPoint> locations) {

    public AutoSpawnGroup {
        locations = List.copyOf(locations);
    }

    public static AutoSpawnGroup of(StatSet set, List<SpawnPoint> locations) {
        return AutoSpawnGroup.builder()
                .npcId(set.getInt("npcId"))
                .count(set.getInt("count"))
                .initialDelaySeconds(set.getInt("initialDelaySeconds"))
                .respawnDelaySeconds(set.getInt("respawnDelaySeconds"))
                .despawnDelaySeconds(set.getInt("despawnDelaySeconds"))
                .broadcastSpawn(set.getBoolean("broadcastSpawn"))
                .randomSpawn(set.getBoolean("randomSpawn"))
                .locations(locations)
                .build();
    }
}
