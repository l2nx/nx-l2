/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.mail;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.mail.CustomMailRepository;
import app.l2nx.highfive.repository.mail.model.CustomMail;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CustomMailRepositoryTest extends RepositoryTest {
    private final CustomMailRepository repository = new CustomMailRepository(JDBC, registry);

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnQueuedMailWithReceiverHandle() {
            final UUID receiver = insertCharacter();
            final long id = insertCustomMail(receiver, "57 100;6393 1 3");

            assertThat(repository.findAll())
                    .filteredOn(row -> row.id() == id)
                    .containsExactly(CustomMail.builder()
                            .id(id)
                            .receiverId(registry.handleOf(receiver))
                            .subject("Reward")
                            .content("Thanks")
                            .items("57 100;6393 1 3")
                            .build());
        }

        @Test
        void findAll_shouldReadNullItems_whenNoAttachments() {
            final long id = insertCustomMail(insertCharacter(), null);

            final List<CustomMail> actual = repository.findAll();

            assertThat(actual)
                    .filteredOn(row -> row.id() == id)
                    .singleElement()
                    .extracting(CustomMail::items)
                    .isNull();
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveOnlyTheDeliveredMail() {
            final UUID receiver = insertCharacter();
            final long delivered = insertCustomMail(receiver, null);
            final long queued = insertCustomMail(receiver, null);

            repository.delete(delivered);

            assertThat(repository.findAll())
                    .extracting(CustomMail::id)
                    .contains(queued)
                    .doesNotContain(delivered);
        }
    }

    private static long insertCustomMail(UUID receiver, String items) {
        return JDBC.sql("""
            INSERT INTO custom_mails (character_id, subject, content, items)
            VALUES (?, 'Reward', 'Thanks', ?)
            RETURNING id\
            """).param(receiver).param(items).query(Long.class).single();
    }
}
