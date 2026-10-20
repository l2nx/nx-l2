/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.util.StatSet;
import lombok.Builder;

/**
 * @param delay respawn delay, seconds
 */
@Builder(toBuilder = true)
public record RiftSpawn(int npcId, int count, int delay) {

    public static RiftSpawn of(StatSet set) {
        return RiftSpawn.builder()
                .npcId(set.getInt("mobId"))
                .count(set.getInt("count"))
                .delay(set.getInt("delay"))
                .build();
    }
}
