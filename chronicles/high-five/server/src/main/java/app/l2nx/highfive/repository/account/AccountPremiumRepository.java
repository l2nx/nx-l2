/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.account;

import app.l2nx.database.JdbcTimes;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountPremiumRepository {
    private static final String SELECT_ENDS_AT = "SELECT ends_at FROM account_premiums WHERE account_id = ?";
    private static final String UPSERT = """
        INSERT INTO account_premiums (account_id, ends_at)
        VALUES (?, ?)
        ON CONFLICT (account_id) DO UPDATE SET ends_at = EXCLUDED.ends_at\
        """;
    private static final String DELETE = "DELETE FROM account_premiums WHERE account_id = ?";

    private final JdbcClient jdbcClient;

    public Optional<Instant> findEndsAt(UUID accountId) {
        return jdbcClient
                .sql(SELECT_ENDS_AT)
                .param(accountId)
                .query((rs, rowNum) -> JdbcTimes.instant(rs, "ends_at"))
                .optional();
    }

    public void save(UUID accountId, Instant endsAt) {
        jdbcClient.sql(UPSERT).param(accountId).param(JdbcTimes.utc(endsAt)).update();
    }

    public void delete(UUID accountId) {
        jdbcClient.sql(DELETE).param(accountId).update();
    }
}
