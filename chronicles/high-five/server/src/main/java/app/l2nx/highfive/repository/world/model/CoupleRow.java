/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.world.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param marriedAt {@code null} while only engaged
 */
@Builder(toBuilder = true)
public record CoupleRow(long id, int firstObjectId, int secondObjectId, Instant engagedAt, Instant marriedAt) {}
