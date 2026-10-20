/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.sevensigns.model;

import lombok.Builder;

/**
 * @param cabal {@code "dawn"}, {@code "dusk"}, or {@code null} after the cycle reset
 * @param seal one of the {@code SevenSigns.SEAL_*} constants, {@code SEAL_NULL} after the cycle reset
 */
@Builder(toBuilder = true)
public record SevenSignsParticipant(
        int objectId,
        String cabal,
        int seal,
        int redStones,
        int greenStones,
        int blueStones,
        double ancientAdenaAmount,
        double contributionScore) {}
