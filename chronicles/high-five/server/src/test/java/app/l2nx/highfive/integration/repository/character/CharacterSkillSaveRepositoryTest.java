/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterSkillSaveRepository;
import app.l2nx.highfive.repository.character.model.CharacterSkillSave;
import app.l2nx.highfive.repository.character.model.OnlineStatus;
import app.l2nx.highfive.repository.character.model.SkillRestoreType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("character_skill_saves")
@ResourceLock("characters.online_status")
class CharacterSkillSaveRepositoryTest extends RepositoryTest {
    private static final CharacterSkillSave EFFECT = CharacterSkillSave.builder()
            .skillId(1204)
            .skillLevel(2)
            .restoreType(SkillRestoreType.EFFECT)
            .remainingMs(60_000L)
            .tickPhaseMs(500L)
            .reuseDelayMs(0)
            .build();
    private static final CharacterSkillSave TOGGLE = CharacterSkillSave.builder()
            .skillId(312)
            .skillLevel(1)
            .restoreType(SkillRestoreType.EFFECT)
            .tickPhaseMs(0L)
            .reuseDelayMs(4000)
            .reuseEndsAt(NOW.plusSeconds(4))
            .build();
    private static final CharacterSkillSave REUSE = CharacterSkillSave.builder()
            .skillId(121)
            .skillLevel(3)
            .restoreType(SkillRestoreType.REUSE)
            .reuseDelayMs(300_000)
            .reuseEndsAt(NOW.plusSeconds(200))
            .build();

    private final CharacterSkillSaveRepository repository =
            new CharacterSkillSaveRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnSavesInSaveOrder_whenReplaced() {
            final int objectId = registry.handleOf(insertCharacter());
            TRANSACTIONS.executeWithoutResult(
                    status -> repository.replaceAll(objectId, 0, List.of(REUSE, TOGGLE, EFFECT)));

            assertThat(repository.findAll(objectId, 0)).containsExactly(REUSE, TOGGLE, EFFECT);
        }

        @Test
        void findAll_shouldBeEmpty_forAnotherClassIndex() {
            final int objectId = registry.handleOf(insertCharacter());
            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(objectId, 0, List.of(EFFECT)));

            assertThat(repository.findAll(objectId, 1)).isEmpty();
        }
    }

    @Nested
    class ReplaceAll {
        @Test
        void replaceAll_shouldDropPreviousSaves() {
            final int objectId = registry.handleOf(insertCharacter());
            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(objectId, 0, List.of(EFFECT, REUSE)));

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(objectId, 0, List.of(TOGGLE)));

            assertThat(repository.findAll(objectId, 0)).containsExactly(TOGGLE);
        }

        @Test
        void replaceAll_shouldStoreNullTimingsOfReuseRow() {
            final UUID id = insertCharacter();
            TRANSACTIONS.executeWithoutResult(
                    status -> repository.replaceAll(registry.handleOf(id), 0, List.of(REUSE)));

            final var actual = JDBC.sql(
                            "SELECT restore_type, remaining_ms, tick_phase_ms, buff_order FROM character_skill_saves"
                                    + " WHERE character_id = ?")
                    .param(id)
                    .query()
                    .singleRow();

            assertThat(actual)
                    .containsEntry("restore_type", "REUSE")
                    .containsEntry("remaining_ms", null)
                    .containsEntry("tick_phase_ms", null)
                    .containsEntry("buff_order", 1);
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldRemoveSavesOfTheClassIndex() {
            final int objectId = registry.handleOf(insertCharacter());
            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(objectId, 0, List.of(EFFECT)));
            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(objectId, 1, List.of(REUSE)));

            repository.deleteAll(objectId, 0);

            assertThat(repository.findAll(objectId, 0)).isEmpty();
            assertThat(repository.findAll(objectId, 1)).containsExactly(REUSE);
        }
    }

    @Nested
    class DeleteSkillOfOfflineCharacters {
        @Test
        void deleteSkillOfOfflineCharacters_shouldKeepSavesOfOnlineCharacters() {
            final UUID offline = insertCharacter();
            final UUID online = insertCharacter();
            JDBC.sql("UPDATE characters SET online_status = ? WHERE id = ?")
                    .param(OnlineStatus.ONLINE.name())
                    .param(online)
                    .update();
            for (UUID id : List.of(offline, online)) {
                TRANSACTIONS.executeWithoutResult(
                        status -> repository.replaceAll(registry.handleOf(id), 0, List.of(EFFECT, REUSE)));
            }

            repository.deleteSkillOfOfflineCharacters(REUSE.skillId());

            assertThat(repository.findAll(registry.handleOf(offline), 0)).containsExactly(EFFECT);
            assertThat(repository.findAll(registry.handleOf(online), 0)).containsExactly(EFFECT, REUSE);
        }

        @Test
        void deleteSkillOfOfflineCharacters_shouldSpareTheExceptedCharacters() {
            final UUID spared = insertCharacter();
            final UUID offline = insertCharacter();
            for (UUID id : List.of(spared, offline)) {
                TRANSACTIONS.executeWithoutResult(
                        status -> repository.replaceAll(registry.handleOf(id), 0, List.of(EFFECT, REUSE)));
            }

            repository.deleteSkillOfOfflineCharacters(REUSE.skillId(), Set.of(spared));

            assertThat(repository.findAll(registry.handleOf(spared), 0)).containsExactly(EFFECT, REUSE);
            assertThat(repository.findAll(registry.handleOf(offline), 0)).containsExactly(EFFECT);
        }
    }

    @Nested
    class DeleteSkill {
        @Test
        void deleteSkill_shouldDeleteTheSkillOfOneCharacter() {
            final UUID character = insertCharacter();
            final UUID other = insertCharacter();
            for (UUID id : List.of(character, other)) {
                TRANSACTIONS.executeWithoutResult(
                        status -> repository.replaceAll(registry.handleOf(id), 0, List.of(EFFECT, REUSE)));
            }

            repository.deleteSkill(character, REUSE.skillId());

            assertThat(repository.findAll(registry.handleOf(character), 0)).containsExactly(EFFECT);
            assertThat(repository.findAll(registry.handleOf(other), 0)).containsExactly(EFFECT, REUSE);
        }
    }

    @Nested
    class DeleteExpiredReuse {
        @Test
        void deleteExpiredReuse_shouldRemoveOnlyEndedReuseRows() {
            final int objectId = registry.handleOf(insertCharacter());
            final CharacterSkillSave ended = REUSE.toBuilder()
                    .skillId(122)
                    .reuseEndsAt(NOW.minusSeconds(1))
                    .build();
            final CharacterSkillSave endedEffect =
                    TOGGLE.toBuilder().reuseEndsAt(NOW.minusSeconds(1)).build();
            TRANSACTIONS.executeWithoutResult(
                    status -> repository.replaceAll(objectId, 0, List.of(ended, REUSE, endedEffect)));

            repository.deleteExpiredReuse(NOW);

            assertThat(repository.findAll(objectId, 0)).containsExactly(REUSE, endedEffect);
        }
    }
}
