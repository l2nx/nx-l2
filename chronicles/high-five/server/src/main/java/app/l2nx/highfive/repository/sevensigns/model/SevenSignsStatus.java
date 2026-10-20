/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.sevensigns.model;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

/**
 * Cabals are the {@code SevenSigns.CABAL_*} constants and the period a {@code SevenSigns.PERIOD_*} constant.
 *
 * @param savedAt {@code null} before the first save
 * @param accumulatedBonuses one value per festival, in festival order
 */
@Builder(toBuilder = true)
public record SevenSignsStatus(
        int currentCycle,
        int festivalCycle,
        int activePeriod,
        Instant savedAt,
        int previousWinner,
        double dawnStoneScore,
        int dawnFestivalScore,
        double duskStoneScore,
        int duskFestivalScore,
        int avariceOwner,
        int gnosisOwner,
        int strifeOwner,
        int avariceDawnScore,
        int gnosisDawnScore,
        int strifeDawnScore,
        int avariceDuskScore,
        int gnosisDuskScore,
        int strifeDuskScore,
        List<Integer> accumulatedBonuses) {
    public SevenSignsStatus {
        accumulatedBonuses = List.copyOf(accumulatedBonuses);
    }
}
