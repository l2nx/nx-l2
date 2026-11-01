/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.engine.core.persistence.value.Holder;
import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.ItemCount;
import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.fixture.FakeItems;
import app.l2nx.engine.mail.fixture.FakeParticipants;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.engine.mail.view.MailArrived;
import app.l2nx.engine.mail.view.MailDone;
import app.l2nx.engine.mail.view.MailNotice;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.MailSent;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SendMailFlowTest {
    static final int ARROW = 17;
    static final int SWORD = 1000;

    private final MailWorld world = new MailWorld(MailSettings.defaults());
    private final MailActor sender = world.online("Sender");

    private SendMail mail(String receiverName, List<ItemCount> items, Long codAdena) {
        return SendMail.builder()
                .receiverName(receiverName)
                .subject("Hello")
                .body("  ")
                .items(items)
                .codAdena(codAdena)
                .build();
    }

    private MailRow onlyMailOf(UUID receiverId) {
        assertThat(world.receivedBy(receiverId)).hasSize(1);
        return world.receivedBy(receiverId).getFirst();
    }

    @Nested
    class Committed {
        @Test
        void handle_shouldMoveItemsAndTakeTheFee_whenTheMailIsSent() {
            final MailActor receiver = world.online("Receiver");
            world.give(sender, FakeItems.ADENA, 10_000);
            final FakeItems.Item sword = world.give(sender, SWORD, 1);
            final FakeItems.Item arrows = world.give(sender, ARROW, 50);

            MailHandlers.send(world)
                    .handle(
                            sender,
                            mail(
                                    "Receiver",
                                    List.of(new ItemCount(sword.handle(), 1), new ItemCount(arrows.handle(), 20)),
                                    null));

            final MailRow mail = onlyMailOf(receiver.characterId());
            assertThat(mail.attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(mail.body()).isNull();
            assertThat(mail.expiresAt()).isEqualTo(MailWorld.START.plus(Duration.ofHours(360)));
            assertThat(world.attachments.findByMail(mail.id()))
                    .extracting(AttachmentSnapshot::slot, AttachmentSnapshot::itemTemplateId, AttachmentSnapshot::count)
                    .containsExactly(tuple(0, SWORD, 1L), tuple(1, ARROW, 20L));
            assertThat(world.store.itemsInMail(mail.id()))
                    .extracting(row -> row.itemTemplateId(), row -> row.count())
                    .containsExactlyInAnyOrder(tuple(SWORD, 1L), tuple(ARROW, 20L));
            final Holder inventory = Holder.inventory(sender.characterId());
            assertThat(world.count(inventory, FakeItems.ADENA)).isEqualTo(10_000 - 2_100);
            assertThat(world.count(inventory, ARROW)).isEqualTo(30);
            assertThat(world.count(inventory, SWORD)).isZero();
            assertThat(world.ledger(HolderType.INVENTORY, sender.characterId(), FakeItems.ADENA))
                    .isEqualTo(-2_100);
            assertThat(world.ledger(HolderType.MAIL, mail.id(), ARROW)).isEqualTo(20);
            assertThat(FakeParticipants.port(sender).events())
                    .containsExactly(MailSent.INSTANCE, MailDone.of(MailNotice.SENT));
            assertThat(FakeParticipants.port(receiver).events()).containsExactly(MailArrived.FRESH);
            assertThat(world.items.pending()).isFalse();
            assertThat(world.participants.inFlight(sender.characterId())).isZero();
        }

        @Test
        void handle_shouldTakeTheFeeFromWhatAttachedAdenaLeaves_whenAdenaIsAttached() {
            final UUID receiver = world.character("Receiver");
            final FakeItems.Item adena = world.give(sender, FakeItems.ADENA, 10_000);

            MailHandlers.send(world)
                    .handle(sender, mail("Receiver", List.of(new ItemCount(adena.handle(), 5_000)), null));

            final MailRow mail = onlyMailOf(receiver);
            assertThat(world.count(Holder.inventory(sender.characterId()), FakeItems.ADENA))
                    .isEqualTo(10_000 - 5_000 - 1_100);
            assertThat(world.store.itemsInMail(mail.id()))
                    .singleElement()
                    .satisfies(row -> assertThat(row.count()).isEqualTo(5_000));
        }

        @Test
        void handle_shouldExpireSooner_whenTheMailHasCashOnDelivery() {
            final UUID receiver = world.character("Receiver");
            world.give(sender, FakeItems.ADENA, 10_000);
            final FakeItems.Item sword = world.give(sender, SWORD, 1);

            MailHandlers.send(world).handle(sender, mail("Receiver", List.of(new ItemCount(sword.handle(), 1)), 700L));

            final MailRow mail = onlyMailOf(receiver);
            assertThat(mail.codAdena()).isEqualTo(700);
            assertThat(mail.expiresAt()).isEqualTo(MailWorld.START.plus(Duration.ofHours(12)));
        }

        @Test
        void handle_shouldSendWithoutAttachments_whenNoneAreGiven() {
            final UUID receiver = world.character("Receiver");
            world.give(sender, FakeItems.ADENA, 100);

            MailHandlers.send(world).handle(sender, mail("Receiver", List.of(), null));

            assertThat(onlyMailOf(receiver).attachments()).isEqualTo(AttachmentState.NONE);
            assertThat(world.count(Holder.inventory(sender.characterId()), FakeItems.ADENA))
                    .isZero();
        }
    }

    @Nested
    class Refused {
        @ParameterizedTest(name = "{0}")
        @MethodSource("refusedInTheTransaction")
        void handle_shouldRefuseAndMoveNothing_whenTheReceiverCannotGetIt(
                String name, BiConsumer<MailWorld, UUID> setup, MailRefused expected) {
            final UUID receiver = world.character("Receiver");
            setup.accept(world, receiver);
            world.give(sender, FakeItems.ADENA, 10_000);
            final FakeItems.Item sword = world.give(sender, SWORD, 1);

            MailHandlers.send(world)
                    .handle(
                            sender,
                            mail(
                                    name.equals("self") ? "Sender" : "Receiver",
                                    List.of(new ItemCount(sword.handle(), 1)),
                                    null));

            assertThat(FakeParticipants.port(sender).events()).containsExactly(expected);
            assertThat(world.count(Holder.inventory(sender.characterId()), FakeItems.ADENA))
                    .isEqualTo(10_000);
            assertThat(world.count(Holder.inventory(sender.characterId()), SWORD))
                    .isOne();
            assertThat(world.items.released()).hasSize(1);
            assertThat(world.participants.inFlight(sender.characterId())).isZero();
        }

        static Stream<Arguments> refusedInTheTransaction() {
            return Stream.of(
                    Arguments.of(
                            "unknown name",
                            (BiConsumer<MailWorld, UUID>) (world, receiver) -> world.jdbc
                                    .sql("UPDATE characters SET name = 'Gone' WHERE id = ?")
                                    .param(receiver)
                                    .update(),
                            MailRefused.of(MailAction.SEND, MailRefusal.RECEIVER_NOT_FOUND)),
                    Arguments.of(
                            "self",
                            (BiConsumer<MailWorld, UUID>) (world, receiver) -> {},
                            MailRefused.of(MailAction.SEND, MailRefusal.RECEIVER_SELF)),
                    Arguments.of(
                            "a GM",
                            (BiConsumer<MailWorld, UUID>) (world, receiver) -> world.gm(receiver),
                            new MailRefused(MailAction.SEND, MailRefusal.RECEIVER_GM_ONLY, "Receiver")),
                    Arguments.of(
                            "blocked",
                            (BiConsumer<MailWorld, UUID>) (world, receiver) -> world.store.block(
                                    receiver,
                                    world.store
                                            .findRecipient("Sender")
                                            .orElseThrow()
                                            .id()),
                            new MailRefused(MailAction.SEND, MailRefusal.RECEIVER_BLOCKED, "Receiver")));
        }

        @Test
        void handle_shouldRefuse_whenTheReceiversBoxIsFull() {
            final MailWorld small = new MailWorld(
                    MailSettings.defaults().toBuilder().mailboxLimit(1).build());
            final MailActor from = small.online("Sender");
            final UUID receiver = small.character("Receiver");
            small.insert(small.mail(small.character("Other"), receiver).build());
            small.give(from, FakeItems.ADENA, 10_000);

            MailHandlers.send(small).handle(from, mail("Receiver", List.of(), null));

            assertThat(FakeParticipants.port(from).events())
                    .containsExactly(MailRefused.of(MailAction.SEND, MailRefusal.RECEIVER_MAILBOX_FULL));
        }

        @Test
        void handle_shouldRefuse_whenTheSendersBoxIsFull() {
            final MailWorld small = new MailWorld(
                    MailSettings.defaults().toBuilder().mailboxLimit(1).build());
            final MailActor from = small.online("Sender");
            small.character("Receiver");
            small.insert(
                    small.mail(from.characterId(), small.character("Other")).build());
            small.give(from, FakeItems.ADENA, 10_000);

            MailHandlers.send(small).handle(from, mail("Receiver", List.of(), null));

            assertThat(FakeParticipants.port(from).events())
                    .containsExactly(MailRefused.of(MailAction.SEND, MailRefusal.SENDER_MAILBOX_FULL));
        }
    }
}
