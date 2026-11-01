/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.MailType;
import app.l2nx.engine.mail.ParticipantState;
import app.l2nx.engine.mail.fixture.FakeItems;
import app.l2nx.engine.mail.fixture.FakeParticipants;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailChange;
import app.l2nx.engine.mail.view.MailChangeType;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.MailStateChanged;
import app.l2nx.engine.mail.view.MailSummary;
import app.l2nx.engine.mail.view.MailView;
import app.l2nx.engine.mail.view.MailableItems;
import app.l2nx.engine.mail.view.ReceivedMailList;
import app.l2nx.engine.mail.view.ReceivedMailOpened;
import app.l2nx.engine.mail.view.SentMailList;
import app.l2nx.engine.mail.view.SentMailOpened;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MailboxHandlersTest {
    static final int SWORD = 1000;

    private final MailWorld world = new MailWorld(MailSettings.defaults());
    private final MailActor sender = world.online("Sender");
    private final MailActor receiver = world.online("Receiver");

    private List<ViewEvent> events(MailActor actor) {
        return FakeParticipants.port(actor).events();
    }

    @Nested
    class Lists {
        @Test
        void handle_shouldListTheVisibleReceivedMailNewestFirst_whenAsked() {
            final UUID older = world.insert(world.mail(sender.characterId(), receiver.characterId())
                    .attachments(AttachmentState.ATTACHED)
                    .codAdena(500L)
                    .build());
            world.clock.advanceWall(Duration.ofMinutes(1));
            final UUID newer = world.insert(world.mail(null, receiver.characterId())
                    .type(MailType.SYSTEM)
                    .build());
            final UUID hidden = world.insert(
                    world.mail(sender.characterId(), receiver.characterId()).build());
            world.mails.hideReceived(List.of(hidden), receiver.characterId(), MailWorld.START);

            MailHandlers.listReceived(world).handle(receiver, new ListReceivedMail());

            assertThat(events(receiver))
                    .singleElement()
                    .isInstanceOfSatisfying(
                            ReceivedMailList.class,
                            list -> assertThat(list.mails())
                                    .extracting(
                                            MailSummary::mailId,
                                            MailSummary::counterpartName,
                                            MailSummary::codPending,
                                            MailSummary::attachmentsTakeable,
                                            MailSummary::returnable)
                                    .containsExactly(
                                            tuple(newer, null, false, false, false),
                                            tuple(older, "Sender", true, true, true)));
        }

        @Test
        void handle_shouldListTheSentMailWithTheReceiver_whenAsked() {
            final UUID mailId = world.insert(
                    world.mail(sender.characterId(), receiver.characterId()).build());

            MailHandlers.listSent(world).handle(sender, new ListSentMail());

            assertThat(events(sender))
                    .singleElement()
                    .isInstanceOfSatisfying(
                            SentMailList.class,
                            list -> assertThat(list.mails()).singleElement().satisfies(summary -> {
                                assertThat(summary.mailId()).isEqualTo(mailId);
                                assertThat(summary.counterpartName()).isEqualTo("Receiver");
                                assertThat(summary.unread()).isTrue();
                            }));
        }

        @Test
        void handle_shouldListAtMostTheMailboxLimit_whenTheBoxHoldsMore() {
            final MailWorld small = new MailWorld(
                    MailSettings.defaults().toBuilder().mailboxLimit(2).build());
            final MailActor owner = small.online("Owner");
            for (int i = 0; i < 3; i++) {
                small.insert(small.mail(null, owner.characterId())
                        .type(MailType.SYSTEM)
                        .build());
            }

            MailHandlers.listReceived(small).handle(owner, new ListReceivedMail());

            assertThat(FakeParticipants.port(owner).events())
                    .singleElement()
                    .isInstanceOfSatisfying(
                            ReceivedMailList.class,
                            list -> assertThat(list.mails()).hasSize(2));
        }

        @Test
        void handle_shouldListTheMailsWithAttachmentsFirst_whenTheBoxHoldsMore() {
            final MailWorld small = new MailWorld(
                    MailSettings.defaults().toBuilder().mailboxLimit(2).build());
            final MailActor owner = small.online("Owner");
            final UUID attached = small.insert(small.mail(null, owner.characterId())
                    .type(MailType.SYSTEM)
                    .attachments(AttachmentState.ATTACHED)
                    .sentAt(MailWorld.START.minus(Duration.ofHours(2)))
                    .build());
            final UUID older = small.insert(small.mail(null, owner.characterId())
                    .type(MailType.SYSTEM)
                    .sentAt(MailWorld.START.minus(Duration.ofHours(1)))
                    .build());
            final UUID newest = small.insert(
                    small.mail(null, owner.characterId()).type(MailType.SYSTEM).build());

            MailHandlers.listReceived(small).handle(owner, new ListReceivedMail());

            assertThat(FakeParticipants.port(owner).events())
                    .singleElement()
                    .isInstanceOfSatisfying(
                            ReceivedMailList.class,
                            list -> assertThat(list.mails())
                                    .extracting(MailSummary::mailId)
                                    .containsExactly(newest, attached)
                                    .doesNotContain(older));
        }

        @Test
        void handle_shouldRefuse_whenThePostboxIsOff() {
            final MailWorld off = new MailWorld(
                    MailSettings.defaults().toBuilder().enabled(false).build());
            final MailActor owner = off.online("Owner");

            MailHandlers.listReceived(off).handle(owner, new ListReceivedMail());

            assertThat(FakeParticipants.port(owner).events())
                    .containsExactly(MailRefused.of(MailAction.READ, MailRefusal.DISABLED));
        }
    }

    @Nested
    class Open {
        @Test
        void handle_shouldMarkReadOnce_whenAnUnreadMailIsOpened() {
            final UUID mailId = world.insert(world.mail(sender.characterId(), receiver.characterId())
                    .attachments(AttachmentState.ATTACHED)
                    .codAdena(500L)
                    .body("Body")
                    .build());
            world.attach(mailId, SWORD, 1);

            MailHandlers.openReceived(world).handle(receiver, new OpenReceivedMail(mailId));
            MailHandlers.openReceived(world).handle(receiver, new OpenReceivedMail(mailId));

            assertThat(events(receiver)).hasSize(3);
            assertThat(events(receiver).get(0)).isInstanceOfSatisfying(ReceivedMailOpened.class, opened -> {
                final MailView view = opened.mail();
                assertThat(view.unread()).isFalse();
                assertThat(view.codAdenaPending()).isEqualTo(500);
                assertThat(view.body()).isEqualTo("Body");
                assertThat(view.snapshots()).hasSize(1);
            });
            assertThat(events(receiver).get(1))
                    .isEqualTo(MailStateChanged.of(MailBox.RECEIVED, mailId, MailChangeType.READ));
            assertThat(events(receiver).get(2)).isInstanceOf(ReceivedMailOpened.class);
            assertThat(world.row(mailId).readAt()).isEqualTo(MailWorld.START);
        }

        @Test
        void handle_shouldShowTheSnapshot_whenTheAttachmentsWereTaken() {
            final UUID mailId = world.insert(world.mail(sender.characterId(), receiver.characterId())
                    .attachments(AttachmentState.RECEIVED)
                    .build());
            world.attach(mailId, SWORD, 1);
            world.store.itemsInMail(mailId).forEach(row -> world.store.deleteItem(row.itemId()));

            MailHandlers.openSent(world).handle(sender, new OpenSentMail(mailId));

            assertThat(events(sender)).singleElement().isInstanceOfSatisfying(SentMailOpened.class, opened -> {
                assertThat(opened.mail().attachments()).isEqualTo(AttachmentState.RECEIVED);
                assertThat(opened.mail().snapshots()).hasSize(1);
                assertThat(opened.mail().unread()).isTrue();
                assertThat(opened.mail().codAdenaPending()).isNull();
            });
        }

        @Test
        void handle_shouldShowNothing_whenTheMailIsSomeoneElses() {
            final UUID mailId = world.insert(
                    world.mail(sender.characterId(), world.character("Other")).build());

            MailHandlers.openReceived(world).handle(receiver, new OpenReceivedMail(mailId));

            assertThat(events(receiver)).isEmpty();
        }
    }

    @Nested
    class Delete {
        @Test
        void handle_shouldHideSettledMailAndRefuseTheRest_whenSomeStillHoldAttachments() {
            final UUID settled = world.insert(
                    world.mail(sender.characterId(), receiver.characterId()).build());
            final UUID attached = world.insert(world.mail(sender.characterId(), receiver.characterId())
                    .attachments(AttachmentState.ATTACHED)
                    .build());

            MailHandlers.deleteReceived(world).handle(receiver, new DeleteReceivedMail(List.of(settled, attached)));

            assertThat(events(receiver))
                    .containsExactly(
                            new MailStateChanged(
                                    MailBox.RECEIVED, List.of(new MailChange(settled, MailChangeType.DELETED))),
                            MailRefused.of(MailAction.DELETE, MailRefusal.HAS_ATTACHMENTS));
            assertThat(world.row(settled).receiverDeletedAt()).isEqualTo(MailWorld.START);
            assertThat(world.row(settled).senderDeletedAt()).isNull();
            assertThat(world.row(attached).receiverDeletedAt()).isNull();
        }

        @Test
        void handle_shouldHideFromTheSentBoxOnly_whenTheSenderDeletes() {
            final UUID mailId = world.insert(
                    world.mail(sender.characterId(), receiver.characterId()).build());

            MailHandlers.deleteSent(world).handle(sender, new DeleteSentMail(List.of(mailId)));

            assertThat(events(sender))
                    .containsExactly(MailStateChanged.of(MailBox.SENT, mailId, MailChangeType.DELETED));
            assertThat(world.row(mailId).senderDeletedAt()).isEqualTo(MailWorld.START);
            assertThat(world.mails.findReceived(mailId, receiver.characterId())).isPresent();
        }
    }

    @Nested
    class Mailable {
        @Test
        void handle_shouldListTheMailableItems_whenInAPeaceZone() {
            final FakeItems.Item sword = world.give(sender, SWORD, 1);

            MailHandlers.listMailable(world).handle(sender, new ListMailableItems());

            assertThat(events(sender)).containsExactly(new MailableItems(List.of(sword.handle())));
        }

        @Test
        void handle_shouldRefuse_whenOutsideAPeaceZone() {
            world.participants.state(
                    sender.characterHandle(),
                    ParticipantState.builder().tradeAllowed(true).build());

            MailHandlers.listMailable(world).handle(sender, new ListMailableItems());

            assertThat(events(sender)).containsExactly(MailRefused.of(MailAction.SEND, MailRefusal.NOT_IN_PEACE_ZONE));
        }
    }
}
