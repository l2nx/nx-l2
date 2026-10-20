/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.util.StatSet;
import lombok.Builder;

/**
 * An immutable datapack position; {@link Location} is mutable and cannot be a record component.
 */
@Builder(toBuilder = true)
public record SpawnPoint(int x, int y, int z, int heading) {

    public static SpawnPoint of(StatSet set) {
        return SpawnPoint.builder()
                .x(set.getInt("x"))
                .y(set.getInt("y"))
                .z(set.getInt("z"))
                .heading(set.getInt("heading"))
                .build();
    }

    public Location toLocation() {
        return new Location(x, y, z, heading);
    }
}
