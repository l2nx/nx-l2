/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.world;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.mechanics.punishment.PunishmentAffect;
import app.l2nx.highfive.mechanics.punishment.PunishmentType;
import app.l2nx.highfive.repository.world.PunishmentRepository;
import app.l2nx.highfive.repository.world.model.Punishment;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PunishmentRepositoryTest extends RepositoryTest {
    private final PunishmentRepository repository = new PunishmentRepository(JDBC, registry);

    private static Punishment.PunishmentBuilder punishment(PunishmentAffect affect) {
        return Punishment.builder()
                .affect(affect)
                .type(PunishmentType.CHAT_BAN)
                .expiration(NOW)
                .reason("Spam")
                .punishedBy("GM");
    }

    private Punishment find(long id) {
        return repository.findAll().stream()
                .filter(row -> row.id() == id)
                .findFirst()
                .orElseThrow();
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreCharacterUuid_whenAffectIsCharacter() {
            final UUID character = insertCharacter();

            final long id = repository.insert(punishment(PunishmentAffect.CHARACTER)
                    .characterId(registry.handleOf(character))
                    .build());

            assertThat(JDBC.sql("SELECT character_id FROM punishments WHERE id = ?")
                            .param(id)
                            .query(UUID.class)
                            .single())
                    .isEqualTo(character);
        }

        @Test
        void insert_shouldStoreNullExpirationAndReason_whenPermanentWithBlankReason() {
            final long id = repository.insert(punishment(PunishmentAffect.HWID)
                    .hwid("00-11-22-33-44-55")
                    .expiration(null)
                    .reason(" ")
                    .build());

            assertThat(JDBC.sql("SELECT expires_at IS NULL AND reason IS NULL FROM punishments WHERE id = ?")
                            .param(id)
                            .query(Boolean.class)
                            .single())
                    .isTrue();
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnTypedTargets_whenEachAffectIsStored() {
            final UUID account = EntityIds.next();
            final UUID character = insertCharacter();
            final Punishment accountBan = punishment(PunishmentAffect.ACCOUNT)
                    .accountId(account)
                    .type(PunishmentType.BAN)
                    .build();
            final Punishment characterJail = punishment(PunishmentAffect.CHARACTER)
                    .characterId(registry.handleOf(character))
                    .type(PunishmentType.JAIL)
                    .expiration(null)
                    .build();
            final Punishment ipBan =
                    punishment(PunishmentAffect.IP).ip("10.0.0.7").reason(null).build();
            final long accountId = repository.insert(accountBan);
            final long characterId = repository.insert(characterJail);
            final long ipId = repository.insert(ipBan);

            assertThat(find(accountId))
                    .isEqualTo(accountBan.toBuilder().id(accountId).build());
            assertThat(find(characterId))
                    .isEqualTo(characterJail.toBuilder().id(characterId).build());
            assertThat(find(ipId)).isEqualTo(ipBan.toBuilder().id(ipId).build());
        }
    }

    @Nested
    class UpdateExpiration {
        @Test
        void updateExpiration_shouldSetEndTime_whenPunishmentEndsEarly() {
            final long id = repository.insert(punishment(PunishmentAffect.IP)
                    .ip("10.0.0.8")
                    .expiration(null)
                    .build());
            final Instant end = NOW.plusSeconds(30);

            repository.updateExpiration(id, end);

            assertThat(find(id).expiration()).isEqualTo(end);
        }
    }
}
