/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.pet;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.pet.PetSkillSaveRepository;
import app.l2nx.highfive.repository.pet.model.SummonEffectRow;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PetSkillSaveRepositoryTest extends RepositoryTest {
    private static final SummonEffectRow HASTE = effect(1086, 2, 60_000L);
    private static final SummonEffectRow TOGGLE = effect(1004, 1, null);

    private final PetSkillSaveRepository repository = new PetSkillSaveRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class ReplaceAll {
        @Test
        void replaceAll_shouldKeepListOrderAndNullDuration() {
            final int item = newControlItem();
            repository.replaceAll(item, List.of(HASTE));

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(item, List.of(TOGGLE, HASTE)));

            assertThat(repository.findAll(item)).containsExactly(TOGGLE, HASTE);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldBeEmpty_whenNothingStored() {
            assertThat(repository.findAll(newControlItem())).isEmpty();
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldRemoveEffects() {
            final int item = newControlItem();
            repository.replaceAll(item, List.of(HASTE, TOGGLE));

            repository.deleteAll(item);

            assertThat(repository.findAll(item)).isEmpty();
        }
    }

    private int newControlItem() {
        return registry.handleOf(insertItem(insertCharacter(), 2375, 1));
    }

    private static SummonEffectRow effect(int skillId, int skillLevel, Long remainingMillis) {
        return SummonEffectRow.builder()
                .skillId(skillId)
                .skillLevel(skillLevel)
                .remainingMillis(remainingMillis)
                .tickPhaseMillis(1500)
                .build();
    }
}
