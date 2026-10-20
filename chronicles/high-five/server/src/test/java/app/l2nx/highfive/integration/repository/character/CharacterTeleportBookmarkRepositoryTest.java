/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterTeleportBookmarkRepository;
import app.l2nx.highfive.repository.character.model.CharacterTeleportBookmark;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterTeleportBookmarkRepositoryTest extends RepositoryTest {
    private static final CharacterTeleportBookmark BOOKMARK = CharacterTeleportBookmark.builder()
            .bookmarkId(1)
            .x(-84318)
            .y(244579)
            .z(-3730)
            .icon(2)
            .tag("TI")
            .name("Talking Island")
            .build();

    private final CharacterTeleportBookmarkRepository repository =
            new CharacterTeleportBookmarkRepository(JDBC, registry);

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreBookmark() {
            final int objectId = registry.handleOf(insertCharacter());

            repository.insert(objectId, BOOKMARK);

            assertThat(repository.findAll(objectId)).containsExactly(BOOKMARK);
        }

        @Test
        void insert_shouldStoreNullTag_whenTagEmpty() {
            final int objectId = registry.handleOf(insertCharacter());

            repository.insert(objectId, BOOKMARK.toBuilder().tag("").build());

            assertThat(repository.findAll(objectId))
                    .containsExactly(BOOKMARK.toBuilder().tag(null).build());
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldOrderByBookmarkId() {
            final int objectId = registry.handleOf(insertCharacter());
            final CharacterTeleportBookmark second =
                    BOOKMARK.toBuilder().bookmarkId(2).build();
            repository.insert(objectId, second);
            repository.insert(objectId, BOOKMARK);

            assertThat(repository.findAll(objectId)).containsExactly(BOOKMARK, second);
        }
    }

    @Nested
    class Update {
        @Test
        void update_shouldChangeIconTagAndName() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.insert(objectId, BOOKMARK);

            repository.update(objectId, 1, 5, "", "Gludin");

            assertThat(repository.findAll(objectId))
                    .containsExactly(BOOKMARK.toBuilder()
                            .icon(5)
                            .tag(null)
                            .name("Gludin")
                            .build());
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveTheBookmark() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.insert(objectId, BOOKMARK);

            repository.delete(objectId, 1);

            assertThat(repository.findAll(objectId)).isEmpty();
        }
    }
}
