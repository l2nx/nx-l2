/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.util.StatSet;
import java.util.List;
import lombok.Builder;

/**
 * A Dimensional Rift room. Area type 0 holds the waiting room (room 0) and the bounds of the whole rift (room 1).
 *
 * @param teleport where a party entering the room lands; {@code null} for the rift bounds
 */
@Builder(toBuilder = true)
public record RiftRoomTemplate(
        int type,
        int id,
        int xMin,
        int xMax,
        int yMin,
        int yMax,
        int zMin,
        int zMax,
        SpawnPoint teleport,
        boolean boss,
        List<RiftSpawn> spawns) {

    public RiftRoomTemplate {
        spawns = List.copyOf(spawns);
    }

    public static RiftRoomTemplate of(int type, StatSet set, List<RiftSpawn> spawns) {
        final SpawnPoint teleport = set.contains("teleportX")
                ? SpawnPoint.builder()
                        .x(set.getInt("teleportX"))
                        .y(set.getInt("teleportY"))
                        .z(set.getInt("teleportZ"))
                        .build()
                : null;
        return RiftRoomTemplate.builder()
                .type(type)
                .id(set.getInt("id"))
                .xMin(set.getInt("xMin"))
                .xMax(set.getInt("xMax"))
                .yMin(set.getInt("yMin"))
                .yMax(set.getInt("yMax"))
                .zMin(set.getInt("zMin"))
                .zMax(set.getInt("zMax"))
                .teleport(teleport)
                .boss(set.getBoolean("boss", false))
                .spawns(spawns)
                .build();
    }
}
