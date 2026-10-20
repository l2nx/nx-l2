/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.sevensigns.model;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

/**
 * @param cabal {@code "dawn"} or {@code "dusk"}
 * @param scoredAt {@code null} while nobody scored
 * @param memberIds object ids of the scoring party
 */
@Builder(toBuilder = true)
public record SevenSignsFestivalScore(
        int cycle, int festivalId, String cabal, Instant scoredAt, int score, List<Integer> memberIds) {
    public SevenSignsFestivalScore {
        memberIds = List.copyOf(memberIds);
    }
}
