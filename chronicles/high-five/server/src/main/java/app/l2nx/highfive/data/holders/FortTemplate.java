/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.util.StatSet;
import java.util.List;
import lombok.Builder;

/**
 * @param type 0 small, 1 large
 */
@Builder(toBuilder = true)
public record FortTemplate(int id, String name, int type, List<SiegeGuardSpawn> siegeGuards, List<FortSpawn> spawns) {

    public FortTemplate {
        siegeGuards = List.copyOf(siegeGuards);
        spawns = List.copyOf(spawns);
    }

    public static FortTemplate of(StatSet set, List<SiegeGuardSpawn> siegeGuards, List<FortSpawn> spawns) {
        return FortTemplate.builder()
                .id(set.getInt("id"))
                .name(set.getString("name"))
                .type(set.getInt("type"))
                .siegeGuards(siegeGuards)
                .spawns(spawns)
                .build();
    }
}
