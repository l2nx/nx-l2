/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.forum;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.forum.model.ForumRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ForumRepository {
    // Index = the Forum type and permission constants.
    private static final List<String> TYPES = List.of("ROOT", "NORMAL", "CLAN", "MEMO", "MAIL");
    private static final List<String> PERMISSIONS = List.of("INVISIBLE", "ALL", "CLANMEMBERONLY", "OWNERONLY");
    private static final int NORMAL = 1;
    private static final int CLAN = 2;
    private static final int MAIL = 4;

    private static final String SELECT_BY_ID = """
        SELECT id, parent_id, name, forum_type, permission, clan_id, character_id, account_id
        FROM forums
        WHERE id = ?\
        """;
    private static final String SELECT_CHILD_IDS = "SELECT id FROM forums WHERE parent_id = ? ORDER BY id";
    private static final String SELECT_ROOT_IDS = "SELECT id FROM forums WHERE forum_type = 'ROOT' ORDER BY id";
    private static final String INSERT = """
        INSERT INTO forums (parent_id, name, forum_type, permission, clan_id, character_id, account_id)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        RETURNING id\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public Optional<ForumRow> findById(int id) {
        return jdbcClient
                .sql(SELECT_BY_ID)
                .param(id)
                .query((rs, rowNum) -> {
                    final UUID clanId = rs.getObject("clan_id", UUID.class);
                    final UUID characterId = rs.getObject("character_id", UUID.class);
                    final int ownerId;
                    if (clanId != null) {
                        ownerId = registry.handleOf(clanId);
                    } else if (characterId != null) {
                        ownerId = registry.handleOf(characterId);
                    } else {
                        ownerId = 0;
                    }
                    return ForumRow.builder()
                            .id(rs.getInt("id"))
                            .parentId(rs.getInt("parent_id"))
                            .name(rs.getString("name"))
                            .type(TYPES.indexOf(rs.getString("forum_type")))
                            .permission(PERMISSIONS.indexOf(rs.getString("permission")))
                            .ownerId(ownerId)
                            .accountId(rs.getObject("account_id", UUID.class))
                            .build();
                })
                .optional();
    }

    public List<Integer> findChildIds(int parentId) {
        return jdbcClient
                .sql(SELECT_CHILD_IDS)
                .param(parentId)
                .query(Integer.class)
                .list();
    }

    public List<Integer> findRootIds() {
        return jdbcClient.sql(SELECT_ROOT_IDS).query(Integer.class).list();
    }

    /**
     * @return the id of the new forum; {@code forum.id()} is ignored, and so is the name of an owned forum, which
     *     the owner column replaces
     */
    public int insert(ForumRow forum) {
        return jdbcClient
                .sql(INSERT)
                .param(forum.parentId() == 0 ? null : forum.parentId())
                .param(forum.type() <= NORMAL ? forum.name() : null)
                .param(TYPES.get(forum.type()))
                .param(PERMISSIONS.get(forum.permission()))
                .param(forum.type() == CLAN ? References.required(registry, forum.ownerId()) : null)
                .param(forum.type() == MAIL ? References.required(registry, forum.ownerId()) : null)
                .param(forum.accountId())
                .query(Integer.class)
                .single();
    }
}
