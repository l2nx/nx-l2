/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.system;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.persistence.value.Holder;
import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.MailType;
import app.l2nx.engine.mail.fixture.FakeItems;
import app.l2nx.engine.mail.fixture.FakeParticipants;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailChangeType;
import app.l2nx.engine.mail.view.MailDone;
import app.l2nx.engine.mail.view.MailNotice;
import app.l2nx.engine.mail.view.MailStateChanged;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MailExpiryTest {
    static final int ARROW = 17;
    static final int SWORD = 1000;

    @AutoClose
    private final MailWorld world = new MailWorld(MailSettings.defaults());

    private final MailExpiry expiry = new MailExpiry(
            world.settings,
            null,
            world.clock,
            world.mails,
            world.attachments,
            world.participants,
            world.items,
            world.store,
            world.queries,
            world.submitter,
            world.writer);

    private UUID attachedMail(UUID senderId, UUID receiverId, MailType type) {
        final UUID mailId = world.insert(world.mail(senderId, receiverId)
                .type(type)
                .attachments(AttachmentState.ATTACHED)
                .build());
        world.attach(mailId, SWORD, 1);
        world.attach(mailId, ARROW, 20);
        return mailId;
    }

    @Nested
    class Poll {
        @Test
        void poll_shouldMoveTheAttachmentsToTheSendersWarehouse_whenTheMailIsDue() {
            final UUID sender = world.character("Sender");
            final UUID receiver = world.character("Receiver");
            final UUID stored = UUID.randomUUID();
            world.store.insert(stored, sender, HolderType.WAREHOUSE.name(), null, ARROW, 5);
            final UUID mailId = attachedMail(sender, receiver, MailType.PLAYER);
            world.clock.advanceWall(Duration.ofHours(360));

            expiry.poll();

            final MailRow mail = world.row(mailId);
            assertThat(mail.attachments()).isEqualTo(AttachmentState.EXPIRED);
            assertThat(mail.expiredAt()).isEqualTo(MailWorld.START.plus(Duration.ofHours(360)));
            final Holder warehouse = Holder.warehouse(sender);
            assertThat(world.count(warehouse, SWORD)).isOne();
            assertThat(world.store.itemsOf(warehouse))
                    .filteredOn(row -> row.itemTemplateId() == ARROW)
                    .singleElement()
                    .satisfies(row -> {
                        assertThat(row.itemId()).isEqualTo(stored);
                        assertThat(row.count()).isEqualTo(25);
                    });
            assertThat(world.ledger(HolderType.WAREHOUSE, sender, ARROW)).isEqualTo(20);
            assertThat(world.mails.findReceived(mailId, receiver)).isEmpty();
            assertThat(world.participants.inFlight(sender)).isZero();
        }

        @Test
        void poll_shouldLeaveTheMailDue_whenTheOwnerIsLoading() {
            final UUID sender = world.character("Sender");
            final UUID mailId = attachedMail(sender, world.character("Receiver"), MailType.PLAYER);
            world.clock.advanceWall(Duration.ofHours(360));
            world.participants.startLoading(sender);

            expiry.poll();

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(world.participants.inFlight(sender)).isZero();
            world.participants.loaded(sender);
            expiry.poll();
            assertThat(world.row(mailId).attachments())
                    .as("the next poll expires it")
                    .isEqualTo(AttachmentState.EXPIRED);
        }

        @Test
        void poll_shouldLeaveTheMail_whenItIsNotDueYet() {
            final UUID mailId = attachedMail(world.character("Sender"), world.character("Receiver"), MailType.PLAYER);
            world.clock.advanceWall(Duration.ofHours(359));

            expiry.poll();

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(world.row(mailId).expiredAt()).isNull();
        }

        @Test
        void poll_shouldGiveSystemMailToTheReceiversWarehouse_whenItIsDue() {
            final UUID receiver = world.character("Receiver");
            attachedMail(null, receiver, MailType.BIRTHDAY);
            world.clock.advanceWall(Duration.ofHours(360));

            expiry.poll();

            assertThat(world.count(Holder.warehouse(receiver), ARROW)).isEqualTo(20);
        }

        @Test
        void poll_shouldGiveTheReceiverTheItems_whenTheSenderWasDeleted() {
            final UUID receiver = world.character("Receiver");
            attachedMail(null, receiver, MailType.PLAYER);
            world.clock.advanceWall(Duration.ofHours(360));

            expiry.poll();

            assertThat(world.count(Holder.warehouse(receiver), SWORD)).isOne();
        }

        @Test
        void poll_shouldDestroyTheAttachments_whenNobodyIsLeftToGetThem() {
            final UUID mailId = attachedMail(null, null, MailType.SYSTEM);
            world.clock.advanceWall(Duration.ofHours(360));

            expiry.poll();

            final MailRow mail = world.row(mailId);
            assertThat(mail.attachments()).isEqualTo(AttachmentState.DESTROYED);
            assertThat(mail.expiredAt()).isNotNull();
            assertThat(world.store.itemsInMail(mailId)).isEmpty();
            assertThat(world.ledger(HolderType.MAIL, mailId, ARROW)).isEqualTo(-20);
        }

        @Test
        void poll_shouldExpireCodMailSooner_whenItHasCashOnDelivery() {
            final UUID sender = world.character("Sender");
            final UUID mailId = world.insert(world.mail(sender, world.character("Receiver"))
                    .attachments(AttachmentState.ATTACHED)
                    .codAdena(500L)
                    .expiresAt(MailWorld.START.plus(world.settings.codExpiry()))
                    .build());
            world.attach(mailId, SWORD, 1);
            world.clock.advanceWall(Duration.ofHours(12));

            expiry.poll();

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.EXPIRED);
            assertThat(world.count(Holder.warehouse(sender), SWORD)).isOne();
        }

        @Test
        void poll_shouldUpdateAnOnlineOwnerAndTellBothSides_whenTheyAreInTheWorld() {
            final MailActor sender = world.online("Sender");
            final MailActor receiver = world.online("Receiver");
            final UUID mailId = attachedMail(sender.characterId(), receiver.characterId(), MailType.PLAYER);
            world.clock.advanceWall(Duration.ofHours(360));

            expiry.poll();

            assertThat(world.items.appliedIncoming())
                    .singleElement()
                    .satisfies(rows -> assertThat(rows).hasSize(2));
            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(
                            MailStateChanged.of(MailBox.SENT, mailId, MailChangeType.DELETED),
                            MailDone.of(MailNotice.EXPIRED_RETURNED));
            assertThat(FakeParticipants.port(receiver).events())
                    .containsExactly(MailStateChanged.of(MailBox.RECEIVED, mailId, MailChangeType.DELETED));
        }

        @Test
        void poll_shouldMergeIntoTheOnlineOwnersWarehouseByAbsoluteCount_whenItsStackIsReserved() {
            final MailActor sender = world.online("Sender");
            final FakeItems.Item stack = world.store(sender, ARROW, 5);
            attachedMail(sender.characterId(), world.character("Receiver"), MailType.PLAYER);
            // Memory leads the row: the transaction writes what memory holds plus what arrives.
            world.jdbc
                    .sql("UPDATE items SET count = 3 WHERE id = ?")
                    .param(stack.id())
                    .update();
            world.clock.advanceWall(Duration.ofHours(360));

            expiry.poll();

            assertThat(world.store.itemsOf(Holder.warehouse(sender.characterId())))
                    .filteredOn(row -> row.itemTemplateId() == ARROW)
                    .singleElement()
                    .satisfies(row -> {
                        assertThat(row.itemId()).isEqualTo(stack.id());
                        assertThat(row.count()).isEqualTo(25);
                    });
            assertThat(world.items.appliedIncoming()).hasSize(1);
            assertThat(world.items.pending()).isFalse();
            assertThat(world.participants.inFlight(sender.characterId())).isZero();
        }

        @Test
        void poll_shouldLeaveTheMailDue_whenTheOnlineOwnersStackIsPendingElsewhere() {
            final MailActor sender = world.online("Sender");
            final FakeItems.Item stack = world.store(sender, ARROW, 5);
            final UUID mailId = attachedMail(sender.characterId(), world.character("Receiver"), MailType.PLAYER);
            world.items.reserveElsewhere(stack.id());
            world.clock.advanceWall(Duration.ofHours(360));

            expiry.poll();

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(world.transactionsOf("MAIL_EXPIRE")).isZero();
            assertThat(world.participants.inFlight(sender.characterId())).isZero();
        }

        @Test
        void poll_shouldGiveAReturnedMailBackToItsOriginalSender_andTellThem() {
            final MailActor original = world.online("Original");
            final UUID returner = world.character("Returner");
            final UUID first = world.insert(world.mail(original.characterId(), returner)
                    .attachments(AttachmentState.RETURNED)
                    .build());
            final UUID returned = world.insert(world.mail(returner, original.characterId())
                    .returnedFromId(first)
                    .attachments(AttachmentState.ATTACHED)
                    .senderDeletedAt(MailWorld.START)
                    .build());
            world.attach(returned, SWORD, 1);
            world.clock.advanceWall(Duration.ofHours(360));

            expiry.poll();

            assertThat(world.count(Holder.warehouse(original.characterId()), SWORD))
                    .isOne();
            assertThat(world.count(Holder.warehouse(returner), SWORD)).isZero();
            assertThat(FakeParticipants.port(original).events()).contains(MailDone.of(MailNotice.EXPIRED_RETURNED));
        }

        @Test
        void poll_shouldExpireOnTheNextPoll_whenTheReservationFailed() {
            final MailActor sender = world.online("Sender");
            final UUID mailId = attachedMail(sender.characterId(), world.character("Receiver"), MailType.PLAYER);
            world.clock.advanceWall(Duration.ofHours(360));
            world.items.failIncoming(new IllegalStateException("broken"));

            expiry.poll();
            final AttachmentState afterFailure = world.row(mailId).attachments();
            final int inFlight = world.participants.inFlight(sender.characterId());
            expiry.poll();

            assertThat(afterFailure).isEqualTo(AttachmentState.ATTACHED);
            assertThat(inFlight).isZero();
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.EXPIRED);
        }

        @Test
        void poll_shouldLeaveTheMailDue_whenTheWarehouseCannotHoldItsAdena() {
            final UUID sender = world.character("Sender");
            world.store.insert(
                    UUID.randomUUID(),
                    sender,
                    HolderType.WAREHOUSE.name(),
                    null,
                    FakeItems.ADENA,
                    FakeItems.ADENA_LIMIT - 10);
            final UUID mailId = world.insert(world.mail(sender, world.character("Receiver"))
                    .attachments(AttachmentState.ATTACHED)
                    .build());
            world.attach(mailId, FakeItems.ADENA, 100);
            world.clock.advanceWall(Duration.ofHours(360));

            expiry.poll();

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(world.participants.inFlight(sender)).isZero();
        }

        @Test
        void poll_shouldOnlyHide_whenTheMailHoldsNoAttachments() {
            final MailActor receiver = world.online("Receiver");
            final UUID settled = world.insert(world.mail(world.character("Sender"), receiver.characterId())
                    .attachments(AttachmentState.RECEIVED)
                    .build());
            world.clock.advanceWall(Duration.ofHours(361));

            expiry.poll();

            assertThat(world.row(settled).attachments()).isEqualTo(AttachmentState.RECEIVED);
            assertThat(world.row(settled).expiredAt()).isNotNull();
            assertThat(FakeParticipants.port(receiver).events())
                    .containsExactly(MailStateChanged.of(MailBox.RECEIVED, settled, MailChangeType.DELETED));
            assertThat(world.transactionsOf("MAIL_EXPIRE")).isZero();
        }

        @Test
        void poll_shouldExpireOnce_whenPolledAgain() {
            final UUID sender = world.character("Sender");
            attachedMail(sender, world.character("Receiver"), MailType.PLAYER);
            world.clock.advanceWall(Duration.ofHours(360));

            expiry.poll();
            expiry.poll();

            assertThat(world.count(Holder.warehouse(sender), ARROW)).isEqualTo(20);
            assertThat(world.transactionsOf("MAIL_EXPIRE")).isOne();
        }
    }
}
