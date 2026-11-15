/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.value.Holder;
import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.core.persistence.value.ValueOutcome;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.ItemRowState;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.MailType;
import app.l2nx.engine.mail.Reservation;
import app.l2nx.engine.mail.ReservedItem;
import app.l2nx.engine.mail.fixture.FakeItems;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.fixture.TestItemStore;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.repository.SendTransaction;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class SendTransactionTest {
    static final int ARROW = 17;
    static final int SWORD = 1000;
    static final long FEE = 100;

    // One mail per box, so a single stored mail fills it.
    @AutoClose
    private final MailWorld world =
            new MailWorld(MailSettings.defaults().toBuilder().mailboxLimit(1).build());

    private final UUID sender = world.character("Sender");
    private final UUID receiver = world.character("Receiver");
    private final ItemRowState adena = stored(FakeItems.ADENA, 1_000);
    private final ItemRowState sword = stored(SWORD, 1);
    private final ItemRowState arrows = stored(ARROW, 50);

    private ItemRowState stored(int itemTemplateId, long count) {
        return ItemRowState.builder()
                .itemId(world.give(sender, itemTemplateId, count))
                .ownerId(sender)
                .itemTemplateId(itemTemplateId)
                .count(count)
                .build();
    }

    private Reservation reservation(List<ReservedItem> items, long fee) {
        final List<AttachmentSnapshot> snapshots = IntStream.range(0, items.size())
                .mapToObj(slot -> AttachmentSnapshot.builder()
                        .slot(slot)
                        .itemTemplateId(items.get(slot).row().itemTemplateId())
                        .count(items.get(slot).count())
                        .enchantOptions(List.of())
                        .build())
                .toList();
        return Reservation.builder()
                .characterId(sender)
                .items(items)
                .adena(fee > 0 ? adena : null)
                .adenaAmount(fee)
                .snapshots(snapshots)
                .build();
    }

    private Reservation swordAndArrows() {
        return reservation(List.of(new ReservedItem(sword, 1), new ReservedItem(arrows, 20)), FEE);
    }

    private SendTransaction send(String receiverName, Reservation reservation, Long codAdena) {
        return SendTransaction.builder()
                .writer(world.writer)
                .idempotencyKey(EntityIds.next())
                .senderId(sender)
                .senderName("Sender")
                .receiverName(receiverName)
                .subject("Hello")
                .body("Body")
                .codAdena(codAdena)
                .reservation(reservation)
                .build();
    }

    private UUID sentMail(ValueOutcome<SendTransaction.Sent> outcome) {
        assertThat(outcome).isInstanceOf(ValueOutcome.Committed.class);
        return ((ValueOutcome.Committed<SendTransaction.Sent>) outcome).result().mailId();
    }

    private void assertNothingSent() {
        assertThat(world.receivedBy(receiver)).isEmpty();
        assertThat(world.store.itemsOf(Holder.inventory(sender)))
                .extracting(TestItemStore.TestRow::itemTemplateId, TestItemStore.TestRow::count)
                .containsExactly(tuple(ARROW, 50L), tuple(FakeItems.ADENA, 1_000L), tuple(SWORD, 1L));
        assertThat(world.transactionsOf("MAIL_SEND")).isZero();
        assertThat(world.ledger(HolderType.INVENTORY, sender, FakeItems.ADENA)).isZero();
    }

    @Nested
    class Committed {
        @Test
        void execute_shouldStoreTheMailItsSnapshotsAndItems_andTakeTheFee_whenTheMailIsSent() {
            final Reservation reservation = swordAndArrows();

            final ValueOutcome<SendTransaction.Sent> outcome = world.run(send("Receiver", reservation, null));

            final UUID mailId = sentMail(outcome);
            assertThat(((ValueOutcome.Committed<SendTransaction.Sent>) outcome).result())
                    .isEqualTo(SendTransaction.Sent.builder()
                            .mailId(mailId)
                            .receiverId(receiver)
                            .build());
            assertThat(world.row(mailId))
                    .isEqualTo(MailRow.builder()
                            .id(mailId)
                            .type(MailType.PLAYER)
                            .senderId(sender)
                            .senderName("Sender")
                            .receiverId(receiver)
                            .receiverName("Receiver")
                            .subject("Hello")
                            .body("Body")
                            .attachments(AttachmentState.ATTACHED)
                            .sentAt(MailWorld.START)
                            .expiresAt(MailWorld.START.plus(world.settings.expiry()))
                            .build());
            assertThat(world.attachments.findByMail(mailId)).isEqualTo(reservation.snapshots());
            assertThat(world.store.itemsInMail(mailId))
                    .extracting(TestItemStore.TestRow::itemTemplateId, TestItemStore.TestRow::count)
                    .containsExactlyInAnyOrder(tuple(SWORD, 1L), tuple(ARROW, 20L));
            assertThat(world.store.itemsInMail(mailId))
                    .filteredOn(row -> row.itemTemplateId() == SWORD)
                    .extracting(TestItemStore.TestRow::itemId)
                    .containsExactly(sword.itemId());
            final Holder inventory = Holder.inventory(sender);
            assertThat(world.count(inventory, FakeItems.ADENA)).isEqualTo(1_000 - FEE);
            assertThat(world.count(inventory, ARROW)).isEqualTo(30);
            assertThat(world.count(inventory, SWORD)).isZero();
            assertThat(world.ledger(HolderType.INVENTORY, sender, FakeItems.ADENA))
                    .isEqualTo(-FEE);
            assertThat(world.ledger(HolderType.INVENTORY, sender, ARROW)).isEqualTo(-20);
            assertThat(world.ledger(HolderType.MAIL, mailId, ARROW)).isEqualTo(20);
            assertThat(world.ledger(HolderType.INVENTORY, sender, SWORD)).isEqualTo(-1);
            assertThat(world.ledger(HolderType.MAIL, mailId, SWORD)).isOne();
        }

        @ParameterizedTest(name = "cash on delivery {0}")
        @CsvSource(textBlock = """
            ,    360
            700, 12
            """)
        void execute_shouldTakeTheExpiryOfItsKind_whenTheMailIsSent(Long codAdena, long expiryHours) {
            final UUID mailId = sentMail(world.run(send("Receiver", swordAndArrows(), codAdena)));

            assertThat(world.row(mailId).codAdena()).isEqualTo(codAdena);
            assertThat(world.row(mailId).expiresAt()).isEqualTo(MailWorld.START.plus(Duration.ofHours(expiryHours)));
        }

        @Test
        void execute_shouldStoreAMailWithoutAttachments_whenNothingIsReserved() {
            final UUID mailId = sentMail(world.run(send("Receiver", reservation(List.of(), FEE), null)));

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.NONE);
            assertThat(world.attachments.findByMail(mailId)).isEmpty();
            assertThat(world.store.itemsInMail(mailId)).isEmpty();
            assertThat(world.count(Holder.inventory(sender), FakeItems.ADENA)).isEqualTo(1_000 - FEE);
        }

        @Test
        void execute_shouldMoveAttachedAdenaFromWhatTheFeeLeft_whenTheFeeAndTheAttachmentShareTheRow() {
            final UUID mailId = sentMail(world.run(
                    send("Receiver", reservation(List.of(new ReservedItem(adena, 500)), FEE), null)));

            assertThat(world.count(Holder.inventory(sender), FakeItems.ADENA)).isEqualTo(1_000 - FEE - 500);
            assertThat(world.store.itemsInMail(mailId))
                    .extracting(TestItemStore.TestRow::count)
                    .containsExactly(500L);
            assertThat(world.ledger(HolderType.INVENTORY, sender, FakeItems.ADENA))
                    .isEqualTo(-FEE - 500);
            assertThat(world.ledger(HolderType.MAIL, mailId, FakeItems.ADENA)).isEqualTo(500);
        }
    }

    @Nested
    class Refused {
        enum Refusal {
            UNKNOWN_RECEIVER(MailRefusal.RECEIVER_NOT_FOUND, null),
            SELF(MailRefusal.RECEIVER_SELF, null),
            GM_RECEIVER(MailRefusal.RECEIVER_GM_ONLY, "Receiver"),
            BLOCKED(MailRefusal.RECEIVER_BLOCKED, "Receiver"),
            RECEIVER_BOX_FULL(MailRefusal.RECEIVER_MAILBOX_FULL, null),
            SENDER_BOX_FULL(MailRefusal.SENDER_MAILBOX_FULL, null);

            final MailRefusal reason;
            final String param;

            Refusal(MailRefusal reason, String param) {
                this.reason = reason;
                this.param = param;
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Refusal.class)
        void execute_shouldRefuseAndChangeNothing_whenTheReceiverCannotGetIt(Refusal refusal) {
            final UUID other = world.character("Other");
            final String receiverName = switch (refusal) {
                case UNKNOWN_RECEIVER -> "Nobody";
                case SELF -> "Sender";
                case GM_RECEIVER -> {
                    world.gm(receiver);
                    yield "Receiver";
                }
                case BLOCKED -> {
                    world.store.block(receiver, sender);
                    yield "Receiver";
                }
                case RECEIVER_BOX_FULL -> {
                    world.insert(world.mail(other, receiver).build());
                    yield "Receiver";
                }
                case SENDER_BOX_FULL -> {
                    world.insert(world.mail(sender, other).build());
                    yield "Receiver";
                }
            };

            final ValueOutcome<SendTransaction.Sent> outcome = world.run(send(receiverName, swordAndArrows(), null));

            assertThat(outcome)
                    .isEqualTo(ValueOutcome.Refused.<SendTransaction.Sent>builder()
                            .reason(refusal.reason)
                            .param(refusal.param)
                            .build());
            assertThat(world.receivedBy(receiver))
                    .allSatisfy(mail -> assertThat(mail.senderId()).isEqualTo(other));
            assertThat(world.count(Holder.inventory(sender), FakeItems.ADENA)).isEqualTo(1_000);
            assertThat(world.count(Holder.inventory(sender), SWORD)).isOne();
            assertThat(world.transactionsOf("MAIL_SEND")).isZero();
        }

        @Test
        void execute_shouldFailAndRollBackTheMailAndTheFee_whenAReservedRowChangedOwner() {
            world.jdbc
                    .sql("UPDATE items SET owner_id = ? WHERE id = ?")
                    .param(receiver)
                    .param(sword.itemId())
                    .update();

            final ValueOutcome<SendTransaction.Sent> outcome = world.run(send("Receiver", swordAndArrows(), null));

            assertThat(outcome).isInstanceOf(ValueOutcome.Failed.class);
            assertThat(world.receivedBy(receiver)).isEmpty();
            assertThat(world.jdbc
                            .sql("SELECT count(*) FROM mail_attachments")
                            .query(Integer.class)
                            .single())
                    .isZero();
            assertThat(world.count(Holder.inventory(sender), FakeItems.ADENA)).isEqualTo(1_000);
            assertThat(world.count(Holder.inventory(sender), ARROW)).isEqualTo(50);
            assertThat(world.transactionsOf("MAIL_SEND")).isZero();
            assertThat(world.ledger(HolderType.INVENTORY, sender, FakeItems.ADENA))
                    .isZero();
        }
    }

    @Nested
    class Replayed {
        @Test
        void execute_shouldReplayWithoutSendingAgain_whenTheKeyIsUsedAgain() {
            final SendTransaction send = send("Receiver", swordAndArrows(), null);
            final UUID mailId = sentMail(world.run(send));

            final ValueOutcome<SendTransaction.Sent> outcome = world.run(send);

            assertThat(outcome).isInstanceOfSatisfying(ValueOutcome.Replayed.class, replayed -> {
                assertThat(replayed.subjectId()).isEqualTo(mailId);
                assertThat(replayed.movements()).hasSize(5);
            });
            assertThat(world.receivedBy(receiver)).hasSize(1);
            assertThat(world.count(Holder.inventory(sender), FakeItems.ADENA)).isEqualTo(1_000 - FEE);
            assertThat(world.count(Holder.inventory(sender), ARROW)).isEqualTo(30);
            assertThat(world.transactionsOf("MAIL_SEND")).isOne();
        }
    }
}
