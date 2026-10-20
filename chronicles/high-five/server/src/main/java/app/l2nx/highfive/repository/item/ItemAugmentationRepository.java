/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The augmentation of an item; an item without one has no row. The item row must exist first.
 */
@Repository
@RequiredArgsConstructor
public class ItemAugmentationRepository {
    private static final String SELECT_AUGMENTATION =
            "SELECT augmentation_id FROM item_augmentations WHERE item_id = ?";
    private static final String UPSERT_AUGMENTATION = """
        INSERT INTO item_augmentations (item_id, augmentation_id)
        VALUES (?, ?)
        ON CONFLICT (item_id) DO UPDATE SET augmentation_id = EXCLUDED.augmentation_id\
        """;
    private static final String DELETE_AUGMENTATION = "DELETE FROM item_augmentations WHERE item_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public Optional<Integer> find(int objectId) {
        return registry.findId(objectId)
                .flatMap(id -> jdbcClient
                        .sql(SELECT_AUGMENTATION)
                        .param(id)
                        .query(Integer.class)
                        .optional());
    }

    public void save(int objectId, int augmentationId) {
        jdbcClient
                .sql(UPSERT_AUGMENTATION)
                .param(References.required(registry, objectId))
                .param(augmentationId)
                .update();
    }

    public void delete(int objectId) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient.sql(DELETE_AUGMENTATION).param(id).update());
    }
}
