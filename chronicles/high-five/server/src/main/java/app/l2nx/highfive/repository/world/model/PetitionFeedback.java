/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.world.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param rating client value, 0 (very poor) to 4 (very good)
 */
@Builder(toBuilder = true)
public record PetitionFeedback(int objectId, int gmObjectId, int rating, String message, Instant createdAt) {}
