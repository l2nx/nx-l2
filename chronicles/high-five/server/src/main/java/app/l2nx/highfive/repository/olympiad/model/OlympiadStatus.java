/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.olympiad.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param period {@code 0} competition, {@code 1} validation (the {@code Olympiad} period constants)
 * @param olympiadEnd {@code null} when not scheduled; the same for the other two instants
 */
@Builder(toBuilder = true)
public record OlympiadStatus(
        int currentCycle, int period, Instant olympiadEnd, Instant validationEnd, Instant nextWeeklyChange) {}
