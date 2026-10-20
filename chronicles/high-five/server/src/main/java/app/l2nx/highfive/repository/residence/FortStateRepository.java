/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence;

import static app.l2nx.highfive.repository.References.requiredOrNull;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.residence.model.FortState;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * A fort without a state row has its initial values; every write creates the row.
 */
@Repository
@RequiredArgsConstructor
public class FortStateRepository {
    private static final String SELECT_BY_ID = """
        SELECT fort_id, owner_clan_id, owned_since, siege_at, contract_state, contracted_castle_id, supply_level
        FROM fort_states
        WHERE fort_id = ?\
        """;
    private static final String UPSERT_OWNER = """
        INSERT INTO fort_states (fort_id, owner_clan_id, owned_since, contract_state, contracted_castle_id)
        VALUES (?, ?, ?, 0, NULL)
        ON CONFLICT (fort_id) DO UPDATE
        SET owner_clan_id = EXCLUDED.owner_clan_id, owned_since = EXCLUDED.owned_since, contract_state = 0,
            contracted_castle_id = NULL\
        """;
    private static final String UPSERT_CONTRACT = """
        INSERT INTO fort_states (fort_id, contract_state, contracted_castle_id) VALUES (?, ?, ?)
        ON CONFLICT (fort_id) DO UPDATE
        SET contract_state = EXCLUDED.contract_state, contracted_castle_id = EXCLUDED.contracted_castle_id\
        """;
    private static final String UPSERT_SIEGE_DATE = """
        INSERT INTO fort_states (fort_id, siege_at) VALUES (?, ?)
        ON CONFLICT (fort_id) DO UPDATE SET siege_at = EXCLUDED.siege_at\
        """;
    private static final String UPSERT_SUPPLY_LEVEL = """
        INSERT INTO fort_states (fort_id, supply_level) VALUES (?, ?)
        ON CONFLICT (fort_id) DO UPDATE SET supply_level = EXCLUDED.supply_level\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public Optional<FortState> findById(int fortId) {
        return jdbcClient
                .sql(SELECT_BY_ID)
                .param(fortId)
                .query((rs, rowNum) -> {
                    final UUID ownerId = rs.getObject("owner_clan_id", UUID.class);
                    return FortState.builder()
                            .fortId(rs.getInt("fort_id"))
                            .ownerClanId(ownerId == null ? 0 : registry.handleOf(ownerId))
                            .lastOwnedTime(JdbcTimes.instant(rs, "owned_since"))
                            .siegeDate(JdbcTimes.instant(rs, "siege_at"))
                            .contractState(rs.getInt("contract_state"))
                            .contractedCastleId(rs.getInt("contracted_castle_id"))
                            .supplyLevel(rs.getInt("supply_level"))
                            .build();
                })
                .optional();
    }

    /**
     * Sets the owner and resets the castle contract.
     *
     * @param ownerClanId clan handle, {@code 0} to leave the fort without an owner
     * @param lastOwnedTime when the owner took the fort, {@code null} for none
     */
    public void updateOwner(int fortId, int ownerClanId, Instant lastOwnedTime) {
        jdbcClient
                .sql(UPSERT_OWNER)
                .param(fortId)
                .param(requiredOrNull(registry, ownerClanId))
                .param(JdbcTimes.utc(lastOwnedTime))
                .update();
    }

    /**
     * @param contractedCastleId {@code 0} for none
     */
    public void updateContract(int fortId, int contractState, int contractedCastleId) {
        jdbcClient
                .sql(UPSERT_CONTRACT)
                .param(fortId)
                .param(contractState)
                .param(contractedCastleId == 0 ? null : contractedCastleId)
                .update();
    }

    /**
     * @param siegeDate {@code null} for none
     */
    public void updateSiegeDate(int fortId, Instant siegeDate) {
        jdbcClient
                .sql(UPSERT_SIEGE_DATE)
                .param(fortId)
                .param(JdbcTimes.utc(siegeDate))
                .update();
    }

    public void updateSupplyLevel(int fortId, int supplyLevel) {
        jdbcClient.sql(UPSERT_SUPPLY_LEVEL).param(fortId).param(supplyLevel).update();
    }
}
