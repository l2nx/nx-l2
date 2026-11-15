/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.MailType;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class MailRepositoryTest {
    private static final Instant LATER = MailWorld.START.plus(Duration.ofMinutes(5));

    @AutoClose
    private final MailWorld world = new MailWorld(MailSettings.defaults());

    private final UUID sender = world.character("Sender");
    private final UUID receiver = world.character("Receiver");
    private final UUID other = world.character("Other");
    private final TransactionTemplate transactions =
            new TransactionTemplate(new DataSourceTransactionManager(world.dataSource));

    private UUID attached(MailRow.MailRowBuilder mail) {
        return world.insert(mail.attachments(AttachmentState.ATTACHED).build());
    }

    private UUID sentAt(Instant sentAt, AttachmentState attachments) {
        return world.insert(world.mail(sender, receiver)
                .attachments(attachments)
                .sentAt(sentAt)
                .build());
    }

    private void hide(UUID mailId, String column) {
        world.jdbc
                .sql("UPDATE mails SET " + column + " = sent_at WHERE id = ?")
                .param(mailId)
                .update();
    }

    enum Visibility {
        VISIBLE,
        HIDDEN,
        EXPIRED,
        ANOTHER_CHARACTERS
    }

    private UUID mailIn(Visibility visibility, String deletedColumn) {
        final UUID mailId = world.insert(world.mail(sender, receiver).build());
        switch (visibility) {
            case VISIBLE, ANOTHER_CHARACTERS -> {}
            case HIDDEN -> hide(mailId, deletedColumn);
            case EXPIRED -> hide(mailId, "expired_at");
        }
        return mailId;
    }

    /** Asked from another connection, outside the transaction of the test. */
    private boolean mailboxFree(UUID characterId) {
        try (Connection connection = world.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT pg_try_advisory_lock(hashtextextended(CAST(? AS text), 0))")) {
            statement.setObject(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Asked from another connection, outside the transaction of the test. */
    private List<UUID> unlockedMailsOf(UUID receiverId) {
        final List<UUID> ids = new ArrayList<>();
        try (Connection connection = world.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM mails WHERE receiver_id = ? ORDER BY id FOR UPDATE SKIP LOCKED")) {
            connection.setAutoCommit(false);
            statement.setObject(1, receiverId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getObject(1, UUID.class));
                }
            }
            connection.rollback();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return ids;
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreEveryColumnItIsGiven_whenTheRowHasThemAll() {
            final UUID original = world.insert(world.mail(receiver, sender)
                    .attachments(AttachmentState.RETURNED)
                    .build());
            final MailRow expected = world.mail(sender, receiver)
                    .type(MailType.PLAYER)
                    .body("Body")
                    .codAdena(700L)
                    .attachments(AttachmentState.ATTACHED)
                    .returnedFromId(original)
                    .senderDeletedAt(MailWorld.START)
                    .build();

            world.mails.insert(expected);

            assertThat(world.row(expected.id())).isEqualTo(expected);
        }
    }

    @Nested
    class FindReceived {
        @ParameterizedTest(name = "{0}")
        @EnumSource(Visibility.class)
        void findReceived_shouldFindTheMail_whenItIsVisibleInTheReceiversBox(Visibility visibility) {
            final UUID mailId = mailIn(visibility, "receiver_deleted_at");
            final UUID owner = visibility == Visibility.ANOTHER_CHARACTERS ? sender : receiver;

            final Optional<MailRow> actual = world.mails.findReceived(mailId, owner);

            assertThat(actual.map(MailRow::id))
                    .isEqualTo(visibility == Visibility.VISIBLE ? Optional.of(mailId) : Optional.empty());
        }
    }

    @Nested
    class FindSent {
        @ParameterizedTest(name = "{0}")
        @EnumSource(Visibility.class)
        void findSent_shouldFindTheMail_whenItIsVisibleInTheSendersBox(Visibility visibility) {
            final UUID mailId = mailIn(visibility, "sender_deleted_at");
            final UUID owner = visibility == Visibility.ANOTHER_CHARACTERS ? receiver : sender;

            final Optional<MailRow> actual = world.mails.findSent(mailId, owner);

            assertThat(actual.map(MailRow::id))
                    .isEqualTo(visibility == Visibility.VISIBLE ? Optional.of(mailId) : Optional.empty());
        }
    }

    @Nested
    class ListReceived {
        @Test
        void listReceived_shouldListTheVisibleMailNewestFirst() {
            final UUID older = sentAt(MailWorld.START, AttachmentState.NONE);
            final UUID newer = sentAt(LATER, AttachmentState.NONE);
            hide(sentAt(LATER, AttachmentState.NONE), "receiver_deleted_at");
            world.insert(world.mail(sender, other).build());

            assertThat(world.mails.listReceived(receiver, 10))
                    .extracting(MailRow::id)
                    .containsExactly(newer, older);
        }

        @Test
        void listReceived_shouldShowTheMailsHoldingAttachments_whenTheBoxHoldsMoreThanTheLimit() {
            final UUID oldAttached = sentAt(MailWorld.START, AttachmentState.ATTACHED);
            sentAt(MailWorld.START.plusSeconds(1), AttachmentState.RECEIVED);
            final UUID newest = sentAt(LATER, AttachmentState.NONE);
            final UUID midAttached = sentAt(MailWorld.START.plusSeconds(2), AttachmentState.ATTACHED);

            assertThat(world.mails.listReceived(receiver, 3))
                    .extracting(MailRow::id)
                    .containsExactly(newest, midAttached, oldAttached);
        }
    }

    @Nested
    class ListSent {
        @Test
        void listSent_shouldListTheVisibleMailNewestFirstUpToTheLimit() {
            sentAt(MailWorld.START, AttachmentState.ATTACHED);
            final UUID middle = sentAt(MailWorld.START.plusSeconds(1), AttachmentState.NONE);
            final UUID newest = sentAt(LATER, AttachmentState.NONE);
            hide(sentAt(LATER.plusSeconds(1), AttachmentState.NONE), "sender_deleted_at");

            assertThat(world.mails.listSent(sender, 2)).extracting(MailRow::id).containsExactly(newest, middle);
        }
    }

    @Nested
    class CountReceived {
        @Test
        void countReceived_shouldCountVisibleMailOnly_whenSomeIsHiddenOrExpired() {
            for (Visibility visibility : Visibility.values()) {
                mailIn(visibility, "receiver_deleted_at");
            }

            assertThat(world.mails.countReceived(receiver)).isEqualTo(2);
            assertThat(world.mails.countReceived(other)).isZero();
        }
    }

    @Nested
    class CountSent {
        @Test
        void countSent_shouldCountVisibleMailOnly_whenSomeIsHiddenOrExpired() {
            for (Visibility visibility : Visibility.values()) {
                mailIn(visibility, "sender_deleted_at");
            }

            assertThat(world.mails.countSent(sender)).isEqualTo(2);
            assertThat(world.mails.countSent(receiver)).isZero();
        }
    }

    @Nested
    class CountUnread {
        @Test
        void countUnread_shouldCountVisibleUnreadMail_whenSomeIsReadHiddenOrExpired() {
            world.insert(world.mail(sender, receiver).build());
            world.mails.markRead(world.insert(world.mail(sender, receiver).build()), receiver, MailWorld.START);
            mailIn(Visibility.HIDDEN, "receiver_deleted_at");
            mailIn(Visibility.EXPIRED, "receiver_deleted_at");

            assertThat(world.mails.countUnread(receiver)).isOne();
        }
    }

    @Nested
    class MarkRead {
        @Test
        void markRead_shouldStampTheFirstReadOnly_whenTheReceiverOpensItTwice() {
            final UUID mailId = world.insert(world.mail(sender, receiver).build());

            assertThat(world.mails.markRead(mailId, receiver, MailWorld.START)).isTrue();
            assertThat(world.mails.markRead(mailId, receiver, LATER)).isFalse();

            assertThat(world.row(mailId).readAt()).isEqualTo(MailWorld.START);
        }

        @Test
        void markRead_shouldNotStamp_whenAnotherCharacterOpensIt() {
            final UUID mailId = world.insert(world.mail(sender, receiver).build());

            assertThat(world.mails.markRead(mailId, sender, MailWorld.START)).isFalse();

            assertThat(world.row(mailId).readAt()).isNull();
        }
    }

    @Nested
    class HideReceived {
        @Test
        void hideReceived_shouldHideTheReceiversMailWithoutAttachments_andKeepTheRest() {
            final UUID read = sentAt(MailWorld.START, AttachmentState.RECEIVED);
            final UUID attached = sentAt(MailWorld.START, AttachmentState.ATTACHED);
            final UUID elsewhere = world.insert(world.mail(sender, other).build());

            final List<UUID> actual = world.mails.hideReceived(List.of(read, attached, elsewhere), receiver, LATER);

            assertThat(actual).containsExactly(read);
            assertThat(world.row(read).receiverDeletedAt()).isEqualTo(LATER);
            assertThat(world.row(attached).receiverDeletedAt()).isNull();
            assertThat(world.row(elsewhere).receiverDeletedAt()).isNull();
            assertThat(world.row(read).senderDeletedAt()).isNull();
        }

        @Test
        void hideReceived_shouldHideNothing_whenNoMailIsGiven() {
            assertThat(world.mails.hideReceived(List.of(), receiver, LATER)).isEmpty();
        }
    }

    @Nested
    class HideSent {
        @Test
        void hideSent_shouldHideTheSendersMailWithoutAttachments_andKeepTheRest() {
            final UUID read = sentAt(MailWorld.START, AttachmentState.RECEIVED);
            final UUID attached = sentAt(MailWorld.START, AttachmentState.ATTACHED);
            final UUID elsewhere = world.insert(world.mail(other, receiver).build());

            final List<UUID> actual = world.mails.hideSent(List.of(read, attached, elsewhere), sender, LATER);

            assertThat(actual).containsExactly(read);
            assertThat(world.row(read).senderDeletedAt()).isEqualTo(LATER);
            assertThat(world.row(attached).senderDeletedAt()).isNull();
            assertThat(world.row(read).receiverDeletedAt()).isNull();
        }

        @Test
        void hideSent_shouldHideNothing_whenNoMailIsGiven() {
            assertThat(world.mails.hideSent(List.of(), sender, LATER)).isEmpty();
        }
    }

    @Nested
    class CountAttachedReceived {
        @Test
        void countAttachedReceived_shouldCountTheGivenVisibleMailHoldingAttachments() {
            final UUID attached = sentAt(MailWorld.START, AttachmentState.ATTACHED);
            final UUID settled = sentAt(MailWorld.START, AttachmentState.RECEIVED);
            final UUID hidden = sentAt(MailWorld.START, AttachmentState.ATTACHED);
            hide(hidden, "receiver_deleted_at");
            sentAt(MailWorld.START, AttachmentState.ATTACHED);

            assertThat(world.mails.countAttachedReceived(List.of(attached, settled, hidden), receiver))
                    .isOne();
            assertThat(world.mails.countAttachedReceived(List.of(attached), sender))
                    .isZero();
            assertThat(world.mails.countAttachedReceived(List.of(), receiver)).isZero();
        }
    }

    @Nested
    class CountAttachedSent {
        @Test
        void countAttachedSent_shouldCountTheGivenVisibleMailHoldingAttachments() {
            final UUID attached = sentAt(MailWorld.START, AttachmentState.ATTACHED);
            final UUID settled = sentAt(MailWorld.START, AttachmentState.CANCELLED);
            final UUID hidden = sentAt(MailWorld.START, AttachmentState.ATTACHED);
            hide(hidden, "sender_deleted_at");

            assertThat(world.mails.countAttachedSent(List.of(attached, settled, hidden), sender))
                    .isOne();
            assertThat(world.mails.countAttachedSent(List.of(attached), receiver))
                    .isZero();
            assertThat(world.mails.countAttachedSent(List.of(), sender)).isZero();
        }
    }

    @Nested
    class Claim {
        @ParameterizedTest(name = "sender matches {0}, cod {1}")
        @CsvSource(textBlock = """
            true,  700, true
            false, 700, false
            true,  ,    false
            true,  800, false
            """)
        void claim_shouldSettle_whenTheSenderAndTheCodAreAsReserved(
                boolean sameSender, Long expectedCod, boolean expected) {
            final UUID mailId = attached(world.mail(sender, receiver).codAdena(700L));

            final boolean claimed =
                    world.mails.claim(mailId, receiver, sameSender ? sender : EntityIds.next(), expectedCod, LATER);

            assertThat(claimed).isEqualTo(expected);
            assertThat(world.row(mailId).attachments())
                    .isEqualTo(expected ? AttachmentState.RECEIVED : AttachmentState.ATTACHED);
            assertThat(world.row(mailId).settledAt()).isEqualTo(expected ? LATER : null);
        }

        @Test
        void claim_shouldSettleMailOfADeletedSender_whenNoSenderIsExpected() {
            final UUID mailId = attached(world.mail(null, receiver).senderName("Sender"));

            assertThat(world.mails.claim(mailId, receiver, null, null, LATER)).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(
                value = Visibility.class,
                names = {"EXPIRED", "ANOTHER_CHARACTERS"})
        void claim_shouldChangeNothing_whenTheMailIsNotTheReceiversToClaim(Visibility visibility) {
            final UUID mailId = attached(world.mail(sender, receiver));
            if (visibility == Visibility.EXPIRED) {
                hide(mailId, "expired_at");
            }
            final UUID claimer = visibility == Visibility.ANOTHER_CHARACTERS ? other : receiver;

            assertThat(world.mails.claim(mailId, claimer, sender, null, LATER)).isFalse();

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
        }
    }

    @Nested
    class Destroy {
        @Test
        void destroy_shouldSettleTheAttachmentsDestroyed_whenTheyAreInTheGivenState() {
            final UUID mailId = attached(world.mail(sender, receiver));

            assertThat(world.mails.destroy(mailId, AttachmentState.ATTACHED, LATER))
                    .isTrue();

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.DESTROYED);
            assertThat(world.row(mailId).settledAt()).isEqualTo(LATER);
        }

        @Test
        void destroy_shouldKeepTheSettlementTime_whenItTurnsAClaimOfTheSameTransaction() {
            final UUID mailId = attached(world.mail(sender, receiver));
            world.mails.claim(mailId, receiver, sender, null, MailWorld.START);

            assertThat(world.mails.destroy(mailId, AttachmentState.RECEIVED, LATER))
                    .isTrue();

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.DESTROYED);
            assertThat(world.row(mailId).settledAt()).isEqualTo(MailWorld.START);
        }

        @Test
        void destroy_shouldChangeNothing_whenTheAttachmentsAreInAnotherState() {
            final UUID mailId = sentAt(MailWorld.START, AttachmentState.CANCELLED);

            assertThat(world.mails.destroy(mailId, AttachmentState.ATTACHED, LATER))
                    .isFalse();

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.CANCELLED);
        }
    }

    @Nested
    class Cancel {
        @Test
        void cancel_shouldSettleCancelledAndHideItFromTheReceiver_whenTheSenderCancels() {
            final UUID mailId = attached(world.mail(sender, receiver));

            assertThat(world.mails.cancel(mailId, sender, LATER)).isTrue();

            final MailRow actual = world.row(mailId);
            assertThat(actual.attachments()).isEqualTo(AttachmentState.CANCELLED);
            assertThat(actual.settledAt()).isEqualTo(LATER);
            assertThat(actual.receiverDeletedAt()).isEqualTo(LATER);
            assertThat(actual.senderDeletedAt()).isNull();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(
                value = AttachmentState.class,
                names = {"NONE", "RECEIVED", "RETURNED", "CANCELLED", "EXPIRED", "DESTROYED"})
        void cancel_shouldChangeNothing_whenTheAttachmentsAreSettled(AttachmentState state) {
            final UUID mailId = sentAt(MailWorld.START, state);

            assertThat(world.mails.cancel(mailId, sender, LATER)).isFalse();

            assertThat(world.row(mailId).receiverDeletedAt()).isNull();
        }

        @Test
        void cancel_shouldChangeNothing_whenAnotherCharacterCancels() {
            final UUID mailId = attached(world.mail(sender, receiver));

            assertThat(world.mails.cancel(mailId, receiver, LATER)).isFalse();

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
        }
    }

    @Nested
    class SettleReturned {
        @Test
        void settleReturned_shouldReturnTheOriginalAsSettled_whenTheReceiverReturnsIt() {
            final UUID mailId = attached(world.mail(sender, receiver));

            final Optional<MailRow> actual = world.mails.settleReturned(mailId, receiver, LATER);

            assertThat(actual).hasValueSatisfying(row -> {
                assertThat(row.id()).isEqualTo(mailId);
                assertThat(row.attachments()).isEqualTo(AttachmentState.RETURNED);
                assertThat(row.settledAt()).isEqualTo(LATER);
            });
            assertThat(world.row(mailId)).isEqualTo(actual.orElseThrow());
        }

        @Test
        void settleReturned_shouldReturnNothing_whenTheMailIsAReturnedOne() {
            final UUID original = sentAt(MailWorld.START, AttachmentState.RETURNED);
            final UUID mailId = attached(world.mail(sender, receiver).returnedFromId(original));

            assertThat(world.mails.settleReturned(mailId, receiver, LATER)).isEmpty();

            assertThat(world.row(mailId).attachments()).isEqualTo(AttachmentState.ATTACHED);
        }
    }

    @Nested
    class Expire {
        @Test
        void expire_shouldSettleExpiredAndHideFromBothBoxes_whenTheMailIsDue() {
            final UUID mailId = attached(world.mail(sender, receiver).expiresAt(MailWorld.START));

            final Optional<MailRow> actual = world.mails.expire(mailId, MailWorld.START);

            assertThat(actual).hasValueSatisfying(row -> {
                assertThat(row.attachments()).isEqualTo(AttachmentState.EXPIRED);
                assertThat(row.expiredAt()).isEqualTo(MailWorld.START);
                assertThat(row.settledAt()).isEqualTo(MailWorld.START);
            });
            assertThat(world.mails.countReceived(receiver)).isZero();
            assertThat(world.mails.countSent(sender)).isZero();
        }

        @Test
        void expire_shouldReturnNothing_whenTheMailIsNotDueYet() {
            final UUID mailId = attached(world.mail(sender, receiver).expiresAt(LATER));

            assertThat(world.mails.expire(mailId, MailWorld.START)).isEmpty();

            assertThat(world.row(mailId).expiredAt()).isNull();
        }

        @Test
        void expire_shouldReturnNothing_whenTheMailHoldsNoAttachments() {
            final UUID mailId = world.insert(
                    world.mail(sender, receiver).expiresAt(MailWorld.START).build());

            assertThat(world.mails.expire(mailId, MailWorld.START)).isEmpty();
        }
    }

    @Nested
    class ExpireWithoutAttachments {
        @Test
        void expireWithoutAttachments_shouldHideTheOldestDueMailWithoutAttachments_upToTheLimit() {
            final UUID oldest = world.insert(world.mail(sender, receiver)
                    .expiresAt(MailWorld.START.minusSeconds(2))
                    .build());
            final UUID older = world.insert(world.mail(sender, receiver)
                    .attachments(AttachmentState.RECEIVED)
                    .expiresAt(MailWorld.START.minusSeconds(1))
                    .build());
            final UUID attached = attached(world.mail(sender, receiver).expiresAt(MailWorld.START.minusSeconds(3)));
            final UUID notDue =
                    world.insert(world.mail(sender, receiver).expiresAt(LATER).build());

            assertThat(world.mails.expireWithoutAttachments(MailWorld.START, 1))
                    .extracting(MailRow::id)
                    .containsExactly(oldest);
            assertThat(world.mails.expireWithoutAttachments(MailWorld.START, 10))
                    .extracting(MailRow::id)
                    .containsExactly(older);

            assertThat(world.row(oldest).expiredAt()).isEqualTo(MailWorld.START);
            assertThat(world.row(oldest).attachments()).isEqualTo(AttachmentState.NONE);
            assertThat(world.row(attached).expiredAt()).isNull();
            assertThat(world.row(notDue).expiredAt()).isNull();
        }
    }

    @Nested
    class FindDueAttached {
        @Test
        void findDueAttached_shouldReturnTheOldestFirst_whenSeveralAreDue() {
            final UUID later = attached(world.mail(sender, receiver).expiresAt(MailWorld.START.minusSeconds(60)));
            final UUID earlier = attached(world.mail(sender, receiver).expiresAt(MailWorld.START.minusSeconds(120)));
            attached(world.mail(sender, receiver).expiresAt(MailWorld.START.plusSeconds(1)));
            world.insert(world.mail(sender, receiver)
                    .attachments(AttachmentState.RECEIVED)
                    .expiresAt(MailWorld.START.minusSeconds(180))
                    .build());

            assertThat(world.mails.findDueAttached(MailWorld.START, 10))
                    .extracting(MailRow::id)
                    .containsExactly(earlier, later);
            assertThat(world.mails.findDueAttached(MailWorld.START, 1))
                    .extracting(MailRow::id)
                    .containsExactly(earlier);
        }
    }

    @Nested
    class DropCod {
        @Test
        void dropCod_shouldClearTheCashOnDeliveryOfAttachedMail_whenTheSenderGoes() {
            final UUID attached = attached(world.mail(sender, receiver).codAdena(700L));
            final UUID settled = world.insert(world.mail(sender, receiver)
                    .attachments(AttachmentState.RECEIVED)
                    .codAdena(700L)
                    .build());
            final UUID othersMail = attached(world.mail(other, receiver).codAdena(700L));

            assertThat(world.mails.dropCod(sender)).isOne();

            assertThat(world.row(attached).codAdena()).isNull();
            assertThat(world.row(settled).codAdena()).isEqualTo(700);
            assertThat(world.row(othersMail).codAdena()).isEqualTo(700);
        }
    }

    @Nested
    class LockAttachedReceived {
        @Test
        void lockAttachedReceived_shouldReturnAndLockTheReceiversMailHoldingAttachments() {
            final UUID first = attached(world.mail(sender, receiver));
            final UUID second = attached(world.mail(other, receiver));
            final UUID settled = sentAt(MailWorld.START, AttachmentState.RECEIVED);
            final UUID expired = attached(world.mail(sender, receiver));
            hide(expired, "expired_at");

            final List<UUID> locked = new ArrayList<>();
            final List<UUID> unlocked = transactions.execute(status -> {
                world.mails.lockAttachedReceived(receiver).forEach(row -> locked.add(row.id()));
                return unlockedMailsOf(receiver);
            });

            assertThat(locked)
                    .containsExactlyElementsOf(
                            List.of(first, second).stream().sorted().toList());
            assertThat(unlocked).containsExactlyInAnyOrder(settled, expired);
            assertThat(unlockedMailsOf(receiver)).hasSize(4);
        }
    }

    @Nested
    class HasAttached {
        @ParameterizedTest(name = "as {0}")
        @CsvSource({"sender", "receiver"})
        void hasAttached_shouldFindTheItem_whenAMailOfTheCharacterStillHoldsIt(String side) {
            final UUID mailId = attached(world.mail(sender, receiver));
            world.attachments.insertAll(mailId, List.of(snapshot(13_539)));

            assertThat(world.mails.hasAttached(side.equals("sender") ? sender : receiver, List.of(57, 13_539)))
                    .isTrue();
            assertThat(world.mails.hasAttached(other, List.of(13_539))).isFalse();
            assertThat(world.mails.hasAttached(receiver, List.of(57))).isFalse();
        }

        @Test
        void hasAttached_shouldNotFindTheItem_whenTheMailIsSettledOrExpired() {
            final UUID settled = sentAt(MailWorld.START, AttachmentState.RECEIVED);
            world.attachments.insertAll(settled, List.of(snapshot(13_539)));
            final UUID expired = attached(world.mail(sender, receiver));
            world.attachments.insertAll(expired, List.of(snapshot(13_539)));
            hide(expired, "expired_at");

            assertThat(world.mails.hasAttached(receiver, List.of(13_539))).isFalse();
        }

        @Test
        void hasAttached_shouldNotFindAnything_whenNoItemIsAsked() {
            attached(world.mail(sender, receiver));

            assertThat(world.mails.hasAttached(receiver, List.of())).isFalse();
        }

        private static AttachmentSnapshot snapshot(int itemTemplateId) {
            return AttachmentSnapshot.builder()
                    .slot(0)
                    .itemTemplateId(itemTemplateId)
                    .count(1)
                    .enchantOptions(List.of())
                    .build();
        }
    }

    @Nested
    class LockMailboxes {
        @Test
        void lockMailboxes_shouldHoldBothBoxesUntilTheTransactionEnds() {
            final List<Boolean> whileLocked = transactions.execute(status -> {
                world.mails.lockMailboxes(receiver, sender);
                return List.of(mailboxFree(sender), mailboxFree(receiver), mailboxFree(other));
            });

            assertThat(whileLocked).containsExactly(false, false, true);
            assertThat(mailboxFree(sender)).isTrue();
            assertThat(mailboxFree(receiver)).isTrue();
        }

        @Test
        void lockMailboxes_shouldLockOneBox_whenBothAreTheSame() {
            final Boolean whileLocked = transactions.execute(status -> {
                world.mails.lockMailboxes(sender, sender);
                return mailboxFree(sender);
            });

            assertThat(whileLocked).isFalse();
        }
    }
}
