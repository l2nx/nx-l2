/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.offline.model;

import app.l2nx.highfive.entity.actor.enums.player.PrivateStoreType;
import java.time.Instant;
import lombok.Builder;

/**
 * @param storeType {@link PrivateStoreType#SELL_BUFFS} for a buff seller, whose store runs as
 *     {@link PrivateStoreType#PACKAGE_SELL}
 */
@Builder(toBuilder = true)
public record OfflineTrade(int characterId, Instant startedAt, PrivateStoreType storeType, String title) {}
