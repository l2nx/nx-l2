/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.util.StatSet;
import java.util.List;
import lombok.Builder;

/**
 * @param siegeGuards spawned while the castle has no owner; an owned castle spawns its hired mercenaries instead
 */
@Builder(toBuilder = true)
public record CastleTemplate(int id, String name, List<SiegeGuardSpawn> siegeGuards) {

    public CastleTemplate {
        siegeGuards = List.copyOf(siegeGuards);
    }

    public static CastleTemplate of(StatSet set, List<SiegeGuardSpawn> siegeGuards) {
        return CastleTemplate.builder()
                .id(set.getInt("id"))
                .name(set.getString("name"))
                .siegeGuards(siegeGuards)
                .build();
    }
}
