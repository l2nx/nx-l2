/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.mail.fixture.FakeParticipants;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.fixture.TestItemStore;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.view.MailArrived;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DefaultMailServiceTest {
    static final int ARROW = 17;
    static final int SWORD = 1000;

    @AutoClose
    private final MailWorld world = new MailWorld(MailSettings.defaults());

    private final DefaultMailService service = new DefaultMailService(
            world.participants,
            world.store,
            world.queries,
            world.mails,
            world.submitter,
            world.writer,
            MailSettings.defaults());

    private SystemMail birthday(UUID receiverId, UUID key) {
        return SystemMail.builder()
                .receiverId(receiverId)
                .type(MailType.BIRTHDAY)
                .subject("Happy birthday")
                .items(List.of(new NewItem(SWORD, 1, 3)))
                .idempotencyKey(key)
                .build();
    }

    @Nested
    class SendSystem {
        @Test
        void sendSystem_shouldCreateTheItemsInTheMail_whenTheReceiverExists() {
            final MailActor receiver = world.online("Receiver");

            service.sendSystem(birthday(receiver.characterId(), EntityIds.next()));

            final MailRow mail = world.receivedBy(receiver.characterId()).getFirst();
            assertThat(mail.type()).isEqualTo(MailType.BIRTHDAY);
            assertThat(mail.senderId()).isNull();
            assertThat(mail.senderName()).isNull();
            assertThat(mail.receiverName()).isEqualTo("Receiver");
            assertThat(mail.attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(world.attachments.findByMail(mail.id())).singleElement().satisfies(snapshot -> {
                assertThat(snapshot.itemTemplateId()).isEqualTo(SWORD);
                assertThat(snapshot.enchantLevel()).isEqualTo(3);
            });
            assertThat(world.store.itemsInMail(mail.id())).hasSize(1);
            assertThat(FakeParticipants.port(receiver).events()).containsExactly(MailArrived.FRESH);
        }

        @Test
        void sendSystem_shouldSendOnce_whenTheKeyRepeats() {
            final UUID receiver = world.character("Receiver");
            final UUID key = EntityIds.next();

            service.sendSystem(birthday(receiver, key));
            service.sendSystem(birthday(receiver, key));

            assertThat(world.receivedBy(receiver)).hasSize(1);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"unknown template, 0, 1", "non-stackable count of two, " + SWORD + ", 2"})
        void sendSystem_shouldThrowBeforeSending_whenAnItemCannotBeCreated(
                String name, int itemTemplateId, long count) {
            final UUID receiver = world.character("Receiver");
            final SystemMail mail = birthday(receiver, EntityIds.next()).toBuilder()
                    .items(List.of(new NewItem(itemTemplateId, count, 0)))
                    .build();

            assertThatThrownBy(() -> service.sendSystem(mail)).isInstanceOf(IllegalArgumentException.class);
            assertThat(world.transactionsOf("MAIL_SEND")).isZero();
        }

        @Test
        void sendSystem_shouldSendNothing_whenTheReceiverDoesNotExist() {
            service.sendSystem(birthday(UUID.randomUUID(), EntityIds.next()));

            assertThat(world.transactionsOf("MAIL_SEND")).isZero();
        }
    }

    @Nested
    class HasAttached {
        @ParameterizedTest(name = "template {0}, attached {1}: {2}")
        @CsvSource({"1000, true, true", "17, true, false", "1000, false, false"})
        void hasAttached_shouldAnswer_whetherAMailStillHoldsTheItem(
                int itemTemplateId, boolean attached, boolean expected) {
            final UUID sender = world.character("Sender");
            final UUID mailId = world.insert(world.mail(sender, world.character("Receiver"))
                    .attachments(attached ? AttachmentState.ATTACHED : AttachmentState.RECEIVED)
                    .build());
            world.attach(mailId, SWORD, 1);
            final List<Boolean> answers = new ArrayList<>();

            service.hasAttached(sender, IntSet.of(itemTemplateId), Runnable::run, answers::add);

            assertThat(answers).containsExactly(expected);
        }
    }

    @Nested
    class CharacterDeleted {
        @Test
        void onCharacterDeleted_shouldReturnOrDestroyReceivedMailAndDropCod_whenTheCharacterGoes() {
            final MailActor sender = world.online("Sender");
            final UUID deleted = world.character("Deleted");
            final UUID received = world.insert(world.mail(sender.characterId(), deleted)
                    .attachments(AttachmentState.ATTACHED)
                    .build());
            world.attach(received, ARROW, 20);
            final UUID system = world.insert(world.mail(null, deleted)
                    .type(MailType.SYSTEM)
                    .attachments(AttachmentState.ATTACHED)
                    .build());
            world.attach(system, SWORD, 1);
            final UUID sent = world.insert(world.mail(deleted, sender.characterId())
                    .attachments(AttachmentState.ATTACHED)
                    .codAdena(900L)
                    .build());
            world.attach(sent, SWORD, 1);
            final List<Boolean> done = new ArrayList<>();

            service.onCharacterDeleted(deleted, EntityIds.next(), Runnable::run, done::add);

            assertThat(done).containsExactly(true);
            assertThat(world.row(received).attachments()).isEqualTo(AttachmentState.RETURNED);
            assertThat(world.row(system).attachments()).isEqualTo(AttachmentState.DESTROYED);
            assertThat(world.row(system).settledAt()).isEqualTo(MailWorld.START);
            assertThat(world.store.itemsInMail(system)).isEmpty();
            assertThat(world.ledger(HolderType.MAIL, system, SWORD)).isEqualTo(-1);
            assertThat(world.row(sent).codAdena()).isNull();
            assertThat(world.receivedBy(sender.characterId()))
                    .filteredOn(mail -> received.equals(mail.returnedFromId()))
                    .singleElement()
                    .satisfies(mail -> assertThat(world.store.itemsInMail(mail.id()))
                            .extracting(TestItemStore.TestRow::itemTemplateId)
                            .containsExactly(ARROW));
            assertThat(FakeParticipants.port(sender).events()).containsExactly(MailArrived.FRESH);
        }
    }

    @Nested
    class WorldEntered {
        @ParameterizedTest(name = "read {0}")
        @CsvSource({"false, 1", "true, 0"})
        void onWorldEntered_shouldTellAboutUnreadMail_whenThereIsSome(boolean read, int expected) {
            final MailActor receiver = world.online("Receiver");
            final UUID mailId = world.insert(world.mail(null, receiver.characterId())
                    .type(MailType.SYSTEM)
                    .build());
            if (read) {
                world.mails.markRead(mailId, receiver.characterId(), MailWorld.START);
            }

            service.onWorldEntered(receiver);

            assertThat(FakeParticipants.port(receiver).events())
                    .hasSize(expected)
                    .allMatch(MailArrived.WAITING::equals);
        }

        @Test
        void onWorldEntered_shouldTellNothing_whenMailIsDisabled() {
            final DefaultMailService disabled = new DefaultMailService(
                    world.participants,
                    world.store,
                    world.queries,
                    world.mails,
                    world.submitter,
                    world.writer,
                    MailSettings.defaults().toBuilder().enabled(false).build());
            final MailActor receiver = world.online("Receiver");
            world.insert(world.mail(null, receiver.characterId())
                    .type(MailType.SYSTEM)
                    .build());

            disabled.onWorldEntered(receiver);

            assertThat(FakeParticipants.port(receiver).events()).isEmpty();
        }
    }
}
