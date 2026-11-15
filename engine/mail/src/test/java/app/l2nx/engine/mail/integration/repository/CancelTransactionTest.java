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
import app.l2nx.engine.mail.CommittedItemRow;
import app.l2nx.engine.mail.IncomingTargets;
import app.l2nx.engine.mail.ItemRowState;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.fixture.FakeItems;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.fixture.TestItemStore;
import app.l2nx.engine.mail.repository.CancelTransaction;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CancelTransactionTest {
    static final int ARROW = 17;
    static final int SWORD = 1000;

    @AutoClose
    private final MailWorld world = new MailWorld(MailSettings.defaults());

    private final UUID sender = world.character("Sender");
    private final UUID receiver = world.character("Receiver");

    private UUID attachedMail() {
        final UUID mailId = world.insert(world.mail(sender, receiver)
                .attachments(AttachmentState.ATTACHED)
                .build());
        world.attach(mailId, SWORD, 1);
        world.attach(mailId, ARROW, 20);
        return mailId;
    }

    private ItemRowState stored(int itemTemplateId, long count) {
        return ItemRowState.builder()
                .itemId(world.give(sender, itemTemplateId, count))
                .ownerId(sender)
                .itemTemplateId(itemTemplateId)
                .count(count)
                .build();
    }

    private CancelTransaction cancel(UUID mailId, UUID characterId, List<ItemRowState> stacks) {
        return CancelTransaction.builder()
                .writer(world.writer)
                .idempotencyKey(EntityIds.next())
                .mailId(mailId)
                .sender(IncomingTargets.builder()
                        .characterId(characterId)
                        .stacks(stacks)
                        .build())
                .build();
    }

    @Nested
    class Committed {
        @Test
        void execute_shouldGiveTheItemsBackIntoTheSendersStacks_andHideTheMailFromTheReceiver() {
            final ItemRowState arrows = stored(ARROW, 10);
            final UUID mailId = attachedMail();

            final ValueOutcome<List<CommittedItemRow>> outcome = world.run(cancel(mailId, sender, List.of(arrows)));

            assertThat(outcome).isInstanceOf(ValueOutcome.Committed.class);
            assertThat(((ValueOutcome.Committed<List<CommittedItemRow>>) outcome).result())
                    .map(TestItemStore.TestRow.class::cast)
                    .extracting(TestItemStore.TestRow::itemTemplateId, TestItemStore.TestRow::count)
                    .containsExactlyInAnyOrder(tuple(SWORD, 1L), tuple(ARROW, 30L));
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.CANCELLED);
            assertThat(world.row(mailId).settledAt()).isEqualTo(MailWorld.START);
            assertThat(world.row(mailId).receiverDeletedAt()).isEqualTo(MailWorld.START);
            assertThat(world.mails.countReceived(receiver)).isZero();
            assertThat(world.store.itemsOf(Holder.inventory(sender)))
                    .extracting(TestItemStore.TestRow::itemTemplateId, TestItemStore.TestRow::count)
                    .containsExactly(tuple(ARROW, 30L), tuple(SWORD, 1L));
            assertThat(world.store.itemsInMail(mailId)).isEmpty();
            assertThat(world.ledger(HolderType.INVENTORY, sender, ARROW)).isEqualTo(20);
            assertThat(world.ledger(HolderType.MAIL, mailId, ARROW)).isEqualTo(-20);
            assertThat(world.ledger(HolderType.INVENTORY, sender, SWORD)).isOne();
            assertThat(world.ledger(HolderType.MAIL, mailId, SWORD)).isEqualTo(-1);
        }
    }

    @Nested
    class Refused {
        enum NotCancellable {
            NOT_THE_SENDER,
            SETTLED,
            EXPIRED
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(NotCancellable.class)
        void execute_shouldRefuseAndChangeNothing_whenTheMailIsNotTheSendersToCancel(NotCancellable reason) {
            final UUID mailId = world.insert(world.mail(sender, receiver)
                    .attachments(reason == NotCancellable.SETTLED ? AttachmentState.RECEIVED : AttachmentState.ATTACHED)
                    .build());
            world.attach(mailId, SWORD, 1);
            if (reason == NotCancellable.EXPIRED) {
                world.jdbc
                        .sql("UPDATE mails SET expired_at = sent_at WHERE id = ?")
                        .param(mailId)
                        .update();
            }
            final UUID canceller = reason == NotCancellable.NOT_THE_SENDER ? receiver : sender;

            final ValueOutcome<List<CommittedItemRow>> outcome = world.run(cancel(mailId, canceller, List.of()));

            assertThat(outcome)
                    .isEqualTo(ValueOutcome.Refused.<List<CommittedItemRow>>builder()
                            .reason(MailRefusal.ALREADY_RECEIVED)
                            .build());
            assertThat(world.row(mailId).receiverDeletedAt()).isNull();
            assertThat(world.store.itemsInMail(mailId)).hasSize(1);
        }

        @Test
        void execute_shouldRollBackTheCancel_whenTheSendersAdenaWouldPassTheLimit() {
            final ItemRowState adena = stored(FakeItems.ADENA, FakeItems.ADENA_LIMIT - 10);
            final UUID mailId = attachedMail();
            world.attach(mailId, FakeItems.ADENA, 1_000);

            final ValueOutcome<List<CommittedItemRow>> outcome = world.run(cancel(mailId, sender, List.of(adena)));

            assertThat(outcome)
                    .isEqualTo(ValueOutcome.Refused.<List<CommittedItemRow>>builder()
                            .reason(MailRefusal.ADENA_LIMIT)
                            .build());
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(world.row(mailId).settledAt()).isNull();
            assertThat(world.row(mailId).receiverDeletedAt()).isNull();
            assertThat(world.store.itemsInMail(mailId)).hasSize(3);
            assertThat(world.count(Holder.inventory(sender), FakeItems.ADENA)).isEqualTo(FakeItems.ADENA_LIMIT - 10);
            assertThat(world.count(Holder.inventory(sender), SWORD)).isZero();
            assertThat(world.transactionsOf("MAIL_CANCEL")).isZero();
        }
    }

    @Nested
    class Replayed {
        @Test
        void execute_shouldReplay_whenTheKeyIsUsedAgain() {
            final UUID mailId = attachedMail();
            final CancelTransaction cancel = cancel(mailId, sender, List.of());
            assertThat(world.run(cancel)).isInstanceOf(ValueOutcome.Committed.class);

            final ValueOutcome<List<CommittedItemRow>> outcome = world.run(cancel);

            assertThat(outcome).isInstanceOfSatisfying(ValueOutcome.Replayed.class, replayed -> {
                assertThat(replayed.subjectId()).isEqualTo(mailId);
                assertThat(replayed.movements()).hasSize(4);
            });
            assertThat(world.count(Holder.inventory(sender), ARROW)).isEqualTo(20);
            assertThat(world.transactionsOf("MAIL_CANCEL")).isOne();
        }
    }
}
