/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.item.model.GroundItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Items lying on the ground, saved as a whole snapshot.
 */
@Repository
@RequiredArgsConstructor
public class GroundItemRepository {
    private static final String SELECT_ITEMS = """
        SELECT id, item_template_id, count, enchant_level, x, y, z, dropped_at, protected
        FROM ground_items\
        """;
    private static final String INSERT_ITEM = """
        INSERT INTO ground_items (id, item_template_id, count, enchant_level, x, y, z, dropped_at, protected)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)\
        """;
    private static final String DELETE_ITEMS = "DELETE FROM ground_items";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<GroundItem> findAll() {
        return jdbcClient
                .sql(SELECT_ITEMS)
                .query((rs, rowNum) -> GroundItem.builder()
                        .objectId(registry.handleOf(rs.getObject("id", UUID.class)))
                        .itemId(rs.getInt("item_template_id"))
                        .count(rs.getLong("count"))
                        .enchantLevel(rs.getInt("enchant_level"))
                        .x(rs.getInt("x"))
                        .y(rs.getInt("y"))
                        .z(rs.getInt("z"))
                        .dropTime(JdbcTimes.instant(rs, "dropped_at"))
                        .protectedItem(rs.getBoolean("protected"))
                        .build())
                .list();
    }

    @Transactional
    public void replaceAll(Collection<GroundItem> items) {
        jdbcClient.sql(DELETE_ITEMS).update();
        final List<Object[]> rows = items.stream()
                .map(item -> new Object[] {
                    registry.idOf(item.objectId()),
                    item.itemId(),
                    item.count(),
                    item.enchantLevel(),
                    item.x(),
                    item.y(),
                    item.z(),
                    JdbcTimes.utc(item.dropTime()),
                    item.protectedItem()
                })
                .toList();
        jdbcTemplate.batchUpdate(INSERT_ITEM, rows);
    }

    public void deleteAll() {
        jdbcClient.sql(DELETE_ITEMS).update();
    }
}
