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
import app.l2nx.engine.mail.IncomingTargets;
import app.l2nx.engine.mail.ItemRowState;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.fixture.FakeItems;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.fixture.TestItemStore;
import app.l2nx.engine.mail.repository.ReceiveTransaction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReceiveTransactionTest {
    static final int ARROW = 17;
    static final int SWORD = 1000;

    @AutoClose
    private final MailWorld world = new MailWorld(MailSettings.defaults());

    private final UUID sender = world.character("Sender");
    private final UUID receiver = world.character("Receiver");

    private UUID attachedMail(Long codAdena) {
        final UUID mailId = world.insert(world.mail(sender, receiver)
                .attachments(AttachmentState.ATTACHED)
                .codAdena(codAdena)
                .build());
        world.attach(mailId, SWORD, 1);
        world.attach(mailId, ARROW, 20);
        return mailId;
    }

    private ReceiveTransaction receive(UUID mailId, Long codAdena, IncomingTargets targets) {
        return ReceiveTransaction.builder()
                .writer(world.writer)
                .idempotencyKey(EntityIds.next())
                .mailId(mailId)
                .receiver(targets)
                .senderId(sender)
                .codAdena(codAdena)
                .charge(codAdena != null)
                .build();
    }

    private IncomingTargets noStacks() {
        return IncomingTargets.builder().characterId(receiver).stacks(List.of()).build();
    }

    private IncomingTargets online(ItemRowState adena, ItemRowState... stacks) {
        final List<ItemRowState> rows = new ArrayList<>(List.of(stacks));
        if (adena != null) {
            rows.add(adena);
        }
        return IncomingTargets.builder()
                .characterId(receiver)
                .stacks(rows)
                .adena(adena)
                .build();
    }

    private ItemRowState stored(UUID ownerId, int itemTemplateId, long count) {
        return state(world.give(ownerId, itemTemplateId, count), ownerId, itemTemplateId, count);
    }

    private static ItemRowState state(UUID itemId, UUID ownerId, int itemTemplateId, long count) {
        return ItemRowState.builder()
                .itemId(itemId)
                .ownerId(ownerId)
                .itemTemplateId(itemTemplateId)
                .count(count)
                .build();
    }

    private static ReceiveTransaction.Received received(ValueOutcome<ReceiveTransaction.Received> outcome) {
        assertThat(outcome).isInstanceOf(ValueOutcome.Committed.class);
        return ((ValueOutcome.Committed<ReceiveTransaction.Received>) outcome).result();
    }

    @Nested
    class Committed {
        @Test
        void execute_shouldMergeIntoTheStackByAbsoluteCount_whenTheReceiverHoldsOne() {
            final ItemRowState arrows = stored(receiver, ARROW, 10);
            final UUID mailId = attachedMail(null);

            final ReceiveTransaction.Received actual = received(world.run(receive(mailId, null, online(null, arrows))));

            final Holder inventory = Holder.inventory(receiver);
            assertThat(actual.attachments()).isEqualTo(AttachmentState.RECEIVED);
            assertThat(actual.senderRows()).isEmpty();
            assertThat(actual.receiverRows())
                    .map(TestItemStore.TestRow.class::cast)
                    .extracting(TestItemStore.TestRow::itemTemplateId, TestItemStore.TestRow::count)
                    .containsExactlyInAnyOrder(tuple(SWORD, 1L), tuple(ARROW, 30L));
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.RECEIVED);
            assertThat(world.row(mailId).settledAt()).isEqualTo(MailWorld.START);
            assertThat(world.store.itemsOf(inventory))
                    .extracting(TestItemStore.TestRow::itemTemplateId, TestItemStore.TestRow::count)
                    .containsExactly(tuple(ARROW, 30L), tuple(SWORD, 1L));
            assertThat(world.store.itemsOf(inventory))
                    .filteredOn(row -> row.itemTemplateId() == ARROW)
                    .extracting(TestItemStore.TestRow::itemId)
                    .containsExactly(arrows.itemId());
            assertThat(world.store.itemsInMail(mailId)).isEmpty();
            assertThat(world.ledger(HolderType.INVENTORY, receiver, ARROW)).isEqualTo(20);
            assertThat(world.ledger(HolderType.MAIL, mailId, ARROW)).isEqualTo(-20);
            assertThat(world.ledger(HolderType.INVENTORY, receiver, SWORD)).isOne();
            assertThat(world.ledger(HolderType.MAIL, mailId, SWORD)).isEqualTo(-1);
        }

        @Test
        void execute_shouldPayAnOfflineSenderByDelta_andReconcileTheLedgerWithTheCounts() {
            final ItemRowState adena = stored(receiver, FakeItems.ADENA, 5_000);
            world.give(sender, FakeItems.ADENA, 1_000);
            final UUID mailId = attachedMail(700L);

            final ReceiveTransaction.Received actual = received(world.run(receive(mailId, 700L, online(adena))));

            assertThat(actual.senderRows())
                    .map(TestItemStore.TestRow.class::cast)
                    .extracting(TestItemStore.TestRow::itemTemplateId, TestItemStore.TestRow::count)
                    .containsExactly(tuple(FakeItems.ADENA, 1_700L));
            assertThat(world.ledger(HolderType.INVENTORY, receiver, FakeItems.ADENA))
                    .isEqualTo(-700);
            assertThat(world.ledger(HolderType.INVENTORY, sender, FakeItems.ADENA))
                    .isEqualTo(700);
            assertThat(world.ledger(HolderType.INVENTORY, receiver, ARROW)
                            + world.ledger(HolderType.MAIL, mailId, ARROW))
                    .isZero();
            assertThat(world.count(Holder.inventory(receiver), FakeItems.ADENA)).isEqualTo(4_300);
            assertThat(world.count(Holder.inventory(sender), FakeItems.ADENA)).isEqualTo(1_700);
            assertThat(world.count(Holder.inventory(receiver), ARROW)).isEqualTo(20);
        }

        @Test
        void execute_shouldGiveTheOfflineSenderANewAdenaRow_whenItHasNone() {
            final ItemRowState adena = stored(receiver, FakeItems.ADENA, 5_000);
            final UUID mailId = attachedMail(700L);

            received(world.run(receive(mailId, 700L, online(adena))));

            assertThat(world.store.itemsOf(Holder.inventory(sender)))
                    .extracting(TestItemStore.TestRow::itemTemplateId, TestItemStore.TestRow::count)
                    .containsExactly(tuple(FakeItems.ADENA, 700L));
            assertThat(world.ledger(HolderType.INVENTORY, sender, FakeItems.ADENA))
                    .isEqualTo(700);
        }

        @Test
        void execute_shouldWriteTheAdenaRowFromWhatTheCashOnDeliveryLeft_whenAdenaIsAttachedToo() {
            final ItemRowState adena = stored(receiver, FakeItems.ADENA, 5_000);
            world.give(sender, FakeItems.ADENA, 1_000);
            final UUID mailId = world.insert(world.mail(sender, receiver)
                    .attachments(AttachmentState.ATTACHED)
                    .codAdena(700L)
                    .build());
            world.attach(mailId, FakeItems.ADENA, 300);

            final ReceiveTransaction.Received actual = received(world.run(receive(mailId, 700L, online(adena))));

            assertThat(world.store.itemsOf(Holder.inventory(receiver)))
                    .extracting(TestItemStore.TestRow::itemId, TestItemStore.TestRow::count)
                    .containsExactly(tuple(adena.itemId(), 5_000L - 700 + 300));
            assertThat(actual.receiverRows())
                    .map(TestItemStore.TestRow.class::cast)
                    .extracting(TestItemStore.TestRow::itemId, TestItemStore.TestRow::count)
                    .containsExactly(tuple(adena.itemId(), 4_300L), tuple(adena.itemId(), 4_600L));
            assertThat(world.ledger(HolderType.INVENTORY, receiver, FakeItems.ADENA))
                    .isEqualTo(-700 + 300);
            assertThat(world.ledger(HolderType.MAIL, mailId, FakeItems.ADENA)).isEqualTo(-300);
        }

        @Test
        void execute_shouldSettleDestroyedWithoutCharging_whenTheItemsWereDestroyedOutsideMail() {
            final ItemRowState adena = stored(receiver, FakeItems.ADENA, 5_000);
            final UUID mailId = world.insert(world.mail(sender, receiver)
                    .attachments(AttachmentState.ATTACHED)
                    .codAdena(700L)
                    .build());
            world.store.deleteItem(world.attach(mailId, SWORD, 1));

            final ReceiveTransaction.Received actual = received(world.run(receive(mailId, 700L, online(adena))));

            assertThat(actual.attachments()).isEqualTo(AttachmentState.DESTROYED);
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.DESTROYED);
            assertThat(world.row(mailId).settledAt()).isEqualTo(MailWorld.START);
            assertThat(world.count(Holder.inventory(receiver), FakeItems.ADENA)).isEqualTo(5_000);
            assertThat(world.ledger(HolderType.INVENTORY, receiver, FakeItems.ADENA))
                    .isZero();
        }
    }

    @Nested
    class Refused {
        enum Limit {
            RECEIVER(MailRefusal.ADENA_LIMIT),
            SENDER(MailRefusal.SENDER_ADENA_LIMIT);

            final MailRefusal reason;

            Limit(MailRefusal reason) {
                this.reason = reason;
            }
        }

        @ParameterizedTest(name = "{0} at the adena limit")
        @EnumSource(Limit.class)
        void execute_shouldRollBackTheClaimAndThePayment_whenAnAdenaRowWouldPassTheLimit(Limit limit) {
            final ItemRowState adena =
                    stored(receiver, FakeItems.ADENA, limit == Limit.RECEIVER ? FakeItems.ADENA_LIMIT - 10 : 5_000);
            world.give(sender, FakeItems.ADENA, limit == Limit.SENDER ? FakeItems.ADENA_LIMIT - 10 : 1_000);
            final UUID mailId = attachedMail(700L);
            world.attach(mailId, FakeItems.ADENA, 1_000);
            final long receiverAdena = world.count(Holder.inventory(receiver), FakeItems.ADENA);
            final long senderAdena = world.count(Holder.inventory(sender), FakeItems.ADENA);

            final ValueOutcome<ReceiveTransaction.Received> outcome = world.run(receive(mailId, 700L, online(adena)));

            assertThat(outcome)
                    .isEqualTo(ValueOutcome.Refused.<ReceiveTransaction.Received>builder()
                            .reason(limit.reason)
                            .build());
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(world.row(mailId).settledAt()).isNull();
            assertThat(world.store.itemsInMail(mailId)).hasSize(3);
            assertThat(world.count(Holder.inventory(receiver), FakeItems.ADENA)).isEqualTo(receiverAdena);
            assertThat(world.count(Holder.inventory(sender), FakeItems.ADENA)).isEqualTo(senderAdena);
            assertThat(world.count(Holder.inventory(receiver), SWORD)).isZero();
            assertThat(world.transactionsOf("MAIL_RECEIVE")).isZero();
        }

        @Test
        void execute_shouldRefuse_whenTheMailWasSettledBefore() {
            final UUID mailId = world.insert(world.mail(sender, receiver)
                    .attachments(AttachmentState.CANCELLED)
                    .build());

            final ValueOutcome<ReceiveTransaction.Received> outcome = world.run(receive(mailId, null, noStacks()));

            assertThat(outcome)
                    .isEqualTo(ValueOutcome.Refused.<ReceiveTransaction.Received>builder()
                            .reason(MailRefusal.ALREADY_RECEIVED)
                            .build());
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.CANCELLED);
        }
    }

    @Nested
    class Replayed {
        @Test
        void execute_shouldReplay_whenTheKeyIsUsedAgain() {
            final UUID mailId = attachedMail(null);
            final ReceiveTransaction receive = receive(mailId, null, noStacks());
            received(world.run(receive));

            final ValueOutcome<ReceiveTransaction.Received> outcome = world.run(receive);

            assertThat(outcome).isInstanceOfSatisfying(ValueOutcome.Replayed.class, replayed -> {
                assertThat(replayed.subjectId()).isEqualTo(mailId);
                assertThat(replayed.movements()).hasSize(4);
            });
            assertThat(world.count(Holder.inventory(receiver), SWORD)).isOne();
            assertThat(world.count(Holder.inventory(receiver), ARROW)).isEqualTo(20);
            assertThat(world.transactionsOf("MAIL_RECEIVE")).isOne();
        }
    }
}
