/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.config.custom.CustomMailManagerConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.partition.BackgroundTasksExtension;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.mail.CustomMailRepository;
import app.l2nx.highfive.repository.mail.model.CustomMail;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class, BackgroundTasksExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock(BackgroundTasksExtension.LOCK)
class CustomMailManagerTest {
    private static final int POLL_DELAY_MILLIS = 30_000;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final CustomMailRepository repository = mock(CustomMailRepository.class);
    private final MailManager mailManager = mock(MailManager.class);
    private final Player receiver = mock(Player.class);
    private final CustomMailManager manager = new CustomMailManager(repository, mailManager);

    private int previousDelay;
    private CustomMail mail;

    @BeforeEach
    void enterWorld() {
        previousDelay = CustomMailManagerConfig.CUSTOM_MAIL_MANAGER_DELAY;
        CustomMailManagerConfig.CUSTOM_MAIL_MANAGER_DELAY = POLL_DELAY_MILLIS;
        new IdManager(registry);

        final int receiverId = registry.allocateTransient();
        when(receiver.getObjectId()).thenReturn(receiverId);
        when(receiver.isPlayer()).thenReturn(true);
        when(receiver.asPlayer()).thenReturn(receiver);
        when(receiver.isOnline()).thenReturn(true);
        World.addObject(receiver);

        mail = CustomMail.builder()
                .id(7L)
                .receiverId(receiverId)
                .subject("Reward")
                .content("Thanks for voting")
                .build();
    }

    @AfterEach
    void leaveWorld() {
        World.removeObject(receiver);
        CustomMailManagerConfig.CUSTOM_MAIL_MANAGER_DELAY = previousDelay;
    }

    @Nested
    class Load {
        @Test
        void load_shouldPollInTheBackground_afterTheConfiguredDelay() {
            manager.load();

            assertThat(BackgroundTasksExtension.pending())
                    .singleElement()
                    .satisfies(poll -> assertThat(poll.start())
                            .isEqualTo(BackgroundTasksExtension.NOW.plusMillis(POLL_DELAY_MILLIS)));
        }
    }

    @Nested
    class Poll {
        @Test
        void poll_shouldSendInTheNextWorldPass_whenTheReceiverIsOnline() {
            when(repository.findAll()).thenReturn(List.of(mail));
            manager.load();

            BackgroundTasksExtension.runPending();

            verify(mailManager, never()).sendMessage(any());
            verify(repository, never()).delete(anyLong());

            WorldLoopExtension.passAfter(Duration.ZERO);

            verify(repository).delete(mail.id());
            verify(mailManager)
                    .sendMessage(argThat(
                            sent -> (sent.getReceiverId() == mail.receiverId()) && "Reward".equals(sent.getSubject())));
        }

        @Test
        void poll_shouldNotPollAgain_untilTheWorldPassDelivered() {
            when(repository.findAll()).thenReturn(List.of(mail));
            manager.load();

            BackgroundTasksExtension.runPending();

            assertThat(BackgroundTasksExtension.pending()).isEmpty();

            WorldLoopExtension.passAfter(Duration.ZERO);

            assertThat(BackgroundTasksExtension.pending()).hasSize(1);
        }

        @Test
        void poll_shouldKeepTheRow_whenTheReceiverIsOffline() {
            when(receiver.isOnline()).thenReturn(false);
            when(repository.findAll()).thenReturn(List.of(mail));
            manager.load();

            BackgroundTasksExtension.runPending();
            WorldLoopExtension.passAfter(Duration.ZERO);

            verify(repository, never()).delete(anyLong());
            verify(mailManager, never()).sendMessage(any());
            assertThat(BackgroundTasksExtension.pending()).hasSize(1);
        }

        @Test
        void poll_shouldSkipTheRow_andSendTheOthers_whenItsDeleteFails() {
            final CustomMail undeletable =
                    mail.toBuilder().id(8L).subject("Stuck").build();
            when(repository.findAll()).thenReturn(List.of(undeletable, mail));
            doThrow(new DataAccessResourceFailureException("database down"))
                    .when(repository)
                    .delete(undeletable.id());
            manager.load();

            BackgroundTasksExtension.runPending();
            WorldLoopExtension.passAfter(Duration.ZERO);

            verify(mailManager, never()).sendMessage(argThat(sent -> "Stuck".equals(sent.getSubject())));
            verify(mailManager).sendMessage(argThat(sent -> "Reward".equals(sent.getSubject())));
            assertThat(BackgroundTasksExtension.pending()).hasSize(1);
        }

        @Test
        void poll_shouldPollAgainWithoutTheWorld_whenTheQueueIsEmpty() {
            when(repository.findAll()).thenReturn(List.of());
            manager.load();

            BackgroundTasksExtension.runPending();

            assertThat(BackgroundTasksExtension.pending()).hasSize(1);
        }
    }
}
