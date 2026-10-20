/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.world;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.world.FishingChampionshipRepository;
import app.l2nx.highfive.repository.world.model.FishingChampionshipEntry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("fishing_championship_entries")
class FishingChampionshipRepositoryTest extends RepositoryTest {
    private final FishingChampionshipRepository repository =
            new FishingChampionshipRepository(JDBC, JDBC_TEMPLATE, registry);

    private static String nameOf(UUID characterId) {
        return JDBC.sql("SELECT name FROM characters WHERE id = ?")
                .param(characterId)
                .query(String.class)
                .single();
    }

    private FishingChampionshipEntry entry(UUID characterId, double length, int rewardType) {
        return FishingChampionshipEntry.builder()
                .objectId(registry.handleOf(characterId))
                .characterName(nameOf(characterId))
                .fishLength(length)
                .rewardType(rewardType)
                .build();
    }

    @Nested
    class ReplaceAll {
        @Test
        void replaceAll_shouldSplitRewardTypeIntoFlags_whenStored() {
            final UUID entrant = insertCharacter();
            final UUID unclaimed = insertCharacter();
            final UUID rewarded = insertCharacter();

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(
                    List.of(entry(entrant, 12.5, 0), entry(unclaimed, 40.25, 1), entry(rewarded, 38.0, 2))));

            assertThat(JDBC.sql("""
                SELECT count(*) FROM fishing_championship_entries
                WHERE (character_id = ? AND NOT winner AND NOT rewarded)
                   OR (character_id = ? AND winner AND NOT rewarded)
                   OR (character_id = ? AND winner AND rewarded)\
                """)
                            .param(entrant)
                            .param(unclaimed)
                            .param(rewarded)
                            .query(Integer.class)
                            .single())
                    .isEqualTo(3);
        }

        @Test
        void replaceAll_shouldDropPreviousEntries_whenCalledAgain() {
            final UUID character = insertCharacter();
            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(List.of(entry(character, 10, 0))));

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(List.of()));

            assertThat(repository.findAll()).isEmpty();
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnLegacyRewardTypes_whenCharacterIsWinnerAndEntrant() {
            final UUID character = insertCharacter();
            final List<FishingChampionshipEntry> expected = List.of(entry(character, 20, 0), entry(character, 30, 2));
            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(expected));

            assertThat(repository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
        }
    }
}
