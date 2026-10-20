/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterRecipeRepository;
import app.l2nx.highfive.repository.character.model.CharacterRecipe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterRecipeRepositoryTest extends RepositoryTest {
    private static final CharacterRecipe COMMON = recipe(1, 0, false);
    private static final CharacterRecipe DWARVEN = recipe(2, 1, true);

    private final CharacterRecipeRepository repository = new CharacterRecipeRepository(JDBC, registry);

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreCommonAndDwarvenRecipes() {
            final int objectId = registry.handleOf(insertCharacter());

            repository.insert(objectId, COMMON);
            repository.insert(objectId, DWARVEN);

            assertThat(repository.findAll(objectId)).containsExactlyInAnyOrder(COMMON, DWARVEN);
        }
    }

    @Nested
    class FindDwarvenRecipeIds {
        @Test
        void findDwarvenRecipeIds_shouldReturnDwarvenRecipesOfTheClassIndex() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.insert(objectId, COMMON);
            repository.insert(objectId, DWARVEN);
            repository.insert(objectId, recipe(3, 2, true));

            assertThat(repository.findDwarvenRecipeIds(objectId, 1)).containsExactly(2);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldBeEmpty_whenNoRecipes() {
            assertThat(repository.findAll(registry.handleOf(insertCharacter()))).isEmpty();
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveTheRecipeOfTheClassIndex() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.insert(objectId, COMMON);
            repository.insert(objectId, DWARVEN);

            repository.delete(objectId, 0, COMMON.recipeId());

            assertThat(repository.findAll(objectId)).containsExactly(DWARVEN);
        }
    }

    private static CharacterRecipe recipe(int recipeId, int classIndex, boolean dwarven) {
        return CharacterRecipe.builder()
                .recipeId(recipeId)
                .classIndex(classIndex)
                .dwarven(dwarven)
                .build();
    }
}
