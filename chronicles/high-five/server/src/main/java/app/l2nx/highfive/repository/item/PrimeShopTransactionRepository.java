/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PrimeShopTransactionRepository {
    private static final String INSERT_TRANSACTION = """
        INSERT INTO prime_shop_transactions (character_id, product_id, quantity, purchased_at)
        VALUES (?, ?, ?, ?)\
        """;
    private static final String SELECT_PRODUCT_IDS = """
        SELECT product_id
        FROM prime_shop_transactions
        WHERE character_id = ?
        ORDER BY purchased_at DESC, id DESC\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public void insert(int characterId, int productId, int quantity, Instant purchasedAt) {
        jdbcClient
                .sql(INSERT_TRANSACTION)
                .param(References.required(registry, characterId))
                .param(productId)
                .param(quantity)
                .param(JdbcTimes.utc(purchasedAt))
                .update();
    }

    /**
     * @return the product ids of the character's purchases, the latest first; a product bought twice appears twice
     */
    public List<Integer> findProductIds(int characterId) {
        return registry.findId(characterId)
                .map(id -> jdbcClient
                        .sql(SELECT_PRODUCT_IDS)
                        .param(id)
                        .query(Integer.class)
                        .list())
                .orElse(List.of());
    }
}
