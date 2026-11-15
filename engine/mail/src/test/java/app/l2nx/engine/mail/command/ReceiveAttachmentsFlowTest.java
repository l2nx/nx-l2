/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.persistence.value.Holder;
import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.core.persistence.value.ValueTransactionSubmitter;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.ParticipantState;
import app.l2nx.engine.mail.fixture.FakeItems;
import app.l2nx.engine.mail.fixture.FakeParticipants;
import app.l2nx.engine.mail.fixture.LosingTransactions;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.fixture.TestItemStore;
import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailChangeType;
import app.l2nx.engine.mail.view.MailDone;
import app.l2nx.engine.mail.view.MailNotice;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.MailStateChanged;
import app.l2nx.engine.mail.view.ReceivedMailList;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReceiveAttachmentsFlowTest {
    static final int ARROW = 17;
    static final int SWORD = 1000;

    @AutoClose
    private final MailWorld world = new MailWorld(MailSettings.defaults());

    private final MailActor receiver = world.online("Receiver");

    private UUID attachedMail(UUID senderId, Long codAdena) {
        final UUID mailId = world.insert(world.mail(senderId, receiver.characterId())
                .attachments(AttachmentState.ATTACHED)
                .codAdena(codAdena)
                .build());
        world.attach(mailId, SWORD, 1);
        world.attach(mailId, ARROW, 20);
        return mailId;
    }

    private void receive(UUID mailId) {
        MailHandlers.receive(world).handle(receiver, new ReceiveAttachments(mailId));
    }

    @Nested
    class Committed {
        @Test
        void handle_shouldMoveTheItemsAndPayTheSenderByDelta_whenTheSenderIsOffline() {
            final UUID sender = world.character("Sender");
            world.give(sender, FakeItems.ADENA, 1_000);
            world.give(receiver, FakeItems.ADENA, 5_000);
            final FakeItems.Item arrows = world.give(receiver, ARROW, 10);
            final UUID mailId = attachedMail(sender, 700L);

            receive(mailId);

            final Holder inventory = Holder.inventory(receiver.characterId());
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.RECEIVED);
            assertThat(world.row(mailId).settledAt()).isEqualTo(MailWorld.START);
            assertThat(world.count(inventory, FakeItems.ADENA)).isEqualTo(4_300);
            assertThat(world.count(inventory, SWORD)).isOne();
            assertThat(world.store.itemsOf(inventory))
                    .filteredOn(row -> row.itemTemplateId() == ARROW)
                    .singleElement()
                    .satisfies(row -> {
                        assertThat(row.itemId()).isEqualTo(arrows.id());
                        assertThat(row.count()).isEqualTo(30);
                    });
            assertThat(world.count(Holder.inventory(sender), FakeItems.ADENA)).isEqualTo(1_700);
            assertThat(world.store.itemsInMail(mailId)).isEmpty();
            assertThat(world.ledger(HolderType.MAIL, mailId, ARROW)).isEqualTo(-20);
            assertThat(world.ledger(HolderType.INVENTORY, sender, FakeItems.ADENA))
                    .isEqualTo(700);
            assertThat(FakeParticipants.port(receiver).events())
                    .hasSize(3)
                    .startsWith(
                            MailStateChanged.of(MailBox.RECEIVED, mailId, MailChangeType.ATTACHMENTS_TAKEN),
                            MailDone.of(MailNotice.RECEIVED));
            assertThat(FakeParticipants.port(receiver).events().get(2))
                    .as("a paid mail refreshes the receiver's list so the client drops its payment flag")
                    .isInstanceOfSatisfying(
                            ReceivedMailList.class,
                            list -> assertThat(list.mails())
                                    .filteredOn(summary -> summary.mailId().equals(mailId))
                                    .singleElement()
                                    .satisfies(summary ->
                                            assertThat(summary.codPending()).isFalse()));
            assertThat(world.items.pending()).isFalse();
            assertThat(world.participants.inFlight(receiver.characterId())).isZero();
            assertThat(world.participants.inFlight(sender)).isZero();
        }

        @Test
        void handle_shouldPayTheSenderAsOnlineAfterItsLoad_whenTheSenderWasLoading() {
            final UUID senderId = world.character("Sender");
            world.give(receiver, FakeItems.ADENA, 5_000);
            final UUID mailId = attachedMail(senderId, 700L);
            world.participants.startLoading(senderId);

            receive(mailId);
            assertThat(world.row(mailId).attachments())
                    .as("nothing moves while the sender's rows are read")
                    .isEqualTo(AttachmentState.ATTACHED);
            assertThat(world.scheduler.delays()).containsExactly(Duration.ofSeconds(1));

            world.participants.loaded(senderId);
            final MailActor sender = world.enter(senderId, "Sender");
            world.give(sender, FakeItems.ADENA, 1_000);
            world.scheduler.runDue();

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.RECEIVED);
            assertThat(world.count(Holder.inventory(senderId), FakeItems.ADENA)).isEqualTo(1_700);
            assertThat(FakeParticipants.port(sender).events())
                    .contains(new MailDone(MailNotice.COD_PAID, "Receiver", 700));
        }

        @Test
        void handle_shouldGiveTheSenderANewAdenaRow_whenTheOfflineSenderHasNone() {
            final UUID sender = world.character("Sender");
            world.give(receiver, FakeItems.ADENA, 5_000);
            final UUID mailId = attachedMail(sender, 700L);

            receive(mailId);

            assertThat(world.count(Holder.inventory(sender), FakeItems.ADENA)).isEqualTo(700);
        }

        @Test
        void handle_shouldTellAnOnlineSenderOfTheTaking_whenTheMailHasNoCashOnDelivery() {
            final MailActor sender = world.online("Sender");
            final UUID mailId = attachedMail(sender.characterId(), null);

            receive(mailId);

            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(
                            MailStateChanged.of(MailBox.SENT, mailId, MailChangeType.ATTACHMENTS_TAKEN),
                            MailDone.of(MailNotice.ATTACHMENTS_TAKEN, "Receiver"));
        }

        @Test
        void handle_shouldPayAnOnlineSenderAbsolutely_whenTheSenderIsInTheWorld() {
            final MailActor sender = world.online("Sender");
            final FakeItems.Item senderAdena = world.give(sender, FakeItems.ADENA, 1_000);
            world.give(receiver, FakeItems.ADENA, 5_000);
            final UUID mailId = attachedMail(sender.characterId(), 700L);

            receive(mailId);

            assertThat(world.count(Holder.inventory(sender.characterId()), FakeItems.ADENA))
                    .isEqualTo(1_700);
            assertThat(world.items.appliedIncoming())
                    .anySatisfy(rows -> assertThat(rows)
                            .containsExactly(new TestItemStore.TestRow(
                                    senderAdena.id(),
                                    HolderType.INVENTORY,
                                    sender.characterId(),
                                    FakeItems.ADENA,
                                    1_700)));
            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(
                            MailStateChanged.of(MailBox.SENT, mailId, MailChangeType.ATTACHMENTS_TAKEN),
                            new MailDone(MailNotice.COD_PAID, "Receiver", 700));
            assertThat(world.participants.inFlight(sender.characterId())).isZero();
        }

        @Test
        void handle_shouldNotCharge_whenTheSenderWasDeleted() {
            world.give(receiver, FakeItems.ADENA, 5_000);
            final UUID mailId = attachedMail(null, 700L);

            receive(mailId);

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.RECEIVED);
            assertThat(world.count(Holder.inventory(receiver.characterId()), FakeItems.ADENA))
                    .isEqualTo(5_000);
        }

        @Test
        void handle_shouldSettleDestroyedWithoutCharging_whenNoLiveItemIsLeft() {
            final UUID sender = world.character("Sender");
            world.give(receiver, FakeItems.ADENA, 5_000);
            final UUID mailId = attachedMail(sender, 700L);
            world.store.itemsInMail(mailId).forEach(row -> world.store.deleteItem(row.itemId()));

            receive(mailId);

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.DESTROYED);
            assertThat(world.count(Holder.inventory(receiver.characterId()), FakeItems.ADENA))
                    .isEqualTo(5_000);
            assertThat(world.count(Holder.inventory(sender), FakeItems.ADENA)).isZero();
            assertThat(world.items.pending()).isFalse();
        }
    }

    @Nested
    class Replayed {
        @ParameterizedTest(name = "result lost as {0}")
        @EnumSource(LosingTransactions.Loss.class)
        void handle_shouldApplyTheRowsTheLedgerNames_whenTheResultIsLost(LosingTransactions.Loss loss) {
            final MailActor sender = world.online("Sender");
            final FakeItems.Item senderAdena = world.give(sender, FakeItems.ADENA, 1_000);
            final FakeItems.Item receiverAdena = world.give(receiver, FakeItems.ADENA, 5_000);
            final FakeItems.Item arrows = world.give(receiver, ARROW, 10);
            final UUID mailId = attachedMail(sender.characterId(), 700L);
            final UUID sword = world.store.itemsInMail(mailId).stream()
                    .filter(row -> row.itemTemplateId() == SWORD)
                    .findFirst()
                    .orElseThrow()
                    .itemId();

            MailHandlers.receive(
                            world,
                            new ValueTransactionSubmitter(
                                    new LosingTransactions(world.transactions, loss), world.scheduler))
                    .handle(receiver, new ReceiveAttachments(mailId));

            final UUID receiverId = receiver.characterId();
            assertThat(world.items.appliedIncoming()).hasSize(2);
            assertThat(world.items.appliedIncoming().get(0))
                    .containsExactlyInAnyOrder(
                            new TestItemStore.TestRow(
                                    receiverAdena.id(), HolderType.INVENTORY, receiverId, FakeItems.ADENA, 4_300),
                            new TestItemStore.TestRow(sword, HolderType.INVENTORY, receiverId, SWORD, 1),
                            new TestItemStore.TestRow(arrows.id(), HolderType.INVENTORY, receiverId, ARROW, 30));
            assertThat(world.items.appliedIncoming().get(1))
                    .containsExactly(new TestItemStore.TestRow(
                            senderAdena.id(), HolderType.INVENTORY, sender.characterId(), FakeItems.ADENA, 1_700));
            assertThat(world.items.pending()).isFalse();
            assertThat(world.participants.inFlight(receiverId)).isZero();
            assertThat(world.participants.inFlight(sender.characterId())).isZero();
            assertThat(FakeParticipants.port(receiver).events())
                    .startsWith(
                            MailStateChanged.of(MailBox.RECEIVED, mailId, MailChangeType.ATTACHMENTS_TAKEN),
                            MailDone.of(MailNotice.RECEIVED))
                    .last()
                    .isInstanceOf(ReceivedMailList.class);
        }
    }

    @Nested
    class Refused {
        @Test
        void handle_shouldRefuseAlreadyReceived_whenTheAttachmentsWereTaken() {
            final UUID mailId = attachedMail(world.character("Sender"), null);
            receive(mailId);

            receive(mailId);

            assertThat(FakeParticipants.port(receiver).events())
                    .endsWith(MailRefused.of(MailAction.RECEIVE, MailRefusal.ALREADY_RECEIVED));
        }

        @Test
        void handle_shouldRefuseWithoutReserving_whenTheInventoryIsFull() {
            final UUID mailId = attachedMail(world.character("Sender"), null);
            world.items.refuseIncoming(MailRefusal.INVENTORY_FULL);

            receive(mailId);

            assertThat(FakeParticipants.port(receiver).events())
                    .containsExactly(MailRefused.of(MailAction.RECEIVE, MailRefusal.INVENTORY_FULL));
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(world.participants.inFlight(receiver.characterId())).isZero();
        }

        @Test
        void handle_shouldRefuseSenderAdenaLimit_whenTheOnlineSenderCannotHoldThePayment() {
            final MailActor sender = world.online("Sender");
            world.give(sender, FakeItems.ADENA, FakeItems.ADENA_LIMIT - 100);
            world.give(receiver, FakeItems.ADENA, 5_000);
            final UUID mailId = attachedMail(sender.characterId(), 700L);

            receive(mailId);

            assertThat(FakeParticipants.port(receiver).events())
                    .containsExactly(MailRefused.of(MailAction.RECEIVE, MailRefusal.SENDER_ADENA_LIMIT));
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(world.items.pending()).isFalse();
        }

        @Test
        void handle_shouldRefuseSenderAdenaLimit_whenTheOfflineSenderCannotHoldThePayment() {
            final UUID sender = world.character("Sender");
            world.give(sender, FakeItems.ADENA, FakeItems.ADENA_LIMIT - 100);
            world.give(receiver, FakeItems.ADENA, 5_000);
            final UUID mailId = attachedMail(sender, 700L);

            receive(mailId);

            assertThat(FakeParticipants.port(receiver).events())
                    .containsExactly(MailRefused.of(MailAction.RECEIVE, MailRefusal.SENDER_ADENA_LIMIT));
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(world.count(Holder.inventory(receiver.characterId()), FakeItems.ADENA))
                    .isEqualTo(5_000);
            assertThat(world.items.pending()).isFalse();
            assertThat(world.participants.inFlight(receiver.characterId())).isZero();
        }

        @Test
        void handle_shouldRefuseNotEnoughAdena_whenTheCodIsMoreThanTheReceiverHas() {
            world.give(receiver, FakeItems.ADENA, 100);
            final UUID mailId = attachedMail(world.character("Sender"), 700L);

            receive(mailId);

            assertThat(FakeParticipants.port(receiver).events())
                    .containsExactly(MailRefused.of(MailAction.RECEIVE, MailRefusal.NOT_ENOUGH_ADENA));
        }

        @Test
        void handle_shouldRefuse_whenOutsideAPeaceZone() {
            final UUID mailId = attachedMail(world.character("Sender"), null);
            world.participants.state(
                    receiver.characterHandle(),
                    ParticipantState.builder().tradeAllowed(true).build());

            receive(mailId);

            assertThat(FakeParticipants.port(receiver).events())
                    .containsExactly(MailRefused.of(MailAction.RECEIVE, MailRefusal.NOT_IN_PEACE_ZONE));
        }

        @Test
        void handle_shouldDoNothing_whenTheMailIsNotTheReceivers() {
            final UUID other = world.character("Other");
            final UUID mailId = world.insert(world.mail(world.character("Sender"), other)
                    .attachments(AttachmentState.ATTACHED)
                    .build());
            world.attach(mailId, SWORD, 1);

            receive(mailId);

            assertThat(FakeParticipants.port(receiver).events()).isEmpty();
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
        }
    }
}
