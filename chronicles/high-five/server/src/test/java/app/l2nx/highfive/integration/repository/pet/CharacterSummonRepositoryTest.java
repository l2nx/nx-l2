/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.pet;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.pet.CharacterSummonRepository;
import app.l2nx.highfive.repository.pet.model.CharacterSummon;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterSummonRepositoryTest extends RepositoryTest {
    private static final int SUMMON_CAT = 1111;
    private static final int SUMMON_UNICORN = 1332;

    private final CharacterSummonRepository repository = new CharacterSummonRepository(JDBC, registry);

    @Nested
    class Save {
        @Test
        void save_shouldInsertThenReplaceServitor() {
            final CharacterSummon summon = summon(SUMMON_CAT);
            repository.save(summon);
            final CharacterSummon updated =
                    summon.toBuilder().currentHp(10).lifeRemainingMs(500).build();

            repository.save(updated);

            assertThat(repository.find(summon.ownerId(), SUMMON_CAT)).contains(updated);
        }
    }

    @Nested
    class Find {
        @Test
        void find_shouldBeEmpty_whenOtherSkill() {
            final CharacterSummon summon = summon(SUMMON_CAT);
            repository.save(summon);

            assertThat(repository.find(summon.ownerId(), SUMMON_UNICORN)).isEmpty();
        }
    }

    @Nested
    class FindSummonSkills {
        @Test
        void findSummonSkills_shouldReturnSkillByOwner() {
            final CharacterSummon summon = summon(SUMMON_UNICORN);
            repository.save(summon);

            assertThat(repository.findSummonSkills()).containsEntry(summon.ownerId(), SUMMON_UNICORN);
        }
    }

    @Nested
    class DeleteByOwner {
        @Test
        void deleteByOwner_shouldRemoveOwnersServitors() {
            final CharacterSummon summon = summon(SUMMON_CAT);
            final CharacterSummon other = summon(SUMMON_CAT);
            repository.save(summon);
            repository.save(other);

            repository.deleteByOwner(summon.ownerId());

            assertThat(repository.find(summon.ownerId(), SUMMON_CAT)).isEmpty();
            assertThat(repository.find(other.ownerId(), SUMMON_CAT)).contains(other);
        }
    }

    private CharacterSummon summon(int skillId) {
        return CharacterSummon.builder()
                .ownerId(registry.handleOf(insertCharacter()))
                .summonSkillId(skillId)
                .currentHp(300)
                .currentMp(120)
                .lifeRemainingMs(3_600_000)
                .build();
    }
}
