/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterRaidPointRepository;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("character_raid_points")
class CharacterRaidPointRepositoryTest extends RepositoryTest {
    private final CharacterRaidPointRepository repository = new CharacterRaidPointRepository(JDBC, registry);

    @Nested
    class Save {
        @Test
        void save_shouldInsertThenReplacePoints() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.save(objectId, 25001, 10);

            repository.save(objectId, 25001, 30);
            repository.save(objectId, 25002, 5);

            assertThat(repository.findAll()).containsEntry(objectId, Map.of(25001, 30, 25002, 5));
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldGroupPointsByCharacter() {
            final int first = registry.handleOf(insertCharacter());
            final int second = registry.handleOf(insertCharacter());
            repository.save(first, 25001, 10);
            repository.save(second, 25001, 20);

            assertThat(repository.findAll())
                    .containsEntry(first, Map.of(25001, 10))
                    .containsEntry(second, Map.of(25001, 20));
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldRemoveEveryCharacterPoints() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.save(objectId, 25001, 10);

            repository.deleteAll();

            assertThat(repository.findAll()).isEmpty();
        }
    }
}
