/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.olympiad.model;

import lombok.Builder;

/**
 * @param characterName read from the character; not written
 */
@Builder(toBuilder = true)
public record OlympiadNoble(
        int objectId,
        String characterName,
        int classId,
        int olympiadPoints,
        int competitionsDone,
        int competitionsWon,
        int competitionsLost,
        int competitionsDrawn,
        int competitionsDoneWeek,
        int competitionsDoneWeekClassed,
        int competitionsDoneWeekNonClassed,
        int competitionsDoneWeekTeam) {}
