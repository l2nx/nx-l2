/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.network.enums.MessageSenderType;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.model.ItemRow;
import app.l2nx.highfive.repository.mail.MailRepository;
import app.l2nx.highfive.repository.mail.model.MailRow;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MailRepositoryTest extends RepositoryTest {
    private final MailRepository repository = new MailRepository(JDBC, registry);

    @Nested
    class Insert {
        @Test
        void insert_shouldStorePlayerMailAndReadItBack() {
            final MailRow mail = playerMail();

            repository.insert(mail);

            assertThat(find(mail.id())).isEqualTo(mail);
        }

        @Test
        void insert_shouldStoreSentinelsAsNull_whenSystemReturnMail() {
            final MailRow mail = playerMail().toBuilder()
                    .senderId(-1)
                    .subject("")
                    .content("")
                    .reqAdena(0)
                    .senderType(MessageSenderType.NONE)
                    .returned(true)
                    .build();

            repository.insert(mail);

            assertThat(stored(mail.id()))
                    .containsEntry("sender_id", null)
                    .containsEntry("subject", null)
                    .containsEntry("content", null)
                    .containsEntry("cod_price", null)
                    .containsEntry("sender_type", "NONE");
            assertThat(find(mail.id()))
                    .extracting(MailRow::senderId, MailRow::subject, MailRow::reqAdena)
                    .containsExactly(-1, null, 0L);
        }

        @Test
        void insert_shouldCommitWithAttachmentWrittenAfter() {
            final MailRow mail = playerMail();
            final ItemRepository items = new ItemRepository(JDBC, registry);
            final ItemRow attachment = ItemRow.builder()
                    .objectId(registry.allocateTransient())
                    .itemId(57)
                    .count(100)
                    .location(ItemLocation.MAIL)
                    .locationSlot(mail.id())
                    .mana(-1)
                    .expiresAt(null)
                    .build();

            TRANSACTIONS.executeWithoutResult(status -> {
                repository.insert(mail);
                items.insert(mail.senderId(), attachment);
            });

            assertThat(items.findByMail(mail.id()))
                    .extracting(ItemRow::objectId)
                    .containsExactly(attachment.objectId());
        }

        @Test
        void attachment_shouldBeRejected_whenItsMailIsNotStored() {
            final MailRow mail = playerMail();
            final ItemRow attachment = ItemRow.builder()
                    .objectId(registry.allocateTransient())
                    .itemId(57)
                    .count(100)
                    .location(ItemLocation.MAIL)
                    .locationSlot(mail.id())
                    .mana(-1)
                    .expiresAt(null)
                    .build();

            assertThatThrownBy(() -> new ItemRepository(JDBC, registry).insert(mail.senderId(), attachment))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(registry.findId(mail.id())).isEmpty();
        }
    }

    @Nested
    class Mark {
        @Test
        void markRead_shouldClearUnread() {
            assertFlag(repository::markRead, "unread", false);
        }

        @Test
        void markDeletedBySender_shouldSetFlag() {
            assertFlag(repository::markDeletedBySender, "deleted_by_sender", true);
        }

        @Test
        void markDeletedByReceiver_shouldSetFlag() {
            assertFlag(repository::markDeletedByReceiver, "deleted_by_receiver", true);
        }

        @Test
        void markAttachmentsRemoved_shouldClearFlag() {
            assertFlag(repository::markAttachmentsRemoved, "has_attachments", false);
        }

        private void assertFlag(IntConsumer mark, String column, boolean expected) {
            final MailRow mail = playerMail();
            repository.insert(mail);

            mark.accept(mail.id());

            assertThat(stored(mail.id())).containsEntry(column, expected);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveMailWithAttachments() {
            final MailRow mail = playerMail();
            repository.insert(mail);
            final UUID attachment = insertAttachment(registry.idOf(mail.id()));

            repository.delete(mail.id());

            assertThat(repository.findAll()).noneMatch(row -> row.id() == mail.id());
            assertThat(JDBC.sql("SELECT count(*) FROM items WHERE id = ?")
                            .param(attachment)
                            .query(Integer.class)
                            .single())
                    .isZero();
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldOrderBySoonestExpiry() {
            final MailRow later = playerMail();
            final MailRow sooner = playerMail().toBuilder()
                    .id(registry.allocateTransient())
                    .expiration(later.expiration().minusSeconds(1))
                    .build();
            repository.insert(later);
            repository.insert(sooner);

            assertThat(repository.findAll())
                    .filteredOn(row -> (row.id() == later.id()) || (row.id() == sooner.id()))
                    .containsExactly(sooner, later);
        }
    }

    private MailRow playerMail() {
        return MailRow.builder()
                .id(registry.allocateTransient())
                .senderId(registry.handleOf(insertCharacter()))
                .receiverId(registry.handleOf(insertCharacter()))
                .subject("Hello")
                .content("Some adena")
                .expiration(NOW)
                .reqAdena(5000)
                .hasAttachments(true)
                .unread(true)
                .deletedBySender(false)
                .deletedByReceiver(false)
                .senderType(MessageSenderType.PLAYER)
                .returned(false)
                .build();
    }

    private MailRow find(int id) {
        return repository.findAll().stream()
                .filter(row -> row.id() == id)
                .findFirst()
                .orElseThrow();
    }

    private Map<String, Object> stored(int id) {
        return JDBC.sql("SELECT * FROM mails WHERE id = ?")
                .param(registry.idOf(id))
                .query()
                .singleRow();
    }

    private static UUID insertAttachment(UUID mail) {
        final UUID id = EntityIds.next();
        JDBC.sql("""
            INSERT INTO items (id, item_template_id, count, enchant_level, location, mail_id, custom_type1, custom_type2)
            VALUES (?, 57, 1, 0, 'MAIL', ?, 0, 0)\
            """).param(id).param(mail).update();
        return id;
    }
}
