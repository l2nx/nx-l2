/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.character.model.CharacterPremiumItem;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Items delivered to a character by an outside writer (the shop) and withdrawn in game; the game never inserts.
 */
@Repository
@RequiredArgsConstructor
public class CharacterPremiumItemRepository {
    private static final String SELECT_BY_CHARACTER = """
        SELECT item_num, item_template_id, item_count, sender_name
        FROM character_premium_items
        WHERE character_id = ?
        ORDER BY item_num\
        """;
    private static final String UPDATE_COUNT =
            "UPDATE character_premium_items SET item_count = ? WHERE character_id = ? AND item_num = ?";
    private static final String DELETE = "DELETE FROM character_premium_items WHERE character_id = ? AND item_num = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<CharacterPremiumItem> findByCharacter(int characterId) {
        return registry.findId(characterId)
                .map(id -> jdbcClient
                        .sql(SELECT_BY_CHARACTER)
                        .param(id)
                        .query((rs, rowNum) -> CharacterPremiumItem.builder()
                                .itemNum(rs.getInt("item_num"))
                                .itemTemplateId(rs.getInt("item_template_id"))
                                .count(rs.getLong("item_count"))
                                .senderName(rs.getString("sender_name"))
                                .build())
                        .list())
                .orElse(List.of());
    }

    public void updateCount(int characterId, int itemNum, long count) {
        registry.findId(characterId)
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_COUNT)
                        .param(count)
                        .param(id)
                        .param(itemNum)
                        .update());
    }

    public void delete(int characterId, int itemNum) {
        registry.findId(characterId)
                .ifPresent(id -> jdbcClient.sql(DELETE).param(id).param(itemNum).update());
    }
}
