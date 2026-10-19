/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.enums.LoginFailReason;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.repository.AccountDataRepository;
import app.l2nx.login.repository.AccountRepository;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

@Execution(ExecutionMode.SAME_THREAD)
class LoginControllerTest {
    private static final String ACCOUNT = "account";
    private static final Duration LOGIN_TIMEOUT = Duration.ofMillis(LoginController.LOGIN_TIMEOUT);

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final ScheduledFuture<?> purgeTask = mock(ScheduledFuture.class);
    private final LoginController controller = new LoginController(
            mock(AccountRepository.class),
            mock(AccountDataRepository.class),
            TestLoginProperties.create(new File(".")),
            taskScheduler);

    @BeforeEach
    void setUp() {
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
                .thenAnswer(invocation -> purgeTask);
    }

    @Nested
    class Load {
        @Test
        void load_shouldSchedulePurgeEveryLoginTimeout() throws Exception {
            final Instant before = Instant.now();
            controller.load();
            final Instant after = Instant.now();

            final ArgumentCaptor<Instant> startTime = ArgumentCaptor.forClass(Instant.class);
            verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), startTime.capture(), eq(LOGIN_TIMEOUT));
            assertThat(startTime.getValue()).isBetween(before.plus(LOGIN_TIMEOUT), after.plus(LOGIN_TIMEOUT));
        }
    }

    @Nested
    class Purge {
        @Test
        void purge_shouldCloseClient_whenLoginTimedOut() throws Exception {
            final LoginClient client = client(false, System.currentTimeMillis() - LOGIN_TIMEOUT.toMillis(), true);

            runPurge();

            verify(client).close(LoginFailReason.REASON_ACCESS_FAILED);
            assertThat(controller.getAuthedClient(ACCOUNT)).isNull();
        }

        @Test
        void purge_shouldCloseClient_whenDisconnected() throws Exception {
            final LoginClient client = client(true, System.currentTimeMillis(), false);

            runPurge();

            verify(client).close(LoginFailReason.REASON_ACCESS_FAILED);
            assertThat(controller.getAuthedClient(ACCOUNT)).isNull();
        }

        @Test
        void purge_shouldKeepClient_whenJoinedGameServer() throws Exception {
            final LoginClient client = client(true, System.currentTimeMillis() - LOGIN_TIMEOUT.toMillis(), true);

            runPurge();

            verify(client, never()).close(any(LoginFailReason.class));
            assertThat(controller.getAuthedClient(ACCOUNT)).isSameAs(client);
        }

        private LoginClient client(boolean joinedGameServer, long connectionStartTime, boolean connected) {
            final LoginClient client = mock(LoginClient.class);
            when(client.hasJoinedGS()).thenReturn(joinedGameServer);
            when(client.getConnectionStartTime()).thenReturn(connectionStartTime);
            when(client.isConnected()).thenReturn(connected);
            controller.assignSessionKeyToClient(ACCOUNT, client);
            return client;
        }

        private void runPurge() throws Exception {
            controller.load();
            final ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(taskScheduler).scheduleAtFixedRate(task.capture(), any(Instant.class), any(Duration.class));
            task.getValue().run();
        }
    }

    @Nested
    class Shutdown {
        @Test
        void shutdown_shouldCancelPurge() throws Exception {
            controller.load();

            controller.shutdown();

            verify(purgeTask).cancel(false);
        }
    }
}
