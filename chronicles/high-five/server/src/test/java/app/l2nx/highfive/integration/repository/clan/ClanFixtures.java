/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.clan;

import app.l2nx.database.JdbcTimes;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.simple.JdbcClient;

final class ClanFixtures {
    // Residence ids are primary keys shared by every test of the JVM.
    private static final AtomicInteger RESIDENCE_IDS = new AtomicInteger(70_000);

    private ClanFixtures() {}

    static int nextResidenceId() {
        return RESIDENCE_IDS.incrementAndGet();
    }

    static int insertCrest(JdbcClient jdbc) {
        return jdbc.sql("INSERT INTO crests (crest_type, image) VALUES ('PLEDGE', ?) RETURNING id")
                .param(new byte[] {1})
                .query(Integer.class)
                .single();
    }

    static void joinClan(JdbcClient jdbc, UUID characterId, UUID clanId) {
        jdbc.sql("UPDATE characters SET clan_id = ? WHERE id = ?")
                .param(clanId)
                .param(characterId)
                .update();
    }

    static String characterName(JdbcClient jdbc, UUID characterId) {
        return jdbc.sql("SELECT name FROM characters WHERE id = ?")
                .param(characterId)
                .query(String.class)
                .single();
    }

    static String clanName(JdbcClient jdbc, UUID clanId) {
        return jdbc.sql("SELECT name FROM clans WHERE id = ?")
                .param(clanId)
                .query(String.class)
                .single();
    }

    static void setAlliance(JdbcClient jdbc, UUID clanId, UUID allyClanId, String allyName) {
        jdbc.sql("UPDATE clans SET ally_clan_id = ?, ally_name = ? WHERE id = ?")
                .param(allyClanId)
                .param(allyName)
                .param(clanId)
                .update();
    }

    static int insertAuctionBid(JdbcClient jdbc, UUID clanId, Instant now) {
        final int clanHallId = nextResidenceId();
        jdbc.sql("INSERT INTO clan_hall_auctions (clan_hall_id, starting_bid, ends_at) VALUES (?, 100, ?)")
                .param(clanHallId)
                .param(JdbcTimes.utc(now))
                .update();
        jdbc.sql("""
            INSERT INTO clan_hall_auction_bids (clan_hall_id, clan_id, bid_amount, bid_at)
            VALUES (?, ?, 100, ?)\
            """).param(clanHallId).param(clanId).param(JdbcTimes.utc(now)).update();
        return clanHallId;
    }

    static long count(JdbcClient jdbc, String table, UUID clanId) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE clan_id = ?")
                .param(clanId)
                .query(Long.class)
                .single();
    }
}
