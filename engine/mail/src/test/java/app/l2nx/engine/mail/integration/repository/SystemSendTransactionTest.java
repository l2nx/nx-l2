/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.core.persistence.value.ValueOutcome;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.MailType;
import app.l2nx.engine.mail.NewItem;
import app.l2nx.engine.mail.SystemMail;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.fixture.TestItemStore;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.repository.SendTransaction;
import app.l2nx.engine.mail.repository.SystemSendTransaction;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SystemSendTransactionTest {
    static final int ARROW = 17;
    static final int SWORD = 1000;

    @AutoClose
    private final MailWorld world = new MailWorld(MailSettings.defaults());

    private final UUID receiver = world.character("Receiver");

    private SystemSendTransaction send(UUID receiverId, List<NewItem> items) {
        return new SystemSendTransaction(
                world.writer,
                SystemMail.builder()
                        .receiverId(receiverId)
                        .type(MailType.BIRTHDAY)
                        .subject("Happy birthday")
                        .body("A gift")
                        .items(items)
                        .idempotencyKey(EntityIds.next())
                        .build());
    }

    private static List<NewItem> gifts() {
        return List.of(new NewItem(ARROW, 20, 0), new NewItem(SWORD, 1, 5));
    }

    private UUID sentMail(ValueOutcome<SendTransaction.Sent> outcome) {
        assertThat(outcome).isInstanceOf(ValueOutcome.Committed.class);
        return ((ValueOutcome.Committed<SendTransaction.Sent>) outcome).result().mailId();
    }

    private int attachmentRows() {
        return world.jdbc
                .sql("SELECT count(*) FROM mail_attachments")
                .query(Integer.class)
                .single();
    }

    @Nested
    class Committed {
        @Test
        void execute_shouldCreateTheItemsInTheMailWithTheirSnapshots_whenTheReceiverExists() {
            final UUID mailId = sentMail(world.run(send(receiver, gifts())));

            assertThat(world.row(mailId))
                    .isEqualTo(MailRow.builder()
                            .id(mailId)
                            .type(MailType.BIRTHDAY)
                            .receiverId(receiver)
                            .receiverName("Receiver")
                            .subject("Happy birthday")
                            .body("A gift")
                            .attachments(AttachmentState.ATTACHED)
                            .sentAt(MailWorld.START)
                            .expiresAt(MailWorld.START.plus(world.settings.expiry()))
                            .build());
            assertThat(world.attachments.findByMail(mailId))
                    .extracting(
                            AttachmentSnapshot::slot,
                            AttachmentSnapshot::itemTemplateId,
                            AttachmentSnapshot::count,
                            AttachmentSnapshot::enchantLevel)
                    .containsExactly(tuple(0, ARROW, 20L, 0), tuple(1, SWORD, 1L, 5));
            assertThat(world.store.itemsInMail(mailId))
                    .extracting(TestItemStore.TestRow::itemTemplateId, TestItemStore.TestRow::count)
                    .containsExactlyInAnyOrder(tuple(ARROW, 20L), tuple(SWORD, 1L));
            assertThat(world.ledger(HolderType.MAIL, mailId, ARROW)).isEqualTo(20);
            assertThat(world.ledger(HolderType.MAIL, mailId, SWORD)).isOne();
        }

        @Test
        void execute_shouldStoreAMailWithoutAttachments_whenItCarriesNoItems() {
            final UUID mailId = sentMail(world.run(send(receiver, List.of())));

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.NONE);
            assertThat(world.attachments.findByMail(mailId)).isEmpty();
            assertThat(world.store.itemsInMail(mailId)).isEmpty();
        }
    }

    @Nested
    class Refused {
        @Test
        void execute_shouldRefuseAndStoreNothing_whenTheReceiverDoesNotExist() {
            final ValueOutcome<SendTransaction.Sent> outcome = world.run(send(EntityIds.next(), gifts()));

            assertThat(outcome)
                    .isEqualTo(ValueOutcome.Refused.<SendTransaction.Sent>builder()
                            .reason(MailRefusal.RECEIVER_NOT_FOUND)
                            .build());
            assertThat(world.transactionsOf("MAIL_SEND")).isZero();
        }

        @Test
        void execute_shouldFailAndRollBackTheMail_whenAnItemCannotBeCreated() {
            final ValueOutcome<SendTransaction.Sent> outcome =
                    world.run(send(receiver, List.of(new NewItem(ARROW, 20, 0), new NewItem(0, 1, 0))));

            assertThat(outcome).isInstanceOf(ValueOutcome.Failed.class);
            assertThat(world.receivedBy(receiver)).isEmpty();
            assertThat(attachmentRows()).isZero();
            assertThat(world.jdbc
                            .sql("SELECT count(*) FROM items WHERE location = 'MAIL'")
                            .query(Integer.class)
                            .single())
                    .isZero();
            assertThat(world.transactionsOf("MAIL_SEND")).isZero();
        }
    }

    @Nested
    class Replayed {
        @Test
        void execute_shouldReplayWithoutCreatingAgain_whenTheKeyIsUsedAgain() {
            final SystemSendTransaction send = send(receiver, gifts());
            final UUID mailId = sentMail(world.run(send));

            final ValueOutcome<SendTransaction.Sent> outcome = world.run(send);

            assertThat(outcome).isInstanceOfSatisfying(ValueOutcome.Replayed.class, replayed -> {
                assertThat(replayed.subjectId()).isEqualTo(mailId);
                assertThat(replayed.movements()).hasSize(2);
            });
            assertThat(world.receivedBy(receiver)).hasSize(1);
            assertThat(world.store.itemsInMail(mailId)).hasSize(2);
            assertThat(attachmentRows()).isEqualTo(2);
        }
    }
}
