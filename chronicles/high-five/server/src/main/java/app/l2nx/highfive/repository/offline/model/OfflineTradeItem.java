/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.offline.model;

import lombok.Builder;

/**
 * @param itemId by store type: the item object id ({@code SELL}, {@code PACKAGE_SELL}), the item template id
 *     ({@code BUY}), the skill id ({@code SELL_BUFFS}) or the recipe id ({@code MANUFACTURE})
 * @param count {@code 0} for buffs and recipes, which are not counted
 */
@Builder(toBuilder = true)
public record OfflineTradeItem(int itemId, long count, long price) {}
