/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterRecommendationRepository;
import app.l2nx.highfive.repository.character.model.CharacterRecommendation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ResourceLock("character_recommendations")
class CharacterRecommendationRepositoryTest extends RepositoryTest {
    private final CharacterRecommendationRepository repository = new CharacterRecommendationRepository(JDBC, registry);

    @Nested
    class Find {
        @Test
        void find_shouldBeEmpty_whenNeverStored() {
            assertThat(repository.find(registry.handleOf(insertCharacter()))).isEmpty();
        }
    }

    @Nested
    class Save {
        @Test
        void save_shouldInsertThenReplaceEveryValue() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.save(objectId, recommendation(1, 2, 3));

            repository.save(objectId, recommendation(4, 5, 6));

            assertThat(repository.find(objectId)).get().isEqualTo(recommendation(4, 5, 6));
        }
    }

    @Nested
    class SaveCounts {
        @Test
        void saveCounts_shouldKeepBonusTime_whenRowStored() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.save(objectId, recommendation(1, 2, 3600000));

            repository.saveCounts(objectId, 7, 8);

            assertThat(repository.find(objectId)).get().isEqualTo(recommendation(7, 8, 3600000));
        }

        @Test
        void saveCounts_shouldStartWithoutBonusTime_whenNew() {
            final int objectId = registry.handleOf(insertCharacter());

            repository.saveCounts(objectId, 7, 8);

            assertThat(repository.find(objectId)).get().isEqualTo(recommendation(7, 8, 0));
        }
    }

    @Nested
    class ResetDaily {
        @ParameterizedTest(name = "received {0} -> {1}")
        @CsvSource({"5, 0", "20, 0", "25, 5"})
        void resetDaily_shouldLowerReceivedNotBelowZero(int received, int expected) {
            final int objectId = registry.handleOf(insertCharacter());
            repository.save(objectId, recommendation(received, 0, 0));

            repository.resetDaily(20, 3600000, 20);

            assertThat(repository.find(objectId)).get().isEqualTo(recommendation(expected, 20, 3600000));
        }
    }

    private static CharacterRecommendation recommendation(int received, int leftToGive, long bonusTimeLeftMs) {
        return CharacterRecommendation.builder()
                .received(received)
                .leftToGive(leftToGive)
                .bonusTimeLeftMs(bonusTimeLeftMs)
                .build();
    }
}
