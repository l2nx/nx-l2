/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.item;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.item.PrimeShopTransactionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PrimeShopTransactionRepositoryTest extends RepositoryTest {
    private final PrimeShopTransactionRepository repository = new PrimeShopTransactionRepository(JDBC, registry);

    @Nested
    class Insert {
        @Test
        void insert_shouldStorePurchaseOfTheCharacter() {
            final UUID character = insertCharacter();

            repository.insert(registry.handleOf(character), 1001, 3, NOW);

            final List<Object> actual = JDBC.sql(
                            "SELECT product_id, quantity, purchased_at FROM prime_shop_transactions WHERE character_id"
                                    + " = ?")
                    .param(character)
                    .query((rs, rowNum) -> List.<Object>of(
                            rs.getInt("product_id"), rs.getInt("quantity"), JdbcTimes.instant(rs, "purchased_at")))
                    .single();

            assertThat(actual).containsExactly(1001, 3, NOW);
        }
    }

    @Nested
    class FindProductIds {
        @Test
        void findProductIds_shouldReturnLatestFirst() {
            final int character = registry.handleOf(insertCharacter());
            repository.insert(character, 1, 1, NOW.minusSeconds(60));
            repository.insert(character, 2, 1, NOW);
            repository.insert(character, 1, 1, NOW.minusSeconds(120));
            repository.insert(registry.handleOf(insertCharacter()), 3, 1, NOW);

            assertThat(repository.findProductIds(character)).containsExactly(2, 1, 1);
        }
    }
}
