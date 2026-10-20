/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.auction.model;

import java.time.Instant;
import lombok.Builder;

/**
 * The auction of a clan hall; the auction id is the clan hall id.
 *
 * @param sellerClanId the clan selling its hall, {@code 0} when the NPC sells it
 * @param currentBid {@code 0} for none
 */
@Builder(toBuilder = true)
public record ClanHallAuctionRow(
        int clanHallId, int sellerClanId, long startingBid, long currentBid, Instant endDate) {}
