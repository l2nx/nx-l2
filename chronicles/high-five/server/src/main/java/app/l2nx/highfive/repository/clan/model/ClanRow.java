/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.clan.model;

import java.time.Instant;
import lombok.Builder;

/** Ids are wire handles, 0 for none; times are {@code null} for none. */
@Builder(toBuilder = true)
public record ClanRow(
        int clanId,
        String name,
        int level,
        int reputationScore,
        int bloodAllianceCount,
        int bloodOathCount,
        int leaderId,
        int newLeaderId,
        int allyId,
        String allyName,
        int crestId,
        int crestLargeId,
        int allyCrestId,
        int bidClanHallId,
        Instant allyPenaltyEndsAt,
        int allyPenaltyType,
        Instant characterPenaltyEndsAt,
        Instant dissolvingEndsAt) {}
