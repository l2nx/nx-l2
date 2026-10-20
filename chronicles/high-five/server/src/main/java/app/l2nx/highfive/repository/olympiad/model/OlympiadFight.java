/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.olympiad.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param winner {@code 1} first, {@code 2} second, {@code 0} draw
 */
@Builder(toBuilder = true)
public record OlympiadFight(
        int firstObjectId,
        int secondObjectId,
        int firstClassId,
        int secondClassId,
        int winner,
        Instant startedAt,
        long durationMs,
        boolean classed) {}
