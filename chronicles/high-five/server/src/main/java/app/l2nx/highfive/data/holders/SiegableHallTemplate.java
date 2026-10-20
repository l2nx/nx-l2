/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.util.StatSet;
import java.time.Duration;
import java.util.List;
import lombok.Builder;

/**
 * A siegable clan hall. The next siege is the last one plus {@code scheduleDays}, {@code scheduleMonths} and
 * {@code scheduleYears}, at {@code siegeHour}:{@code siegeMinute}.
 */
@Builder(toBuilder = true)
public record SiegableHallTemplate(
        int id,
        String name,
        String description,
        String location,
        Duration siegeLength,
        int scheduleDays,
        int scheduleMonths,
        int scheduleYears,
        int siegeHour,
        int siegeMinute,
        List<SiegeGuardSpawn> siegeGuards) {

    public SiegableHallTemplate {
        siegeGuards = List.copyOf(siegeGuards);
    }

    public static SiegableHallTemplate of(StatSet set, List<SiegeGuardSpawn> siegeGuards) {
        return SiegableHallTemplate.builder()
                .id(set.getInt("id"))
                .name(set.getString("name"))
                .description(set.getString("description"))
                .location(set.getString("location"))
                .siegeLength(Duration.ofMillis(set.getLong("siegeLengthMs")))
                .scheduleDays(set.getInt("scheduleDays"))
                .scheduleMonths(set.getInt("scheduleMonths"))
                .scheduleYears(set.getInt("scheduleYears"))
                .siegeHour(set.getInt("siegeHour"))
                .siegeMinute(set.getInt("siegeMinute"))
                .siegeGuards(siegeGuards)
                .build();
    }
}
