/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.item;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.item.BuylistStockRepository;
import app.l2nx.highfive.repository.item.model.BuylistStock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BuylistStockRepositoryTest extends RepositoryTest {
    private static final AtomicInteger BUYLIST_IDS = new AtomicInteger(1000);

    private final BuylistStockRepository repository = new BuylistStockRepository(JDBC);

    @Nested
    class Save {
        @Test
        void save_shouldInsertThenUpdateStock() {
            final BuylistStock stock = stock(NOW);
            repository.save(stock.toBuilder().count(1).build());

            repository.save(stock);

            assertThat(repository.findAll())
                    .filteredOn(row -> row.buyListId() == stock.buyListId())
                    .containsExactly(stock);
        }

        @Test
        void save_shouldStoreNoRestock_whenTimeIsNull() {
            final BuylistStock stock = stock(null);

            repository.save(stock);

            assertThat(JDBC.sql("SELECT restocks_at IS NULL FROM buylist_stocks WHERE buylist_id = ?")
                            .param(stock.buyListId())
                            .query(Boolean.class)
                            .single())
                    .isTrue();
            assertThat(repository.findAll())
                    .filteredOn(row -> row.buyListId() == stock.buyListId())
                    .containsExactly(stock);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnEveryProductOfTheBuylist() {
            final BuylistStock first = stock(null);
            final BuylistStock second = first.toBuilder().itemId(58).build();
            repository.save(first);
            repository.save(second);

            assertThat(repository.findAll())
                    .filteredOn(row -> row.buyListId() == first.buyListId())
                    .containsExactlyInAnyOrder(first, second);
        }
    }

    private static BuylistStock stock(Instant nextRestockTime) {
        return BuylistStock.builder()
                .buyListId(BUYLIST_IDS.incrementAndGet())
                .itemId(57)
                .count(15)
                .nextRestockTime(nextRestockTime)
                .build();
    }
}
