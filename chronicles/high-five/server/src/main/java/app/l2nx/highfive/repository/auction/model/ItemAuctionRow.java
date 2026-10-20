/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.auction.model;

import app.l2nx.highfive.entity.item.auction.ItemAuctionState;
import java.time.Instant;
import lombok.Builder;

/**
 * @param instanceId the auction manager NPC
 * @param auctionItemId the {@code ItemAuctions.xml} item
 */
@Builder(toBuilder = true)
public record ItemAuctionRow(
        int auctionId,
        int instanceId,
        int auctionItemId,
        Instant startingTime,
        Instant endingTime,
        ItemAuctionState state) {}
