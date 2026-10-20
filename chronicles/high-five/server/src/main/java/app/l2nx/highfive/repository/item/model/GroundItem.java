/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param dropTime {@code null} for a protected item and for an item exempt from auto-destroy
 */
@Builder(toBuilder = true)
public record GroundItem(
        int objectId,
        int itemId,
        long count,
        int enchantLevel,
        int x,
        int y,
        int z,
        Instant dropTime,
        boolean protectedItem) {}
