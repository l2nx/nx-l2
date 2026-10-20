/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.world;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.world.CoupleRepository;
import app.l2nx.highfive.repository.world.model.CoupleRow;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CoupleRepositoryTest extends RepositoryTest {
    private final CoupleRepository repository = new CoupleRepository(JDBC, registry);

    private CoupleRow engaged(long id, int first, int second) {
        return CoupleRow.builder()
                .id(id)
                .firstObjectId(first)
                .secondObjectId(second)
                .engagedAt(NOW)
                .build();
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreEngagedCouple_whenCreated() {
            final int first = registry.handleOf(insertCharacter());
            final int second = registry.handleOf(insertCharacter());

            final long id = repository.insert(first, second, NOW);

            assertThat(repository.findById(id)).contains(engaged(id, first, second));
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldContainEveryCouple_whenStored() {
            final int first = registry.handleOf(insertCharacter());
            final int second = registry.handleOf(insertCharacter());
            final long id = repository.insert(first, second, NOW);

            assertThat(repository.findAll()).contains(engaged(id, first, second));
        }
    }

    @Nested
    class FindById {
        @Test
        void findById_shouldReturnEmpty_whenCoupleIsMissing() {
            assertThat(repository.findById(-1)).isEmpty();
        }
    }

    @Nested
    class UpdateMarried {
        @Test
        void updateMarried_shouldSetMarriedAt_whenCoupleMarries() {
            final int first = registry.handleOf(insertCharacter());
            final int second = registry.handleOf(insertCharacter());
            final long id = repository.insert(first, second, NOW);
            final Instant marriedAt = NOW.plusSeconds(3600);

            repository.updateMarried(id, marriedAt);

            assertThat(repository.findById(id))
                    .contains(engaged(id, first, second).toBuilder()
                            .marriedAt(marriedAt)
                            .build());
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveCouple_whenDivorced() {
            final long id =
                    repository.insert(registry.handleOf(insertCharacter()), registry.handleOf(insertCharacter()), NOW);

            repository.delete(id);

            assertThat(repository.findById(id)).isEmpty();
        }
    }
}
