/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.util.StatSet;
import lombok.Builder;

/**
 * @param location the town name the community board region list matches
 * @param lease adena per lease period
 * @param auctionStartingBid adena, the starting bid of the NPC auction of a free hall; {@code null} when the NPC
 *     never auctions the hall
 */
@Builder(toBuilder = true)
public record ClanHallTemplate(
        int id, String name, String description, String location, int lease, int grade, Long auctionStartingBid) {

    public static ClanHallTemplate of(StatSet set) {
        return ClanHallTemplate.builder()
                .id(set.getInt("id"))
                .name(set.getString("name"))
                .description(set.getString("description"))
                .location(set.getString("location"))
                .lease(set.getInt("lease"))
                .grade(set.getInt("grade"))
                .auctionStartingBid(set.contains("auctionStartingBid") ? set.getLong("auctionStartingBid") : null)
                .build();
    }
}
