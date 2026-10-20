/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.auction.model;

import lombok.Builder;

/**
 * @param lastBid {@code -1} for a cancelled bid, kept until the auction ends
 */
@Builder(toBuilder = true)
public record ItemAuctionBidRow(int characterId, long lastBid) {}
