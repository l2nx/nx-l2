/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.sevensigns;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.sevensigns.SevenSignsFestivalScoreRepository;
import app.l2nx.highfive.repository.sevensigns.model.SevenSignsFestivalScore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SevenSignsFestivalScoreRepositoryTest extends RepositoryTest {
    private final SevenSignsFestivalScoreRepository repository =
            new SevenSignsFestivalScoreRepository(JDBC, JDBC_TEMPLATE, registry);

    private List<SevenSignsFestivalScore> findCycle(int cycle) {
        return repository.findAll().stream()
                .filter(score -> score.cycle() == cycle)
                .toList();
    }

    @Nested
    class SaveAll {
        @Test
        void saveAll_shouldStoreMemberUuidsAndNullDate_whenPlaceholderAndScore() {
            final UUID member = insertCharacter();
            final SevenSignsFestivalScore placeholder = SevenSignsFestivalScore.builder()
                    .cycle(101)
                    .festivalId(0)
                    .cabal("dusk")
                    .memberIds(List.of())
                    .build();
            final SevenSignsFestivalScore scored = SevenSignsFestivalScore.builder()
                    .cycle(101)
                    .festivalId(0)
                    .cabal("dawn")
                    .scoredAt(NOW)
                    .score(120)
                    .memberIds(List.of(registry.handleOf(member)))
                    .build();

            repository.saveAll(List.of(placeholder, scored));

            assertThat(JDBC.sql("""
                SELECT member_ids[1] FROM seven_signs_festival_scores WHERE cycle = 101 AND cabal = 'DAWN'\
                """).query(UUID.class).single()).isEqualTo(member);
            assertThat(JDBC.sql("""
                SELECT scored_at IS NULL AND cardinality(member_ids) = 0
                FROM seven_signs_festival_scores
                WHERE cycle = 101 AND cabal = 'DUSK'\
                """).query(Boolean.class).single()).isTrue();
        }

        @Test
        void saveAll_shouldReplaceScore_whenFestivalAlreadyStored() {
            final UUID first = insertCharacter();
            final UUID second = insertCharacter();
            final SevenSignsFestivalScore initial = SevenSignsFestivalScore.builder()
                    .cycle(102)
                    .festivalId(3)
                    .cabal("dusk")
                    .memberIds(List.of())
                    .build();
            repository.saveAll(List.of(initial));
            final SevenSignsFestivalScore expected = initial.toBuilder()
                    .scoredAt(NOW)
                    .score(80)
                    .memberIds(List.of(registry.handleOf(first), registry.handleOf(second)))
                    .build();

            repository.saveAll(List.of(expected));

            assertThat(findCycle(102)).containsExactly(expected);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnShortCabalZeroDateAndHandles_whenStored() {
            final UUID member = insertCharacter();
            JDBC.sql("""
                INSERT INTO seven_signs_festival_scores (cycle, festival_id, cabal, score, member_ids)
                VALUES (103, 4, 'DAWN', 0, ARRAY[?]::uuid[])\
                """).param(member).update();

            assertThat(findCycle(103))
                    .containsExactly(SevenSignsFestivalScore.builder()
                            .cycle(103)
                            .festivalId(4)
                            .cabal("dawn")
                            .memberIds(List.of(registry.handleOf(member)))
                            .build());
        }
    }
}
