/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterHennaRepository;
import app.l2nx.highfive.repository.character.model.CharacterHenna;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterHennaRepositoryTest extends RepositoryTest {
    private final CharacterHennaRepository repository = new CharacterHennaRepository(JDBC, registry);

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreHennaInTheSlotOfTheClassIndex() {
            final int objectId = registry.handleOf(insertCharacter());

            repository.insert(objectId, 1, henna(2, 180));

            assertThat(repository.findAll(objectId, 1)).containsExactly(henna(2, 180));
            assertThat(repository.findAll(objectId, 0)).isEmpty();
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnHennasBySlot() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.insert(objectId, 0, henna(3, 182));
            repository.insert(objectId, 0, henna(1, 180));

            assertThat(repository.findAll(objectId, 0)).containsExactly(henna(1, 180), henna(3, 182));
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveOnlyTheSlot() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.insert(objectId, 0, henna(1, 180));
            repository.insert(objectId, 0, henna(2, 181));

            repository.delete(objectId, 0, 1);

            assertThat(repository.findAll(objectId, 0)).containsExactly(henna(2, 181));
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldRemoveHennasOfTheClassIndexOnly() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.insert(objectId, 0, henna(1, 180));
            repository.insert(objectId, 1, henna(1, 180));

            repository.deleteAll(objectId, 1);

            assertThat(repository.findAll(objectId, 1)).isEmpty();
            assertThat(repository.findAll(objectId, 0)).hasSize(1);
        }
    }

    static CharacterHenna henna(int slot, int hennaId) {
        return CharacterHenna.builder().slot(slot).hennaId(hennaId).build();
    }
}
