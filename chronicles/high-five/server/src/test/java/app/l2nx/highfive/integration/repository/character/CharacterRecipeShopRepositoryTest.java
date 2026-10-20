/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterRecipeShopRepository;
import app.l2nx.highfive.repository.character.model.CharacterRecipeShopItem;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterRecipeShopRepositoryTest extends RepositoryTest {
    private final CharacterRecipeShopRepository repository =
            new CharacterRecipeShopRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class ReplaceAll {
        @Test
        void replaceAll_shouldKeepListOrderAndDropPreviousItems() {
            final int objectId = registry.handleOf(insertCharacter());
            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(objectId, List.of(item(9, 1))));
            final List<CharacterRecipeShopItem> expected = List.of(item(20, 5000), item(10, 300));

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(objectId, expected));

            assertThat(repository.findAll(objectId)).containsExactlyElementsOf(expected);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldBeEmpty_whenNoShop() {
            assertThat(repository.findAll(registry.handleOf(insertCharacter()))).isEmpty();
        }
    }

    private static CharacterRecipeShopItem item(int recipeId, long price) {
        return CharacterRecipeShopItem.builder().recipeId(recipeId).price(price).build();
    }
}
