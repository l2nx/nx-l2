/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.util.StatSet;
import java.util.List;
import lombok.Builder;

@Builder(toBuilder = true)
public record TerritoryTemplate(int id, int castleId, int fortId, List<TerritorySpawn> spawns) {

    public TerritoryTemplate {
        spawns = List.copyOf(spawns);
    }

    public static TerritoryTemplate of(StatSet set, List<TerritorySpawn> spawns) {
        return TerritoryTemplate.builder()
                .id(set.getInt("id"))
                .castleId(set.getInt("castleId"))
                .fortId(set.getInt("fortId"))
                .spawns(spawns)
                .build();
    }
}
