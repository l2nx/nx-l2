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
import app.l2nx.engine.mail.MailType;
import app.l2nx.engine.mail.fixture.FakeItems;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.fixture.TestItemStore;
import app.l2nx.engine.mail.repository.ExpireTransaction;
import app.l2nx.engine.mail.repository.MailRow;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ExpireTransactionTest {
    static final int ARROW = 17;
    static final int SWORD = 1000;

    private final MailWorld world = new MailWorld(MailSettings.defaults());
    private final UUID sender = world.character("Sender");
    private final UUID receiver = world.character("Receiver");

    private MailRow.MailRowBuilder due(UUID senderId, UUID receiverId) {
        return world.mail(senderId, receiverId)
                .attachments(AttachmentState.ATTACHED)
                .expiresAt(MailWorld.START.minusSeconds(1));
    }

    private UUID attached(MailRow.MailRowBuilder mail) {
        final UUID mailId = world.insert(mail.build());
        world.attach(mailId, SWORD, 1);
        world.attach(mailId, ARROW, 20);
        return mailId;
    }

    private UUID stored(UUID ownerId, int itemTemplateId, long count) {
        final UUID id = EntityIds.next();
        world.store.insert(id, ownerId, HolderType.WAREHOUSE.name(), null, itemTemplateId, count);
        return id;
    }

    private ExpireTransaction expire(UUID mailId, UUID ownerId, IncomingTargets warehouse) {
        return ExpireTransaction.builder()
                .writer(world.writer)
                .idempotencyKey(EntityIds.next())
                .mailId(mailId)
                .ownerId(ownerId)
                .warehouse(warehouse)
                .build();
    }

    private static ExpireTransaction.Expired expired(ValueOutcome<ExpireTransaction.Expired> outcome) {
        assertThat(outcome).isInstanceOf(ValueOutcome.Committed.class);
        return ((ValueOutcome.Committed<ExpireTransaction.Expired>) outcome).result();
    }

    private void assertExpiredRow(UUID mailId, AttachmentState attachments) {
        final MailRow row = world.row(mailId);
        assertThat(row.attachments()).isEqualTo(attachments);
        assertThat(row.expiredAt()).isEqualTo(MailWorld.START);
        assertThat(row.settledAt()).isEqualTo(MailWorld.START);
    }

    @Nested
    class Committed {
        @Test
        void execute_shouldMergeIntoTheOfflineWarehouseStackByDelta_whenTheOwnerHoldsOne() {
            final UUID arrows = stored(sender, ARROW, 10);
            final UUID mailId = attached(due(sender, receiver));

            final ExpireTransaction.Expired actual = expired(world.run(expire(mailId, sender, null)));

            final Holder warehouse = Holder.warehouse(sender);
            assertThat(actual.ownerId()).isEqualTo(sender);
            assertThat(actual.mail().id()).isEqualTo(mailId);
            assertThat(actual.rows())
                    .map(TestItemStore.TestRow.class::cast)
                    .extracting(TestItemStore.TestRow::itemId, TestItemStore.TestRow::count)
                    .contains(tuple(arrows, 30L));
            assertExpiredRow(mailId, AttachmentState.EXPIRED);
            assertThat(world.store.itemsOf(warehouse))
                    .extracting(TestItemStore.TestRow::itemTemplateId, TestItemStore.TestRow::count)
                    .containsExactly(tuple(ARROW, 30L), tuple(SWORD, 1L));
            assertThat(world.store.itemsOf(warehouse))
                    .filteredOn(row -> row.itemTemplateId() == ARROW)
                    .extracting(TestItemStore.TestRow::itemId)
                    .containsExactly(arrows);
            assertThat(world.store.itemsInMail(mailId)).isEmpty();
            assertThat(world.ledger(HolderType.WAREHOUSE, sender, ARROW)).isEqualTo(20);
            assertThat(world.ledger(HolderType.MAIL, mailId, ARROW)).isEqualTo(-20);
            assertThat(world.ledger(HolderType.WAREHOUSE, sender, SWORD)).isOne();
            assertThat(world.ledger(HolderType.MAIL, mailId, SWORD)).isEqualTo(-1);
        }

        @Test
        void execute_shouldMergeIntoTheLoadedWarehouseByAbsoluteCount_whenTheOwnerIsOnline() {
            final UUID arrows = stored(sender, ARROW, 10);
            final UUID mailId = attached(due(sender, receiver));
            final IncomingTargets warehouse = IncomingTargets.builder()
                    .characterId(sender)
                    .stacks(List.of(ItemRowState.builder()
                            .itemId(arrows)
                            .ownerId(sender)
                            .itemTemplateId(ARROW)
                            .count(10)
                            .build()))
                    .build();

            expired(world.run(expire(mailId, sender, warehouse)));

            assertThat(world.store.itemsOf(Holder.warehouse(sender)))
                    .extracting(TestItemStore.TestRow::itemId, TestItemStore.TestRow::count)
                    .contains(tuple(arrows, 30L));
            assertThat(world.ledger(HolderType.WAREHOUSE, sender, ARROW)).isEqualTo(20);
        }

        enum Owner {
            PLAYER_MAIL,
            SYSTEM_MAIL,
            SENDER_DELETED,
            RETURNED_MAIL
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Owner.class)
        void execute_shouldGiveTheAttachmentsToTheExpiryOwner_whenTheMailIsDue(Owner owner) {
            final MailRow.MailRowBuilder mail = due(sender, receiver);
            final UUID expected = switch (owner) {
                case PLAYER_MAIL -> sender;
                case SYSTEM_MAIL -> {
                    mail.type(MailType.SYSTEM).senderId(null).senderName(null);
                    yield receiver;
                }
                case SENDER_DELETED -> {
                    mail.senderId(null);
                    yield receiver;
                }
                case RETURNED_MAIL -> {
                    mail.returnedFromId(world.insert(world.mail(receiver, sender)
                            .attachments(AttachmentState.RETURNED)
                            .build()));
                    yield receiver;
                }
            };
            final UUID mailId = attached(mail);

            final ExpireTransaction.Expired actual = expired(world.run(expire(mailId, expected, null)));

            assertThat(actual.ownerId()).isEqualTo(expected);
            assertThat(world.count(Holder.warehouse(expected), SWORD)).isOne();
            assertThat(world.count(Holder.warehouse(expected), ARROW)).isEqualTo(20);
        }

        @Test
        void execute_shouldDestroyTheAttachments_whenNobodyIsLeftToGetThem() {
            final UUID original = world.insert(world.mail(null, sender)
                    .attachments(AttachmentState.RETURNED)
                    .build());
            final UUID mailId = attached(due(sender, null).returnedFromId(original));

            final ExpireTransaction.Expired actual = expired(world.run(expire(mailId, null, null)));

            assertThat(actual.ownerId()).isNull();
            assertThat(actual.rows()).isEmpty();
            assertExpiredRow(mailId, AttachmentState.DESTROYED);
            assertThat(world.store.itemsInMail(mailId)).isEmpty();
            assertThat(world.ledger(HolderType.MAIL, mailId, ARROW)).isEqualTo(-20);
            assertThat(world.ledger(HolderType.MAIL, mailId, SWORD)).isEqualTo(-1);
        }
    }

    @Nested
    class Refused {
        enum NotDue {
            NOT_YET,
            SETTLED
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(NotDue.class)
        void execute_shouldRefuse_whenTheMailIsNotDueWithAttachments(NotDue reason) {
            final MailRow.MailRowBuilder mail = due(sender, receiver);
            if (reason == NotDue.NOT_YET) {
                mail.expiresAt(MailWorld.START.plusSeconds(1));
            } else {
                mail.attachments(AttachmentState.RECEIVED);
            }
            final UUID mailId = attached(mail);

            final ValueOutcome<ExpireTransaction.Expired> outcome = world.run(expire(mailId, sender, null));

            assertThat(outcome)
                    .isEqualTo(ValueOutcome.Refused.<ExpireTransaction.Expired>builder()
                            .reason(MailRefusal.ALREADY_RECEIVED)
                            .build());
            assertThat(world.row(mailId).expiredAt()).isNull();
        }

        @Test
        void execute_shouldRollBackTheExpiry_whenTheOwnerChangedSinceThePartitionPrepared() {
            final UUID mailId = attached(due(sender, receiver));

            final ValueOutcome<ExpireTransaction.Expired> outcome = world.run(expire(mailId, receiver, null));

            assertThat(outcome)
                    .isEqualTo(ValueOutcome.Refused.<ExpireTransaction.Expired>builder()
                            .reason(MailRefusal.FAILED)
                            .build());
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(world.row(mailId).expiredAt()).isNull();
            assertThat(world.row(mailId).settledAt()).isNull();
            assertThat(world.mails.findDueAttached(MailWorld.START, 10))
                    .extracting(MailRow::id)
                    .containsExactly(mailId);
        }

        @Test
        void execute_shouldLeaveTheMailDue_whenTheWarehouseAdenaWouldPassTheLimit() {
            final UUID adena = stored(sender, FakeItems.ADENA, FakeItems.ADENA_LIMIT - 10);
            final UUID mailId = attached(due(sender, receiver));
            world.attach(mailId, FakeItems.ADENA, 1_000);

            final ValueOutcome<ExpireTransaction.Expired> outcome = world.run(expire(mailId, sender, null));

            assertThat(outcome)
                    .isEqualTo(ValueOutcome.Refused.<ExpireTransaction.Expired>builder()
                            .reason(MailRefusal.ADENA_LIMIT)
                            .build());
            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
            assertThat(world.row(mailId).expiredAt()).isNull();
            assertThat(world.mails.findDueAttached(MailWorld.START, 10))
                    .extracting(MailRow::id)
                    .containsExactly(mailId);
            assertThat(world.store.itemsInMail(mailId)).hasSize(3);
            assertThat(world.store.itemsOf(Holder.warehouse(sender)))
                    .extracting(TestItemStore.TestRow::itemId, TestItemStore.TestRow::count)
                    .containsExactly(tuple(adena, FakeItems.ADENA_LIMIT - 10));
            assertThat(world.transactionsOf("MAIL_EXPIRE")).isZero();
        }
    }

    @Nested
    class Replayed {
        @Test
        void execute_shouldReplay_whenTheKeyIsUsedAgain() {
            final UUID mailId = attached(due(sender, receiver));
            final ExpireTransaction expire = expire(mailId, sender, null);
            expired(world.run(expire));

            final ValueOutcome<ExpireTransaction.Expired> outcome = world.run(expire);

            assertThat(outcome).isInstanceOfSatisfying(ValueOutcome.Replayed.class, replayed -> {
                assertThat(replayed.subjectId()).isEqualTo(mailId);
                assertThat(replayed.movements()).hasSize(4);
            });
            assertThat(world.count(Holder.warehouse(sender), ARROW)).isEqualTo(20);
            assertThat(world.transactionsOf("MAIL_EXPIRE")).isOne();
        }
    }
}
