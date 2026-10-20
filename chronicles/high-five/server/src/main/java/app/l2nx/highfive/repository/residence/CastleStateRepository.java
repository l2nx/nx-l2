/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence;

import static app.l2nx.highfive.repository.References.requiredOrNull;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.residence.model.CastleState;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * A castle without a state row has its initial values; every write creates the row.
 */
@Repository
@RequiredArgsConstructor
public class CastleStateRepository {
    private static final String SELECT_BY_ID = """
        SELECT castle_id, owner_clan_id, tax_percent, treasury, siege_at, registration_ends_at, registration_over,
               show_npc_crest, ticket_buy_count
        FROM castle_states
        WHERE castle_id = ?\
        """;
    private static final String SELECT_ID_BY_OWNER = "SELECT castle_id FROM castle_states WHERE owner_clan_id = ?";
    private static final String UPSERT_OWNER = """
        INSERT INTO castle_states (castle_id, owner_clan_id) VALUES (?, ?)
        ON CONFLICT (castle_id) DO UPDATE SET owner_clan_id = EXCLUDED.owner_clan_id\
        """;
    // A clan holds one castle: the legacy clan column it replaces dropped the previous castle on a new win.
    private static final String RELEASE_OTHER_CASTLE =
            "UPDATE castle_states SET owner_clan_id = NULL WHERE owner_clan_id = ? AND castle_id <> ?";
    private static final String UPSERT_TREASURY = """
        INSERT INTO castle_states (castle_id, treasury) VALUES (?, ?)
        ON CONFLICT (castle_id) DO UPDATE SET treasury = EXCLUDED.treasury\
        """;
    private static final String UPSERT_TAX_PERCENT = """
        INSERT INTO castle_states (castle_id, tax_percent) VALUES (?, ?)
        ON CONFLICT (castle_id) DO UPDATE SET tax_percent = EXCLUDED.tax_percent\
        """;
    private static final String UPSERT_SHOW_NPC_CREST = """
        INSERT INTO castle_states (castle_id, show_npc_crest) VALUES (?, ?)
        ON CONFLICT (castle_id) DO UPDATE SET show_npc_crest = EXCLUDED.show_npc_crest\
        """;
    private static final String UPSERT_TICKET_BUY_COUNT = """
        INSERT INTO castle_states (castle_id, ticket_buy_count) VALUES (?, ?)
        ON CONFLICT (castle_id) DO UPDATE SET ticket_buy_count = EXCLUDED.ticket_buy_count\
        """;
    private static final String UPSERT_SIEGE_SCHEDULE = """
        INSERT INTO castle_states (castle_id, siege_at, registration_ends_at, registration_over) VALUES (?, ?, ?, ?)
        ON CONFLICT (castle_id) DO UPDATE
        SET siege_at = EXCLUDED.siege_at, registration_ends_at = EXCLUDED.registration_ends_at,
            registration_over = EXCLUDED.registration_over\
        """;
    private static final String RESET_TAX_OF_UNOWNED =
            "UPDATE castle_states SET tax_percent = 0 WHERE owner_clan_id IS NULL AND tax_percent <> 0";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public Optional<CastleState> findById(int castleId) {
        return jdbcClient
                .sql(SELECT_BY_ID)
                .param(castleId)
                .query((rs, rowNum) -> CastleState.builder()
                        .castleId(rs.getInt("castle_id"))
                        .ownerClanId(handleOrZero(rs.getObject("owner_clan_id", UUID.class)))
                        .taxPercent(rs.getInt("tax_percent"))
                        .treasury(rs.getLong("treasury"))
                        .siegeDate(JdbcTimes.instant(rs, "siege_at"))
                        .registrationEndDate(JdbcTimes.instant(rs, "registration_ends_at"))
                        .registrationOver(rs.getBoolean("registration_over"))
                        .showNpcCrest(rs.getBoolean("show_npc_crest"))
                        .ticketBuyCount(rs.getInt("ticket_buy_count"))
                        .build())
                .optional();
    }

    public OptionalInt findCastleIdByOwner(int clanId) {
        final Optional<UUID> id = registry.findId(clanId);
        if (id.isEmpty()) {
            return OptionalInt.empty();
        }

        return jdbcClient
                .sql(SELECT_ID_BY_OWNER)
                .param(id.get())
                .query(Integer.class)
                .optional()
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }

    /**
     * @param ownerClanId clan handle, {@code 0} to leave the castle without an owner
     */
    @Transactional
    public void updateOwner(int castleId, int ownerClanId) {
        final UUID ownerId = requiredOrNull(registry, ownerClanId);
        if (ownerId != null) {
            jdbcClient.sql(RELEASE_OTHER_CASTLE).param(ownerId).param(castleId).update();
        }

        jdbcClient.sql(UPSERT_OWNER).param(castleId).param(ownerId).update();
    }

    public void updateTreasury(int castleId, long treasury) {
        jdbcClient.sql(UPSERT_TREASURY).param(castleId).param(treasury).update();
    }

    public void updateTaxPercent(int castleId, int taxPercent) {
        jdbcClient.sql(UPSERT_TAX_PERCENT).param(castleId).param(taxPercent).update();
    }

    public void updateShowNpcCrest(int castleId, boolean showNpcCrest) {
        jdbcClient
                .sql(UPSERT_SHOW_NPC_CREST)
                .param(castleId)
                .param(showNpcCrest)
                .update();
    }

    public void updateTicketBuyCount(int castleId, int ticketBuyCount) {
        jdbcClient
                .sql(UPSERT_TICKET_BUY_COUNT)
                .param(castleId)
                .param(ticketBuyCount)
                .update();
    }

    /**
     * @param siegeDate {@code null} for none
     * @param registrationEndDate {@code null} for none
     */
    public void updateSiegeSchedule(
            int castleId, Instant siegeDate, Instant registrationEndDate, boolean registrationOver) {
        jdbcClient
                .sql(UPSERT_SIEGE_SCHEDULE)
                .param(castleId)
                .param(JdbcTimes.utc(siegeDate))
                .param(JdbcTimes.utc(registrationEndDate))
                .param(registrationOver)
                .update();
    }

    public int resetTaxOfUnownedCastles() {
        return jdbcClient.sql(RESET_TAX_OF_UNOWNED).update();
    }

    private int handleOrZero(UUID id) {
        return id == null ? 0 : registry.handleOf(id);
    }
}
