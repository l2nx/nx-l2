/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.sevensigns;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.sevensigns.SevenSignsParticipantRepository;
import app.l2nx.highfive.repository.sevensigns.model.SevenSignsParticipant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SevenSignsParticipantRepositoryTest extends RepositoryTest {
    private final SevenSignsParticipantRepository repository =
            new SevenSignsParticipantRepository(JDBC, JDBC_TEMPLATE, registry);

    private SevenSignsParticipant participant(UUID characterId, String cabal, int seal) {
        return SevenSignsParticipant.builder()
                .objectId(registry.handleOf(characterId))
                .cabal(cabal)
                .seal(seal)
                .redStones(3)
                .greenStones(2)
                .blueStones(1)
                .ancientAdenaAmount(12_000)
                .contributionScore(450)
                .build();
    }

    private SevenSignsParticipant find(UUID characterId) {
        return repository.findAll().stream()
                .filter(participant -> participant.objectId() == registry.handleOf(characterId))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> columns(UUID characterId) {
        return JDBC.sql("SELECT cabal, seal FROM seven_signs_participants WHERE character_id = ?")
                .param(characterId)
                .query((rs, rowNum) -> Arrays.asList(rs.getString("cabal"), rs.getString("seal")))
                .single();
    }

    @Nested
    class Save {
        @Test
        void save_shouldStoreUpperCaseCabalAndSealName_whenSigningUp() {
            final UUID character = insertCharacter();

            repository.save(participant(character, "dawn", 2));

            assertThat(columns(character)).containsExactly("DAWN", "GNOSIS");
        }

        @Test
        void save_shouldStoreNulls_whenCabalWasReset() {
            final UUID character = insertCharacter();
            repository.save(participant(character, "dusk", 1));

            repository.save(participant(character, null, 0));

            assertThat(columns(character)).containsExactly(null, null);
        }
    }

    @Nested
    class SaveAll {
        @Test
        void saveAll_shouldUpdateEveryParticipant_whenStored() {
            final UUID first = insertCharacter();
            final UUID second = insertCharacter();
            repository.saveAll(List.of(participant(first, "dawn", 1), participant(second, "dusk", 3)));
            final SevenSignsParticipant expected = participant(first, "dawn", 1).toBuilder()
                    .redStones(10)
                    .contributionScore(999)
                    .build();

            repository.saveAll(List.of(expected));

            assertThat(find(first)).isEqualTo(expected);
            assertThat(find(second)).isEqualTo(participant(second, "dusk", 3));
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnNoCabalAndNullSeal_whenColumnsAreNull() {
            final UUID character = insertCharacter();
            JDBC.sql("INSERT INTO seven_signs_participants (character_id) VALUES (?)")
                    .param(character)
                    .update();

            final SevenSignsParticipant actual = find(character);

            assertThat(actual.cabal()).isNull();
            assertThat(actual.seal()).isZero();
        }
    }
}
