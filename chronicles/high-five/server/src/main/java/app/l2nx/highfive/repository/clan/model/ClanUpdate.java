/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.clan.model;

import java.time.Instant;
import lombok.Builder;

/**
 * The clan columns the game saves together (leadership, alliance, reputation, penalties). Name, level, crests, blood
 * counts and the auction bid have their own updates.
 */
@Builder(toBuilder = true)
public record ClanUpdate(
        int clanId,
        int leaderId,
        int newLeaderId,
        int allyId,
        String allyName,
        int reputationScore,
        Instant allyPenaltyEndsAt,
        int allyPenaltyType,
        Instant characterPenaltyEndsAt,
        Instant dissolvingEndsAt) {}
