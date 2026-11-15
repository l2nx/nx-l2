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
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.fixture.TestItemStore;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.repository.ReturnTransaction;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReturnTransactionTest {
    static final int ARROW = 17;
    static final int SWORD = 1000;

    @AutoClose
    private final MailWorld world = new MailWorld(MailSettings.defaults());

    private final UUID sender = world.character("Sender");
    private final UUID receiver = world.character("Receiver");

    private UUID attachedMail(MailRow.MailRowBuilder mail) {
        final UUID mailId = world.insert(mail.build());
        world.attach(mailId, SWORD, 1);
        world.attach(mailId, ARROW, 20);
        return mailId;
    }

    private UUID attachedMail() {
        return attachedMail(
                world.mail(sender, receiver).body("Body").codAdena(700L).attachments(AttachmentState.ATTACHED));
    }

    private ReturnTransaction returnMail(UUID mailId, UUID receiverId) {
        return ReturnTransaction.builder()
                .writer(world.writer)
                .idempotencyKey(EntityIds.next())
                .mailId(mailId)
                .receiverId(receiverId)
                .build();
    }

    private int mails() {
        return world.jdbc.sql("SELECT count(*) FROM mails").query(Integer.class).single();
    }

    @Nested
    class Committed {
        @Test
        void execute_shouldSendTheAttachmentsBackInANewMail_whenTheReceiverReturnsIt() {
            final UUID original = attachedMail();
            final MailRow sent = world.row(original);

            final ValueOutcome<ReturnTransaction.Returned> outcome = world.run(returnMail(original, receiver));

            assertThat(outcome).isInstanceOf(ValueOutcome.Committed.class);
            final ReturnTransaction.Returned actual =
                    ((ValueOutcome.Committed<ReturnTransaction.Returned>) outcome).result();
            assertThat(actual.senderId()).isEqualTo(sender);
            final UUID returned = actual.mailId();
            assertThat(world.row(original).attachments()).isEqualTo(AttachmentState.RETURNED);
            assertThat(world.row(original).settledAt()).isEqualTo(MailWorld.START);
            assertThat(world.row(returned))
                    .isEqualTo(MailRow.builder()
                            .id(returned)
                            .type(MailType.PLAYER)
                            .senderId(receiver)
                            .senderName(sent.receiverName())
                            .receiverId(sender)
                            .receiverName(sent.senderName())
                            .subject(sent.subject())
                            .body("Body")
                            .attachments(AttachmentState.ATTACHED)
                            .returnedFromId(original)
                            .sentAt(MailWorld.START)
                            .expiresAt(MailWorld.START.plus(world.settings.expiry()))
                            .senderDeletedAt(MailWorld.START)
                            .build());
            assertThat(world.attachments.findByMail(returned)).isEqualTo(world.attachments.findByMail(original));
            assertThat(world.store.itemsInMail(original)).isEmpty();
            assertThat(world.store.itemsInMail(returned))
                    .extracting(TestItemStore.TestRow::itemTemplateId, TestItemStore.TestRow::count)
                    .containsExactlyInAnyOrder(tuple(SWORD, 1L), tuple(ARROW, 20L));
            assertThat(world.ledger(HolderType.MAIL, original, ARROW)).isEqualTo(-20);
            assertThat(world.ledger(HolderType.MAIL, returned, ARROW)).isEqualTo(20);
            assertThat(world.ledger(HolderType.MAIL, original, SWORD)).isEqualTo(-1);
            assertThat(world.ledger(HolderType.MAIL, returned, SWORD)).isOne();
        }
    }

    @Nested
    class Refused {
        enum NotReturnable {
            SYSTEM_MAIL,
            ALREADY_RETURNED,
            SENDER_DELETED,
            SETTLED,
            NOT_THE_RECEIVER
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(NotReturnable.class)
        void execute_shouldRefuseAndChangeNothing_whenTheMailCannotBeReturned(NotReturnable reason) {
            final MailRow.MailRowBuilder mail = world.mail(sender, receiver).attachments(AttachmentState.ATTACHED);
            switch (reason) {
                case SYSTEM_MAIL -> mail.type(MailType.SYSTEM).senderId(null).senderName(null);
                case ALREADY_RETURNED -> mail.returnedFromId(attachedMail());
                case SENDER_DELETED -> mail.senderId(null);
                case SETTLED -> mail.attachments(AttachmentState.RECEIVED);
                case NOT_THE_RECEIVER -> {}
            }
            final UUID mailId = attachedMail(mail);
            final MailRow before = world.row(mailId);
            final int mailsBefore = mails();

            final ValueOutcome<ReturnTransaction.Returned> outcome =
                    world.run(returnMail(mailId, reason == NotReturnable.NOT_THE_RECEIVER ? sender : receiver));

            assertThat(outcome)
                    .isEqualTo(ValueOutcome.Refused.<ReturnTransaction.Returned>builder()
                            .reason(MailRefusal.NOT_RETURNABLE)
                            .build());
            assertThat(world.row(mailId)).isEqualTo(before);
            assertThat(mails()).isEqualTo(mailsBefore);
            assertThat(world.store.itemsInMail(mailId)).hasSize(2);
        }
    }

    @Nested
    class Replayed {
        @Test
        void execute_shouldReplayWithoutReturningAgain_whenTheKeyIsUsedAgain() {
            final UUID original = attachedMail();
            final ReturnTransaction returnMail = returnMail(original, receiver);
            final ValueOutcome<ReturnTransaction.Returned> first = world.run(returnMail);
            final UUID returned = ((ValueOutcome.Committed<ReturnTransaction.Returned>) first)
                    .result()
                    .mailId();

            final ValueOutcome<ReturnTransaction.Returned> outcome = world.run(returnMail);

            assertThat(outcome).isInstanceOfSatisfying(ValueOutcome.Replayed.class, replayed -> {
                assertThat(replayed.subjectId()).isEqualTo(returned);
                assertThat(replayed.movements()).hasSize(4);
            });
            assertThat(world.receivedBy(sender)).extracting(MailRow::id).containsExactly(returned);
            assertThat(world.store.itemsInMail(returned)).hasSize(2);
            assertThat(world.transactionsOf("MAIL_RETURN")).isOne();
        }
    }
}
