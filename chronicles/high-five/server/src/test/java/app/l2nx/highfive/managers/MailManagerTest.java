/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.network.enums.MessageSenderType;
import app.l2nx.highfive.network.holders.MailMessage;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.item.ItemAugmentationRepository;
import app.l2nx.highfive.repository.item.ItemElementalRepository;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.ItemVariableRepository;
import app.l2nx.highfive.repository.mail.MailRepository;
import app.l2nx.highfive.repository.mail.model.MailRow;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class MailManagerTest {
    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final MailRepository mails = mock(MailRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final TransactionTemplate transactions = new TransactionTemplate(new NoOpTransactionManager());

    private final int character = registry.allocateTransient();
    private final int otherCharacter = registry.allocateTransient();

    private MailManager manager;

    @BeforeEach
    void createManager() {
        new IdManager(registry);
        new ItemManager(
                items,
                mock(ItemAugmentationRepository.class),
                mock(ItemElementalRepository.class),
                mock(ItemVariableRepository.class),
                transactions);
        manager = new MailManager(mails, items, transactions);
    }

    @Nested
    class DeleteMailOfCharacter {
        @Test
        void deleteMailOfCharacter_shouldDeleteReceivedAndSentMail_whenCharacterIsDeleted() {
            final MailRow received = mail(otherCharacter, character, false);
            final MailRow sent = mail(character, otherCharacter, false);
            final MailRow unrelated = mail(otherCharacter, otherCharacter, false);
            load(received, sent, unrelated);

            manager.deleteMailOfCharacter(character);

            verify(mails).delete(received.id());
            verify(mails).delete(sent.id());
            verify(mails, never()).delete(unrelated.id());
            assertThat(manager.getMessages()).extracting(MailMessage::getId).containsExactly(unrelated.id());
            assertThat(registry.findId(received.id())).isEmpty();
            assertThat(registry.findId(unrelated.id())).isPresent();
        }

        @Test
        void deleteMailOfCharacter_shouldReturnAttachmentsToSender_whenPlayerMailHasAttachments() {
            final MailRow received = mail(otherCharacter, character, true);
            load(received);

            manager.deleteMailOfCharacter(character);

            verify(items).moveMailToWarehouse(received.id(), otherCharacter);
            verify(items, never()).deleteByMail(anyInt());
            verify(mails, never()).markAttachmentsRemoved(anyInt());
            verify(mails).delete(received.id());
        }

        @Test
        void deleteMailOfCharacter_shouldDeleteAttachmentsWithMail_whenSentBySystem() {
            final MailRow received = mail(MailMessage.SYSTEM_SENDER, character, true).toBuilder()
                    .senderType(MessageSenderType.NEWS)
                    .build();
            load(received);

            manager.deleteMailOfCharacter(character);

            verify(items, never()).moveMailToWarehouse(anyInt(), anyInt());
            verify(items).deleteByMail(received.id());
            verify(mails).delete(received.id());
        }

        @Test
        void deleteMailOfCharacter_shouldReleaseDeletedAttachments_whenTransactionCommits() {
            final MailRow sent = mail(character, otherCharacter, true);
            final int attachment = registry.handleOf(EntityIds.next());
            when(items.deleteByMail(sent.id())).thenReturn(List.of(attachment));
            load(sent);

            manager.deleteMailOfCharacter(character);

            assertThat(registry.findId(attachment)).isEmpty();
        }

        @Test
        void deleteMailOfCharacter_shouldKeepMemory_whenEnclosingTransactionRollsBack() {
            final MailRow received = mail(otherCharacter, character, true);
            final MailRow sent = mail(character, otherCharacter, true);
            final int attachment = registry.handleOf(EntityIds.next());
            when(items.deleteByMail(sent.id())).thenReturn(List.of(attachment));
            load(received, sent);

            assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                        manager.deleteMailOfCharacter(character);
                        throw new DataIntegrityViolationException("character");
                    }))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(manager.getMessages())
                    .extracting(MailMessage::getId)
                    .containsExactlyInAnyOrder(received.id(), sent.id());
            assertThat(manager.getMessages()).allMatch(MailMessage::hasAttachments);
            assertThat(registry.findId(received.id())).isPresent();
            assertThat(registry.findId(attachment)).isPresent();
        }
    }

    @Nested
    class SendMessage {
        @Test
        void sendMessage_shouldStoreMailInTransaction() {
            final MailMessage message = new MailMessage(otherCharacter, character, false, "Hello", "Some text", 0);

            manager.sendMessage(message);

            verify(mails).insert(message.toRow());
            assertThat(manager.isSent(message.getId())).isTrue();
        }
    }

    private void load(MailRow... rows) {
        when(mails.findAll()).thenReturn(List.of(rows));
        manager.load();
    }

    private MailRow mail(int senderId, int receiverId, boolean hasAttachments) {
        return MailRow.builder()
                .id(registry.handleOf(EntityIds.next()))
                .senderId(senderId)
                .receiverId(receiverId)
                .subject("Hello")
                .expiration(ClockBridgeExtension.WALL_START)
                .hasAttachments(hasAttachments)
                .unread(true)
                .senderType(MessageSenderType.PLAYER)
                .build();
    }

    /**
     * Begins, commits and rolls back nothing, so that transaction synchronizations run as with a real manager.
     */
    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        private boolean active;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            active = true;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {}

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            active = false;
        }
    }
}
