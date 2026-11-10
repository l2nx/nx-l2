/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.character.model.CharacterRecipe;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The recipe book. A dwarven recipe belongs to one class index; a common recipe is shared by all classes and stored
 * with class index 0.
 */
@Repository
@RequiredArgsConstructor
public class CharacterRecipeRepository {
    private static final String SELECT_RECIPES =
            "SELECT recipe_id, class_index, dwarven FROM character_recipes WHERE character_id = ?";
    private static final String SELECT_DWARVEN_RECIPE_IDS = """
        SELECT recipe_id FROM character_recipes
        WHERE character_id = ? AND class_index = ? AND dwarven\
        """;
    private static final String INSERT_RECIPE =
            "INSERT INTO character_recipes (character_id, class_index, recipe_id, dwarven) VALUES (?, ?, ?, ?)";
    private static final String DELETE_RECIPE =
            "DELETE FROM character_recipes WHERE character_id = ? AND class_index = ? AND recipe_id = ?";

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<CharacterRecipe> findAll(UUID characterId) {
        return jdbcClient
                .sql(SELECT_RECIPES)
                .param(characterId)
                .query((rs, rowNum) -> CharacterRecipe.builder()
                        .recipeId(rs.getInt("recipe_id"))
                        .classIndex(rs.getInt("class_index"))
                        .dwarven(rs.getBoolean("dwarven"))
                        .build())
                .list();
    }

    public List<CharacterRecipe> findAll(int objectId) {
        return registry.findId(objectId).map(this::findAll).orElse(List.of());
    }

    public List<Integer> findDwarvenRecipeIds(int objectId, int classIndex) {
        return registry.findId(objectId)
                .map(id -> jdbcClient
                        .sql(SELECT_DWARVEN_RECIPE_IDS)
                        .param(id)
                        .param(classIndex)
                        .query(Integer.class)
                        .list())
                .orElse(List.of());
    }

    public void insert(int objectId, CharacterRecipe recipe) {
        jdbcClient
                .sql(INSERT_RECIPE)
                .param(References.required(registry, objectId))
                .param(recipe.classIndex())
                .param(recipe.recipeId())
                .param(recipe.dwarven())
                .update();
    }

    public void delete(int objectId, int classIndex, int recipeId) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient
                        .sql(DELETE_RECIPE)
                        .param(id)
                        .param(classIndex)
                        .param(recipeId)
                        .update());
    }
}
