/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.world;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.world.PetitionFeedbackRepository;
import app.l2nx.highfive.repository.world.model.PetitionFeedback;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PetitionFeedbackRepositoryTest extends RepositoryTest {
    private final PetitionFeedbackRepository repository = new PetitionFeedbackRepository(JDBC, registry);

    private static List<Object> stored(UUID characterId) {
        return JDBC.sql("SELECT gm_character_id, rating, message FROM petition_feedbacks WHERE character_id = ?")
                .param(characterId)
                .query((rs, rowNum) -> Arrays.<Object>asList(
                        rs.getObject("gm_character_id", UUID.class), rs.getInt("rating"), rs.getString("message")))
                .single();
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreCharacterAndGmUuids_whenRated() {
            final UUID character = insertCharacter();
            final UUID gm = insertCharacter();

            repository.insert(PetitionFeedback.builder()
                    .objectId(registry.handleOf(character))
                    .gmObjectId(registry.handleOf(gm))
                    .rating(4)
                    .message("Thanks")
                    .createdAt(NOW)
                    .build());

            assertThat(stored(character)).containsExactly(gm, 4, "Thanks");
        }

        @Test
        void insert_shouldStoreNullMessage_whenMessageIsBlank() {
            final UUID character = insertCharacter();
            final UUID gm = insertCharacter();

            repository.insert(PetitionFeedback.builder()
                    .objectId(registry.handleOf(character))
                    .gmObjectId(registry.handleOf(gm))
                    .rating(0)
                    .message("")
                    .createdAt(NOW)
                    .build());

            assertThat(stored(character)).containsExactly(gm, 0, null);
        }
    }
}
