/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.minigame.model;

import lombok.Builder;

/**
 * @param first zero-based lane index of the winner
 * @param second zero-based lane index of the runner-up
 */
@Builder(toBuilder = true)
public record MonsterRaceResult(int raceId, int first, int second, double oddRate) {}
