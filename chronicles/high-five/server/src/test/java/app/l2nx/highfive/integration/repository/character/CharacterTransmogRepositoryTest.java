/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterTransmogRepository;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterTransmogRepositoryTest extends RepositoryTest {
    private final CharacterTransmogRepository repository =
            new CharacterTransmogRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class FindByCharacter {
        @Test
        void findByCharacter_shouldReturnTemplateIdsAscending() {
            final UUID character = insertCharacter();
            insertTransmog(character, null, 6380);
            insertTransmog(character, null, 2407);
            insertTransmog(insertCharacter(), null, 1);

            assertThat(repository.findByCharacter(registry.handleOf(character))).containsExactly(2407, 6380);
        }
    }

    @Nested
    class FindByAccount {
        @Test
        void findByAccount_shouldReturnTemplateIdsAscending() {
            final UUID accountId = EntityIds.next();
            insertTransmog(null, accountId, 6380);
            insertTransmog(null, accountId, 2407);
            insertTransmog(null, EntityIds.next(), 1);

            assertThat(repository.findByAccount(accountId)).containsExactly(2407, 6380);
        }
    }

    @Nested
    class AddForCharacter {
        @Test
        void addForCharacter_shouldStoreNewUnlocks_andSkipKnownOnes() {
            final UUID character = insertCharacter();
            insertTransmog(character, null, 2407);

            repository.addForCharacter(registry.handleOf(character), List.of(2407, 6380));

            assertThat(transmogs())
                    .filteredOn(row -> character.equals(row.toList().get(0)))
                    .containsExactlyInAnyOrder(tuple(character, null, 2407), tuple(character, null, 6380));
        }
    }

    @Nested
    class AddForAccount {
        @Test
        void addForAccount_shouldStoreNewUnlocks_andSkipKnownOnes() {
            final UUID accountId = EntityIds.next();
            insertTransmog(null, accountId, 2407);

            repository.addForAccount(accountId, List.of(2407, 6380));

            assertThat(transmogs())
                    .filteredOn(row -> accountId.equals(row.toList().get(1)))
                    .containsExactlyInAnyOrder(tuple(null, accountId, 2407), tuple(null, accountId, 6380));
        }

        @Test
        void addForAccount_shouldDoNothing_whenNoUnlocks() {
            final UUID accountId = EntityIds.next();

            repository.addForAccount(accountId, List.of());

            assertThat(repository.findByAccount(accountId)).isEmpty();
        }
    }

    private static void insertTransmog(UUID character, UUID accountId, int templateId) {
        JDBC.sql("INSERT INTO character_transmogs (character_id, account_id, item_template_id) VALUES (?, ?, ?)")
                .param(character)
                .param(accountId)
                .param(templateId)
                .update();
    }

    private static List<Tuple> transmogs() {
        return JDBC.sql("SELECT character_id, account_id, item_template_id FROM character_transmogs")
                .query((rs, rowNum) -> tuple(
                        rs.getObject("character_id", UUID.class),
                        rs.getObject("account_id", UUID.class),
                        rs.getInt("item_template_id")))
                .list();
    }
}
