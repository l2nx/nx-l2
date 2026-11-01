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
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.MailType;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.fixture.TestItemStore;
import app.l2nx.engine.mail.repository.CharacterDeletedTransaction;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.repository.ReturnTransaction;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterDeletedTransactionTest {
    static final int ARROW = 17;
    static final int SWORD = 1000;

    private final MailWorld world = new MailWorld(MailSettings.defaults());
    private final UUID deleted = world.character("Deleted");
    private final UUID friend = world.character("Friend");
    private UUID fromFriend;
    private UUID fromSystem;
    private UUID returnedToDeleted;
    private UUID read;
    private UUID sentWithCod;

    @BeforeEach
    void storeMail() {
        fromFriend = attached(world.mail(friend, deleted));
        fromSystem = attached(world.mail(null, deleted).type(MailType.SYSTEM));
        returnedToDeleted = attached(world.mail(friend, deleted)
                .returnedFromId(world.insert(world.mail(deleted, friend)
                        .attachments(AttachmentState.RETURNED)
                        .build())));
        read = world.insert(world.mail(friend, deleted).build());
        sentWithCod = attached(world.mail(deleted, friend).codAdena(700L));
    }

    private UUID attached(MailRow.MailRowBuilder mail) {
        final UUID mailId =
                world.insert(mail.attachments(AttachmentState.ATTACHED).build());
        world.attach(mailId, SWORD, 1);
        world.attach(mailId, ARROW, 20);
        return mailId;
    }

    private CharacterDeletedTransaction deleteCharacter() {
        return CharacterDeletedTransaction.builder()
                .writer(world.writer)
                .idempotencyKey(EntityIds.next())
                .characterId(deleted)
                .build();
    }

    @Nested
    class Committed {
        @Test
        void execute_shouldReturnPlayerMailDestroyTheRestAndDropTheCod_whenTheCharacterGoes() {
            final ValueOutcome<List<ReturnTransaction.Returned>> outcome = world.run(deleteCharacter());

            assertThat(outcome).isInstanceOf(ValueOutcome.Committed.class);
            final List<ReturnTransaction.Returned> actual =
                    ((ValueOutcome.Committed<List<ReturnTransaction.Returned>>) outcome).result();
            assertThat(actual).singleElement().satisfies(returned -> {
                assertThat(returned.senderId()).isEqualTo(friend);
                assertThat(world.row(returned.mailId()).returnedFromId()).isEqualTo(fromFriend);
                assertThat(world.row(returned.mailId()).receiverId()).isEqualTo(friend);
                assertThat(world.store.itemsInMail(returned.mailId()))
                        .extracting(TestItemStore.TestRow::itemTemplateId, TestItemStore.TestRow::count)
                        .containsExactlyInAnyOrder(tuple(SWORD, 1L), tuple(ARROW, 20L));
            });
            assertThat(world.row(fromFriend).attachments()).isEqualTo(AttachmentState.RETURNED);
            assertThat(List.of(fromSystem, returnedToDeleted)).allSatisfy(mailId -> {
                assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.DESTROYED);
                assertThat(world.row(mailId).settledAt()).isEqualTo(MailWorld.START);
                assertThat(world.store.itemsInMail(mailId)).isEmpty();
                assertThat(world.ledger(HolderType.MAIL, mailId, ARROW)).isEqualTo(-20);
            });
            assertThat(world.row(read).attachments()).isEqualTo(AttachmentState.NONE);
            assertThat(world.row(sentWithCod).codAdena()).isNull();
            assertThat(world.row(sentWithCod).attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(world.store.itemsInMail(sentWithCod)).hasSize(2);
            assertThat(world.ledger(HolderType.MAIL, fromFriend, ARROW)).isEqualTo(-20);
        }
    }

    @Nested
    class Replayed {
        @Test
        void execute_shouldReplayWithoutSettlingAgain_whenTheKeyIsUsedAgain() {
            final CharacterDeletedTransaction deleteCharacter = deleteCharacter();
            assertThat(world.run(deleteCharacter)).isInstanceOf(ValueOutcome.Committed.class);

            final ValueOutcome<List<ReturnTransaction.Returned>> outcome = world.run(deleteCharacter);

            assertThat(outcome)
                    .isInstanceOfSatisfying(
                            ValueOutcome.Replayed.class,
                            replayed -> assertThat(replayed.movements()).hasSize(8));
            assertThat(world.receivedBy(friend))
                    .filteredOn(mail -> fromFriend.equals(mail.returnedFromId()))
                    .hasSize(1);
            assertThat(world.transactionsOf("MAIL_CHARACTER_DELETE")).isOne();
        }
    }
}
