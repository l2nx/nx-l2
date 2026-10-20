/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.repository.item.model.BuylistStock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BuylistStockRepository {
    private static final String SELECT_STOCKS =
            "SELECT buylist_id, item_template_id, stock_count, restocks_at FROM buylist_stocks";
    private static final String UPSERT_STOCK = """
        INSERT INTO buylist_stocks (buylist_id, item_template_id, stock_count, restocks_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (buylist_id, item_template_id)
        DO UPDATE SET stock_count = EXCLUDED.stock_count, restocks_at = EXCLUDED.restocks_at\
        """;

    private final JdbcClient jdbcClient;

    public List<BuylistStock> findAll() {
        return jdbcClient
                .sql(SELECT_STOCKS)
                .query((rs, rowNum) -> BuylistStock.builder()
                        .buyListId(rs.getInt("buylist_id"))
                        .itemId(rs.getInt("item_template_id"))
                        .count(rs.getLong("stock_count"))
                        .nextRestockTime(JdbcTimes.instant(rs, "restocks_at"))
                        .build())
                .list();
    }

    public void save(BuylistStock stock) {
        jdbcClient
                .sql(UPSERT_STOCK)
                .param(stock.buyListId())
                .param(stock.itemId())
                .param(stock.count())
                .param(JdbcTimes.utc(stock.nextRestockTime()))
                .update();
    }
}
