/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.olympiad;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.olympiad.OlympiadNobleRepository;
import app.l2nx.highfive.repository.olympiad.model.OlympiadNoble;
import app.l2nx.highfive.repository.olympiad.model.RankedNoble;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("olympiad_nobles")
class OlympiadNobleRepositoryTest extends RepositoryTest {
    private static final int CLASS_ID = 88;
    private static final int MIN_COMPETITIONS = 9;

    private final OlympiadNobleRepository repository = new OlympiadNobleRepository(JDBC, JDBC_TEMPLATE, registry);

    @BeforeEach
    void clear() {
        JDBC.sql("TRUNCATE olympiad_nobles, olympiad_nobles_eom").update();
    }

    private static void insertSnapshotNoble(UUID characterId, int classId, int points, int done, int won) {
        JDBC.sql("""
            INSERT INTO olympiad_nobles_eom (
                character_id, class_id, olympiad_points, competitions_done, competitions_won, competitions_lost,
                competitions_drawn)
            VALUES (?, ?, ?, ?, ?, 0, 0)\
            """)
                .param(characterId)
                .param(classId)
                .param(points)
                .param(done)
                .param(won)
                .update();
    }

    private static void insertNoble(UUID characterId, int classId, int points, int done, int won) {
        JDBC.sql("""
            INSERT INTO olympiad_nobles (
                character_id, class_id, olympiad_points, competitions_done, competitions_won, competitions_lost,
                competitions_drawn, competitions_done_week, competitions_done_week_classed,
                competitions_done_week_non_classed, competitions_done_week_team)
            VALUES (?, ?, ?, ?, ?, 0, 0, 0, 0, 0, 0)\
            """)
                .param(characterId)
                .param(classId)
                .param(points)
                .param(done)
                .param(won)
                .update();
    }

    private static String nameOf(UUID characterId) {
        return JDBC.sql("SELECT name FROM characters WHERE id = ?")
                .param(characterId)
                .query(String.class)
                .single();
    }

    private OlympiadNoble noble(UUID characterId, int points) {
        return OlympiadNoble.builder()
                .objectId(registry.handleOf(characterId))
                .characterName(nameOf(characterId))
                .classId(CLASS_ID)
                .olympiadPoints(points)
                .competitionsDone(10)
                .competitionsWon(6)
                .competitionsLost(3)
                .competitionsDrawn(1)
                .competitionsDoneWeek(4)
                .competitionsDoneWeekClassed(2)
                .competitionsDoneWeekNonClassed(1)
                .competitionsDoneWeekTeam(1)
                .build();
    }

    @Nested
    class SaveAll {
        @Test
        void saveAll_shouldInsertNoble_whenNew() {
            final OlympiadNoble expected = noble(insertCharacter(), 18);

            repository.saveAll(List.of(expected));

            assertThat(repository.findAll()).containsExactly(expected);
        }

        @Test
        void saveAll_shouldUpdateCountersAndKeepClass_whenStored() {
            final UUID character = insertCharacter();
            repository.saveAll(List.of(noble(character, 18)));
            final OlympiadNoble update = noble(character, 30).toBuilder()
                    .classId(CLASS_ID + 1)
                    .competitionsDone(11)
                    .build();

            repository.saveAll(List.of(update));

            assertThat(repository.findAll())
                    .containsExactly(update.toBuilder().classId(CLASS_ID).build());
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldResolveHandleAndName_whenNobleIsStored() {
            final UUID character = insertCharacter();
            insertNoble(character, CLASS_ID, 12, 3, 2);

            final List<OlympiadNoble> actual = repository.findAll();

            assertThat(actual).singleElement().satisfies(noble -> {
                assertThat(noble.objectId()).isEqualTo(registry.handleOf(character));
                assertThat(noble.characterName()).isEqualTo(nameOf(character));
                assertThat(noble.olympiadPoints()).isEqualTo(12);
            });
        }
    }

    @Nested
    class FindHeroCandidate {
        @Test
        void findHeroCandidate_shouldReturnBestNobleWithWins_whenClassHasCandidates() {
            final UUID best = insertCharacter();
            final UUID noWins = insertCharacter();
            final UUID tooFewFights = insertCharacter();
            final UUID second = insertCharacter();
            insertNoble(best, CLASS_ID, 50, 12, 8);
            insertNoble(noWins, CLASS_ID, 90, 12, 0);
            insertNoble(tooFewFights, CLASS_ID, 99, 5, 5);
            insertNoble(second, CLASS_ID, 50, 10, 8);

            assertThat(repository.findHeroCandidate(CLASS_ID, MIN_COMPETITIONS))
                    .contains(new RankedNoble(registry.handleOf(best), nameOf(best)));
        }

