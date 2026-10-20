/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.pet;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.pet.SummonSkillSaveRepository;
import app.l2nx.highfive.repository.pet.model.SummonEffectRow;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SummonSkillSaveRepositoryTest extends RepositoryTest {
    private static final int SUMMON_CAT = 1111;
    private static final SummonEffectRow HASTE = effect(1086, 2, 60_000L);
    private static final SummonEffectRow TOGGLE = effect(1004, 1, null);

    private final SummonSkillSaveRepository repository = new SummonSkillSaveRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class ReplaceAll {
        @Test
        void replaceAll_shouldKeepListOrderPerClassIndex() {
            final int owner = registry.handleOf(insertCharacter());
            repository.replaceAll(owner, 1, SUMMON_CAT, List.of(HASTE));

            TRANSACTIONS.executeWithoutResult(
                    status -> repository.replaceAll(owner, 0, SUMMON_CAT, List.of(TOGGLE, HASTE)));

            assertThat(repository.findAll(owner, 0, SUMMON_CAT)).containsExactly(TOGGLE, HASTE);
            assertThat(repository.findAll(owner, 1, SUMMON_CAT)).containsExactly(HASTE);
        }

        @Test
        void replaceAll_shouldKeepLastDuplicate() {
            final int owner = registry.handleOf(insertCharacter());
            final SummonEffectRow shorter =
                    HASTE.toBuilder().remainingMillis(1000L).build();

            repository.replaceAll(owner, 0, SUMMON_CAT, List.of(HASTE, shorter));

            assertThat(repository.findAll(owner, 0, SUMMON_CAT)).containsExactly(shorter);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldBeEmpty_whenOtherSummonSkill() {
            final int owner = registry.handleOf(insertCharacter());
            repository.replaceAll(owner, 0, SUMMON_CAT, List.of(HASTE));

            assertThat(repository.findAll(owner, 0, 1332)).isEmpty();
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldRemoveEffectsOfTheServitor() {
            final int owner = registry.handleOf(insertCharacter());
            repository.replaceAll(owner, 0, SUMMON_CAT, List.of(HASTE, TOGGLE));

            repository.deleteAll(owner, 0, SUMMON_CAT);

            assertThat(repository.findAll(owner, 0, SUMMON_CAT)).isEmpty();
        }
    }

    private static SummonEffectRow effect(int skillId, int skillLevel, Long remainingMillis) {
        return SummonEffectRow.builder()
                .skillId(skillId)
                .skillLevel(skillLevel)
                .remainingMillis(remainingMillis)
                .tickPhaseMillis(0)
                .build();
    }
}
