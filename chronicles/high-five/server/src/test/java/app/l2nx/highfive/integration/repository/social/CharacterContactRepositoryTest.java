/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.social;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.social.CharacterContactRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterContactRepositoryTest extends RepositoryTest {
    private final CharacterContactRepository repository = new CharacterContactRepository(JDBC, registry);

    @Nested
    class FindContactIds {
        @Test
        void findContactIds_shouldReturnContactHandles() {
            final UUID character = insertCharacter();
            final UUID contact = insertCharacter();
            insertContact(character, contact);
            insertContact(insertCharacter(), character);

            final List<Integer> actual = repository.findContactIds(registry.handleOf(character));

            assertThat(actual).containsExactly(registry.handleOf(contact));
        }
    }

    @Nested
    class FindContactNames {
        @Test
        void findContactNames_shouldGiveTheNamesOfTheContacts() {
            final UUID character = insertCharacter();
            final UUID first = insertCharacter();
            final UUID second = insertCharacter();
            JDBC.sql("UPDATE characters SET name = 'First' WHERE id = ?")
                    .param(first)
                    .update();
            JDBC.sql("UPDATE characters SET name = 'Second' WHERE id = ?")
                    .param(second)
                    .update();
            insertContact(character, first);
            insertContact(character, second);
            insertContact(insertCharacter(), character);
            final int allocatedBefore = registry.allocatedCount();

            final List<String> actual = repository.findContactNames(character);

            assertThat(actual).containsExactlyInAnyOrder("First", "Second");
            assertThat(registry.allocatedCount()).isEqualTo(allocatedBefore);
        }
    }

    @Nested
    class Add {
        @Test
        void add_shouldStoreContactUuid() {
            final UUID character = insertCharacter();
            final UUID contact = insertCharacter();

            repository.add(registry.handleOf(character), registry.handleOf(contact));

            assertThat(contacts(character)).containsExactly(contact);
        }

        @Test
        void add_shouldKeepOneRow_whenContactAlreadyStored() {
            final UUID character = insertCharacter();
            final UUID contact = insertCharacter();
            insertContact(character, contact);

            repository.add(registry.handleOf(character), registry.handleOf(contact));

            assertThat(contacts(character)).containsExactly(contact);
        }
    }

    @Nested
    class Remove {
        @Test
        void remove_shouldDeleteOnlyThatContact() {
            final UUID character = insertCharacter();
            final UUID removed = insertCharacter();
            final UUID kept = insertCharacter();
            insertContact(character, removed);
            insertContact(character, kept);

            repository.remove(registry.handleOf(character), registry.handleOf(removed));

            assertThat(contacts(character)).containsExactly(kept);
        }
    }

    private static void insertContact(UUID character, UUID contact) {
        JDBC.sql("INSERT INTO character_contacts (character_id, contact_character_id) VALUES (?, ?)")
                .param(character)
                .param(contact)
                .update();
    }

    private static List<UUID> contacts(UUID character) {
        return JDBC.sql("SELECT contact_character_id FROM character_contacts WHERE character_id = ?")
                .param(character)
                .query(UUID.class)
                .list();
    }
}
