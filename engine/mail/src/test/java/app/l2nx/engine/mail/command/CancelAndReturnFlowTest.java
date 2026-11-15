/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.engine.core.persistence.value.Holder;
import app.l2nx.engine.core.persistence.value.ValueTransactionSubmitter;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.CommittedItemRow;
import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.MailType;
import app.l2nx.engine.mail.fixture.FakeItems;
import app.l2nx.engine.mail.fixture.FakeParticipants;
import app.l2nx.engine.mail.fixture.LosingTransactions;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.fixture.TestItemStore;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.engine.mail.view.MailArrived;
import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailChangeType;
import app.l2nx.engine.mail.view.MailDone;
import app.l2nx.engine.mail.view.MailNotice;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.MailStateChanged;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CancelAndReturnFlowTest {
    static final int ARROW = 17;
    static final int SWORD = 1000;

    @AutoClose
    private final MailWorld world = new MailWorld(MailSettings.defaults());

    private final MailActor sender = world.online("Sender");
    private final MailActor receiver = world.online("Receiver");

    private UUID attachedMail(MailType type) {
        final UUID mailId =
                world.insert(world.mail(type == MailType.PLAYER ? sender.characterId() : null, receiver.characterId())
                        .type(type)
                        .attachments(AttachmentState.ATTACHED)
                        .codAdena(type == MailType.PLAYER ? 300L : null)
                        .build());
        world.attach(mailId, SWORD, 1);
        world.attach(mailId, ARROW, 20);
        return mailId;
    }

    @Nested
    class Cancel {
        @Test
        void handle_shouldGiveTheItemsBackAndHideTheMail_whenTheyAreStillInIt() {
            final FakeItems.Item arrows = world.give(sender, ARROW, 5);
            final UUID mailId = attachedMail(MailType.PLAYER);

            MailHandlers.cancel(world).handle(sender, new CancelMail(mailId));

            final MailRow mail = world.row(mailId);
            assertThat(mail.attachments()).isEqualTo(AttachmentState.CANCELLED);
            assertThat(mail.receiverDeletedAt()).isEqualTo(MailWorld.START);
            assertThat(mail.senderDeletedAt()).isNull();
            final Holder inventory = Holder.inventory(sender.characterId());
            assertThat(world.count(inventory, SWORD)).isOne();
            assertThat(world.store.itemsOf(inventory))
                    .filteredOn(row -> row.itemTemplateId() == ARROW)
                    .singleElement()
                    .satisfies(row -> {
                        assertThat(row.itemId()).isEqualTo(arrows.id());
                        assertThat(row.count()).isEqualTo(25);
                    });
            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(
                            MailStateChanged.of(MailBox.SENT, mailId, MailChangeType.ATTACHMENTS_TAKEN),
                            MailDone.of(MailNotice.CANCELLED));
            assertThat(FakeParticipants.port(receiver).events())
                    .containsExactly(
                            MailStateChanged.of(MailBox.RECEIVED, mailId, MailChangeType.DELETED),
                            MailDone.of(MailNotice.CANCELLED_BY_SENDER, "Sender"));
            assertThat(world.mails.findReceived(mailId, receiver.characterId())).isEmpty();
            assertThat(world.participants.inFlight(sender.characterId())).isZero();
        }

        @ParameterizedTest(name = "result lost as {0}")
        @EnumSource(LosingTransactions.Loss.class)
        void handle_shouldApplyTheRowsTheLedgerNames_whenTheResultIsLost(LosingTransactions.Loss loss) {
            final FakeItems.Item arrows = world.give(sender, ARROW, 5);
            final UUID mailId = attachedMail(MailType.PLAYER);

            MailHandlers.cancel(
                            world,
                            new ValueTransactionSubmitter(
                                    new LosingTransactions(world.transactions, loss), world.scheduler))
                    .handle(sender, new CancelMail(mailId));

            assertThat(world.items.appliedIncoming())
                    .singleElement()
                    .satisfies(rows -> assertThat(rows)
                            .extracting(
                                    row -> ((TestItemStore.TestRow) row).itemTemplateId(),
                                    row -> ((TestItemStore.TestRow) row).count())
                            .containsExactlyInAnyOrder(tuple(SWORD, 1L), tuple(ARROW, 25L)));
            assertThat(world.items.appliedIncoming().getFirst())
                    .extracting(CommittedItemRow::itemId)
                    .contains(arrows.id());
            assertThat(world.items.pending()).isFalse();
            assertThat(world.participants.inFlight(sender.characterId())).isZero();
        }

        @Test
        void handle_shouldRefuseAlreadyReceived_whenTheReceiverTookThem() {
            world.give(receiver, FakeItems.ADENA, 1_000);
            final UUID mailId = attachedMail(MailType.PLAYER);
            MailHandlers.receive(world).handle(receiver, new ReceiveAttachments(mailId));

            MailHandlers.cancel(world).handle(sender, new CancelMail(mailId));

            assertThat(FakeParticipants.port(sender).events())
                    .endsWith(MailRefused.of(MailAction.CANCEL, MailRefusal.ALREADY_RECEIVED));
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.RECEIVED);
        }
    }

    @Nested
    class Return {
        @Test
        void handle_shouldSendANewMailBack_whenTheReceiverReturnsIt() {
            final UUID mailId = attachedMail(MailType.PLAYER);
            world.clock.advanceWall(Duration.ofHours(1));

            MailHandlers.returnMail(world).handle(receiver, new ReturnMail(mailId));

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.RETURNED);
            final MailRow returned = world.receivedBy(sender.characterId()).getFirst();
            assertThat(returned.returnedFromId()).isEqualTo(mailId);
            assertThat(returned.type()).isEqualTo(MailType.PLAYER);
            assertThat(returned.attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(returned.codAdena()).isNull();
            assertThat(returned.senderId()).isEqualTo(receiver.characterId());
            assertThat(returned.senderDeletedAt()).isNotNull();
            assertThat(returned.expiresAt())
                    .isEqualTo(MailWorld.START.plus(Duration.ofHours(1)).plus(Duration.ofHours(360)));
            assertThat(world.attachments.findByMail(returned.id()))
                    .extracting(AttachmentSnapshot::itemTemplateId)
                    .containsExactly(SWORD, ARROW);
            assertThat(world.attachments.findByMail(mailId)).hasSize(2);
            assertThat(world.store.itemsInMail(returned.id())).hasSize(2);
            assertThat(world.store.itemsInMail(mailId)).isEmpty();
            assertThat(world.mails.listSent(receiver.characterId(), 10)).isEmpty();
            assertThat(FakeParticipants.port(receiver).events())
                    .containsExactly(
                            MailStateChanged.of(MailBox.RECEIVED, mailId, MailChangeType.ATTACHMENTS_TAKEN),
                            MailDone.of(MailNotice.RETURNED));
            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(MailArrived.FRESH, MailDone.of(MailNotice.RETURNED_TO_YOU, "Receiver"));
        }

        @Test
        void handle_shouldRefuse_whenTheMailIsSystemMail() {
            final UUID mailId = attachedMail(MailType.SYSTEM);

            MailHandlers.returnMail(world).handle(receiver, new ReturnMail(mailId));

            assertThat(FakeParticipants.port(receiver).events())
                    .containsExactly(MailRefused.of(MailAction.RETURN, MailRefusal.NOT_RETURNABLE));
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
        }

        @Test
        void handle_shouldRefuse_whenTheMailIsAReturnedOne() {
            final UUID mailId = attachedMail(MailType.PLAYER);
            MailHandlers.returnMail(world).handle(receiver, new ReturnMail(mailId));
            final UUID returned =
                    world.receivedBy(sender.characterId()).getFirst().id();

            MailHandlers.returnMail(world).handle(sender, new ReturnMail(returned));

            assertThat(FakeParticipants.port(sender).events())
                    .endsWith(MailRefused.of(MailAction.RETURN, MailRefusal.NOT_RETURNABLE));
        }
    }
}
