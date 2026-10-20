/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.account;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.account.AccountPremiumRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AccountPremiumRepositoryTest extends RepositoryTest {
    private final AccountPremiumRepository repository = new AccountPremiumRepository(JDBC);

    @Nested
    class FindEndsAt {
        @Test
        void findEndsAt_shouldReturnEnd_whenPremium() {
            final UUID accountId = EntityIds.next();
            insertPremium(accountId, NOW);

            assertThat(repository.findEndsAt(accountId)).contains(NOW);
        }

        @Test
        void findEndsAt_shouldBeEmpty_whenNoPremium() {
            assertThat(repository.findEndsAt(EntityIds.next())).isEmpty();
        }
    }

    @Nested
    class Save {
        @Test
        void save_shouldInsert_whenNoPremium() {
            final UUID accountId = EntityIds.next();

            repository.save(accountId, NOW);

            assertThat(endsAt(accountId)).isEqualTo(NOW);
        }

        @Test
        void save_shouldReplaceEnd_whenPremium() {
            final UUID accountId = EntityIds.next();
            insertPremium(accountId, NOW);
            final Instant extended = NOW.plusSeconds(86_400);

            repository.save(accountId, extended);

            assertThat(endsAt(accountId)).isEqualTo(extended);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemovePremium() {
            final UUID accountId = EntityIds.next();
            insertPremium(accountId, NOW);

            repository.delete(accountId);

            assertThat(repository.findEndsAt(accountId)).isEmpty();
        }
    }

    private static void insertPremium(UUID accountId, Instant endsAt) {
        JDBC.sql("INSERT INTO account_premiums (account_id, ends_at) VALUES (?, ?)")
                .param(accountId)
                .param(JdbcTimes.utc(endsAt))
                .update();
    }

    private static Instant endsAt(UUID accountId) {
        return JDBC.sql("SELECT ends_at FROM account_premiums WHERE account_id = ?")
                .param(accountId)
                .query((rs, rowNum) -> JdbcTimes.instant(rs, "ends_at"))
                .single();
    }
}