        @Test
        void findHeroCandidate_shouldReturnEmpty_whenNoNobleQualifies() {
            insertNoble(insertCharacter(), CLASS_ID, 50, 2, 1);

            assertThat(repository.findHeroCandidate(CLASS_ID, MIN_COMPETITIONS)).isEmpty();
        }
    }

    @Nested
    class FindLeaderNames {
        @Test
        void findLeaderNames_shouldReturnBestNamesOfClasses_whenLimited() {
            final UUID first = insertCharacter();
            final UUID second = insertCharacter();
            final UUID third = insertCharacter();
            insertNoble(first, 132, 40, 10, 5);
            insertNoble(second, 133, 30, 10, 5);
            insertNoble(third, 132, 20, 10, 5);
            insertNoble(insertCharacter(), 90, 99, 10, 5);

            assertThat(repository.findLeaderNames(List.of(132, 133), MIN_COMPETITIONS, 2))
                    .containsExactly(nameOf(first), nameOf(second));
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldRemoveEveryNoble_whenCalled() {
            insertNoble(insertCharacter(), CLASS_ID, 10, 1, 1);

            repository.deleteAll();

            assertThat(repository.findAll()).isEmpty();
        }
    }

    @Nested
    class ReplaceSnapshot {
        @Test
        void replaceSnapshot_shouldCopyCurrentNobles_whenSnapshotHasOldRows() {
            final UUID current = insertCharacter();
            insertNoble(current, CLASS_ID, 25, 10, 5);
            insertSnapshotNoble(insertCharacter(), CLASS_ID, 70, 10, 5);

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceSnapshot());

            assertThat(JDBC.sql("SELECT character_id FROM olympiad_nobles_eom")
                            .query(UUID.class)
                            .list())
                    .containsExactly(current);
            assertThat(repository.findSnapshotPoints(registry.handleOf(current)))
                    .hasValue(25);
        }
    }

    @Nested
    class FindSnapshotRanking {
        @Test
        void findSnapshotRanking_shouldOrderQualifiedNobles_whenSnapshotExists() {
            final UUID first = insertCharacter();
            final UUID second = insertCharacter();
            insertSnapshotNoble(second, CLASS_ID, 30, 10, 5);
            insertSnapshotNoble(first, CLASS_ID, 30, 11, 5);
            insertSnapshotNoble(insertCharacter(), CLASS_ID, 90, 2, 2);

            assertThat(repository.findSnapshotRanking(MIN_COMPETITIONS))
                    .containsExactly(registry.handleOf(first), registry.handleOf(second));
        }
    }

    @Nested
    class FindSnapshotLeaderNames {
        @Test
        void findSnapshotLeaderNames_shouldReadSnapshot_whenCurrentNoblesDiffer() {
            final UUID snapshotLeader = insertCharacter();
            insertSnapshotNoble(snapshotLeader, CLASS_ID, 30, 10, 5);
            insertNoble(insertCharacter(), CLASS_ID, 90, 10, 5);

            assertThat(repository.findSnapshotLeaderNames(List.of(CLASS_ID), MIN_COMPETITIONS, 10))
                    .containsExactly(nameOf(snapshotLeader));
        }
    }

    @Nested
    class FindSnapshotPoints {
        @Test
        void findSnapshotPoints_shouldReturnPoints_whenCharacterIsInSnapshot() {
            final UUID character = insertCharacter();
            insertSnapshotNoble(character, CLASS_ID, 33, 10, 5);

            assertThat(repository.findSnapshotPoints(registry.handleOf(character)))
                    .hasValue(33);
        }

        @Test
        void findSnapshotPoints_shouldReturnEmpty_whenCharacterIsNotInSnapshot() {
            assertThat(repository.findSnapshotPoints(registry.handleOf(insertCharacter())))
                    .isEmpty();
        }

        @Test
        void findSnapshotPoints_shouldReturnEmpty_whenHandleIsUnknown() {
            assertThat(repository.findSnapshotPoints(registry.allocateTransient()))
                    .isEmpty();
        }
    }
}
