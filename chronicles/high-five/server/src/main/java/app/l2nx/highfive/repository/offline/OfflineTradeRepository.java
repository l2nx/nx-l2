/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.offline;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.entity.actor.enums.player.PrivateStoreType;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.offline.model.OfflineTrade;
import app.l2nx.highfive.repository.offline.model.OfflineTradeItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class OfflineTradeRepository {
    private static final String SELECT_TRADES =
            "SELECT character_id, started_at, store_type, title FROM character_offline_trades";
    private static final String UPSERT_TRADE = """
        INSERT INTO character_offline_trades (character_id, started_at, store_type, title)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (character_id) DO UPDATE
        SET started_at = EXCLUDED.started_at, store_type = EXCLUDED.store_type, title = EXCLUDED.title\
        """;
    private static final String DELETE_TRADE = "DELETE FROM character_offline_trades WHERE character_id = ?";
    private static final String DELETE_ALL_TRADES = "DELETE FROM character_offline_trades";
    private static final String SELECT_ITEMS = """
        SELECT item_id, target_id, item_count, price
        FROM character_offline_trade_items
        WHERE character_id = ?
        ORDER BY id\
        """;
    private static final String INSERT_ITEM = """
        INSERT INTO character_offline_trade_items (character_id, item_id, target_id, item_count, price)
        VALUES (?, ?, ?, ?, ?)\
        """;
    private static final String DELETE_ITEMS = "DELETE FROM character_offline_trade_items WHERE character_id = ?";
    private static final String DELETE_ALL_ITEMS = "DELETE FROM character_offline_trade_items";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<OfflineTrade> findAll() {
        return jdbcClient
                .sql(SELECT_TRADES)
                .query((rs, rowNum) -> OfflineTrade.builder()
                        .characterId(registry.handleOf(rs.getObject("character_id", UUID.class)))
                        .startedAt(JdbcTimes.instant(rs, "started_at"))
                        .storeType(PrivateStoreType.valueOf(rs.getString("store_type")))
                        .title(rs.getString("title"))
                        .build())
                .list();
    }

    public List<OfflineTradeItem> findItems(int characterId) {
        return registry.findId(characterId)
                .map(id -> jdbcClient
                        .sql(SELECT_ITEMS)
                        .param(id)
                        .query((rs, rowNum) -> {
                            final UUID itemId = rs.getObject("item_id", UUID.class);
                            return OfflineTradeItem.builder()
                                    .itemId(itemId != null ? registry.handleOf(itemId) : rs.getInt("target_id"))
                                    .count(rs.getLong("item_count"))
                                    .price(rs.getLong("price"))
                                    .build();
                        })
                        .list())
                .orElse(List.of());
    }

    @Transactional
    public void save(OfflineTrade trade, Collection<OfflineTradeItem> items) {
        final UUID characterId = References.required(registry, trade.characterId());
        jdbcClient.sql(DELETE_ITEMS).param(characterId).update();
        if (!items.isEmpty()) {
            final boolean itemInstances = listsItemInstances(trade.storeType());
            jdbcTemplate.batchUpdate(
                    INSERT_ITEM,
                    items.stream()
                            .map(item -> new Object[] {
                                characterId,
                                itemInstances ? References.required(registry, item.itemId()) : null,
                                itemInstances ? null : item.itemId(),
                                item.count(),
                                item.price()
                            })
                            .toList());
        }
        jdbcClient
                .sql(UPSERT_TRADE)
                .param(characterId)
                .param(JdbcTimes.utc(trade.startedAt()))
                .param(trade.storeType().name())
                .param(StringUtils.defaultIfEmpty(trade.title(), null))
                .update();
    }

    public void deleteItems(int characterId) {
        registry.findId(characterId)
                .ifPresent(id -> jdbcClient.sql(DELETE_ITEMS).param(id).update());
    }

    @Transactional
    public void delete(int characterId) {
        registry.findId(characterId).ifPresent(id -> {
            jdbcClient.sql(DELETE_ITEMS).param(id).update();
            jdbcClient.sql(DELETE_TRADE).param(id).update();
        });
    }

    @Transactional
    public void deleteAll() {
        jdbcClient.sql(DELETE_ALL_ITEMS).update();
        jdbcClient.sql(DELETE_ALL_TRADES).update();
    }

    private static boolean listsItemInstances(PrivateStoreType storeType) {
        return storeType == PrivateStoreType.SELL || storeType == PrivateStoreType.PACKAGE_SELL;
    }
}
