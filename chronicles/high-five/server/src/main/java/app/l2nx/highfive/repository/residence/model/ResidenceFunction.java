/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence.model;

import java.time.Instant;
import lombok.Builder;

/**
 * A paid function of a castle, fort or clan hall.
 *
 * @param rate fee period in milliseconds
 * @param endTime when the paid period ends, {@code null} before the first fee is charged
 */
@Builder(toBuilder = true)
public record ResidenceFunction(int type, int level, int lease, long rate, Instant endTime) {}
