/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.pet;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.pet.PetRepository;
import app.l2nx.highfive.repository.pet.model.PetRow;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PetRepositoryTest extends RepositoryTest {
    private static final AtomicInteger TEMPLATES = new AtomicInteger(200_000);

    private final PetRepository repository = new PetRepository(JDBC, registry);

    @Nested
    class Save {
        @Test
        void save_shouldInsertThenUpdatePet() {
            final PetRow pet = pet(insertCharacter(), TEMPLATES.incrementAndGet());
            repository.save(pet);
            final PetRow updated = pet.toBuilder()
                    .level(12)
                    .exp(5000)
                    .name("Rex")
                    .restore(false)
                    .build();

            repository.save(updated);

            assertThat(repository.find(pet.controlItemId())).contains(updated);
        }

        @Test
        void save_shouldStoreNoName_whenEmpty() {
            final PetRow pet = pet(insertCharacter(), TEMPLATES.incrementAndGet()).toBuilder()
                    .name("")
                    .build();

            repository.save(pet);

            assertThat(repository.find(pet.controlItemId()))
                    .get()
                    .extracting(PetRow::name)
                    .isNull();
        }
    }

    @Nested
    class Find {
        @Test
        void find_shouldBeEmpty_whenPetNeverStored() {
            final int item = registry.handleOf(insertItem(insertCharacter(), 2375, 1));

            assertThat(repository.find(item)).isEmpty();
        }
    }

    @Nested
    class FindRestorable {
        @Test
        void findRestorable_shouldReturnControlItemByOwner_whenRestoreOnLogin() {
            final UUID owner = insertCharacter();
            final PetRow restorable = pet(owner, TEMPLATES.incrementAndGet());
            final PetRow resting = pet(insertCharacter(), TEMPLATES.incrementAndGet()).toBuilder()
                    .restore(false)
                    .build();
            repository.save(restorable);
            repository.save(resting);

            assertThat(repository.findRestorable())
                    .containsEntry(restorable.ownerId(), restorable.controlItemId())
                    .doesNotContainKey(resting.ownerId());
        }
    }

    @Nested
    class NameExists {
        @Test
        void nameExists_shouldMatchIgnoringCase_whenSameControlItemTemplate() {
            final int template = TEMPLATES.incrementAndGet();
            repository.save(
                    pet(insertCharacter(), template).toBuilder().name("Fluffy").build());

            assertThat(repository.nameExists("FLUFFY", template)).isTrue();
        }

        @Test
        void nameExists_shouldBeFalse_whenOtherControlItemTemplate() {
            final int template = TEMPLATES.incrementAndGet();
            repository.save(
                    pet(insertCharacter(), template).toBuilder().name("Spot").build());

            assertThat(repository.nameExists("Spot", TEMPLATES.incrementAndGet()))
                    .isFalse();
        }
    }

    @Nested
    class UpdateFed {
        @Test
        void updateFed_shouldStoreFeed() {
            final PetRow pet = pet(insertCharacter(), TEMPLATES.incrementAndGet());
            repository.save(pet);

            repository.updateFed(pet.controlItemId(), 7);

            assertThat(repository.find(pet.controlItemId()))
                    .get()
                    .extracting(PetRow::fed)
                    .isEqualTo(7);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemovePet() {
            final PetRow pet = pet(insertCharacter(), TEMPLATES.incrementAndGet());
            repository.save(pet);

            repository.delete(pet.controlItemId());

            assertThat(repository.find(pet.controlItemId())).isEmpty();
        }
    }

    private PetRow pet(UUID owner, int controlItemTemplate) {
        return PetRow.builder()
                .controlItemId(registry.handleOf(insertItem(owner, controlItemTemplate, 1)))
                .ownerId(registry.handleOf(owner))
                .name(null)
                .level(1)
                .currentHp(100)
                .currentMp(50)
                .exp(0)
                .sp(0)
                .fed(30)
                .restore(true)
                .build();
    }
}
