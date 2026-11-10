/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.item.model.ItemElemental;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ItemElementalRepository {
    private static final String SELECT_ELEMENTALS =
            "SELECT element, element_value FROM item_elementals WHERE item_id = ? ORDER BY element";
    private static final String SELECT_BY_OWNER = """
        SELECT e.item_id, e.element, e.element_value
        FROM item_elementals e
        JOIN items i ON i.id = e.item_id
        WHERE i.owner_id = ?
        ORDER BY e.item_id, e.element\
        """;
    private static final String INSERT_ELEMENTAL =
            "INSERT INTO item_elementals (item_id, element, element_value) VALUES (?, ?, ?)";
    private static final String DELETE_ELEMENTAL = "DELETE FROM item_elementals WHERE item_id = ? AND element = ?";
    private static final String DELETE_ELEMENTALS = "DELETE FROM item_elementals WHERE item_id = ?";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<ItemElemental> findAll(int objectId) {
        return registry.findId(objectId)
                .map(id -> jdbcClient
                        .sql(SELECT_ELEMENTALS)
                        .param(id)
                        .query((rs, rowNum) -> toElemental(rs))
                        .list())
                .orElse(List.of());
    }

    public Map<UUID, List<ItemElemental>> findByOwner(UUID ownerId) {
        final Map<UUID, List<ItemElemental>> elementals = new HashMap<>();
        jdbcClient.sql(SELECT_BY_OWNER).param(ownerId).query((RowCallbackHandler) rs -> elementals
                .computeIfAbsent(rs.getObject("item_id", UUID.class), id -> new ArrayList<>())
                .add(toElemental(rs)));
        return elementals;
    }

    /**
     * Skips NONE ({@code -1}) and negative-value elementals: restore never applies them.
     */
    @Transactional
    public void replaceAll(int objectId, Collection<ItemElemental> elementals) {
        final UUID itemId = References.required(registry, objectId);
        jdbcClient.sql(DELETE_ELEMENTALS).param(itemId).update();
        final List<Object[]> rows = elementals.stream()
                .filter(elemental -> (elemental.element() >= 0) && (elemental.value() >= 0))
                .map(elemental -> new Object[] {itemId, (short) elemental.element(), elemental.value()})
                .toList();
        jdbcTemplate.batchUpdate(INSERT_ELEMENTAL, rows);
    }

    public void delete(int objectId, byte element) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_ELEMENTAL)
                        .param(id)
                        .param((short) element)
                        .update());
    }

    public void deleteAll(int objectId) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient.sql(DELETE_ELEMENTALS).param(id).update());
    }

    private static ItemElemental toElemental(ResultSet rs) throws SQLException {
        return ItemElemental.builder()
                .element(rs.getByte("element"))
                .value(rs.getInt("element_value"))
                .build();
    }
}
