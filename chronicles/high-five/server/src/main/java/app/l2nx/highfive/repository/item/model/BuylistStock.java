/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param nextRestockTime {@code null} when no restock is scheduled
 */
@Builder(toBuilder = true)
public record BuylistStock(int buyListId, int itemId, long count, Instant nextRestockTime) {}
