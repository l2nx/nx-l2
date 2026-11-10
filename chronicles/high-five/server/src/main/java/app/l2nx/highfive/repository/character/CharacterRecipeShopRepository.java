/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.character.model.CharacterRecipeShopItem;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class CharacterRecipeShopRepository {
    private static final String SELECT_ITEMS = """
        SELECT recipe_id, price FROM character_recipe_shop_items
        WHERE character_id = ?
        ORDER BY sort_order\
        """;
    private static final String UPSERT_ITEM = """
        INSERT INTO character_recipe_shop_items (character_id, recipe_id, price, sort_order)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (character_id, recipe_id) DO UPDATE SET price = EXCLUDED.price, sort_order = EXCLUDED.sort_order\
        """;
    private static final String DELETE_ITEMS = "DELETE FROM character_recipe_shop_items WHERE character_id = ?";

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final IdentityRegistry registry;

    public List<CharacterRecipeShopItem> findAll(UUID characterId) {
        return jdbcClient
                .sql(SELECT_ITEMS)
                .param(characterId)
                .query((rs, rowNum) -> CharacterRecipeShopItem.builder()
                        .recipeId(rs.getInt("recipe_id"))
                        .price(rs.getLong("price"))
                        .build())
                .list();
    }

    public List<CharacterRecipeShopItem> findAll(int objectId) {
        return registry.findId(objectId).map(this::findAll).orElse(List.of());
    }

    @Transactional
    public void replaceAll(int objectId, List<CharacterRecipeShopItem> items) {
        final UUID id = References.required(registry, objectId);
        jdbcClient.sql(DELETE_ITEMS).param(id).update();
        final List<Object[]> rows = new ArrayList<>(items.size());
        for (CharacterRecipeShopItem item : items) {
            rows.add(new Object[] {id, item.recipeId(), item.price(), rows.size() + 1});
        }

        jdbcTemplate.batchUpdate(UPSERT_ITEM, rows);
    }
}
