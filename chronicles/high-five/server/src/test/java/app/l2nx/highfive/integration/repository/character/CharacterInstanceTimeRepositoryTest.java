/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterInstanceTimeRepository;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("character_instance_times")
class CharacterInstanceTimeRepositoryTest extends RepositoryTest {
    private static final Instant REENTER_AT = NOW.plusSeconds(3600);

    private final CharacterInstanceTimeRepository repository = new CharacterInstanceTimeRepository(JDBC, registry);

    @Nested
    class Save {
        @Test
        void save_shouldInsertThenReplaceTime() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.save(objectId, 80, NOW);

            repository.save(objectId, 80, REENTER_AT);

            assertThat(repository.findAll(objectId)).containsExactly(entry(80, REENTER_AT));
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldBeEmpty_whenNoTimes() {
            assertThat(repository.findAll(registry.handleOf(insertCharacter()))).isEmpty();
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveTheInstanceTemplateTime() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.save(objectId, 80, REENTER_AT);
            repository.save(objectId, 81, REENTER_AT);

            repository.delete(objectId, 80);

            assertThat(repository.findAll(objectId)).containsExactly(entry(81, REENTER_AT));
        }
    }

    @Nested
    class DeleteExpired {
        @Test
        void deleteExpired_shouldRemoveTimesNotInTheFuture() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.save(objectId, 80, NOW);
            repository.save(objectId, 81, REENTER_AT);

            repository.deleteExpired(NOW);

            assertThat(repository.findAll(objectId)).containsExactly(entry(81, REENTER_AT));
        }
    }
}
