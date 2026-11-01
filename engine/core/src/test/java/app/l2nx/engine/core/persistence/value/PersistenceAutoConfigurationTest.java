/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.clock.SystemGameClock;
import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import java.time.Clock;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

class PersistenceAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PersistenceAutoConfiguration.class))
            .withBean(GameClock.class, () -> new SystemGameClock(Clock.systemUTC()))
            .withBean(
                    TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME,
                    Executor.class,
                    () -> Runnable::run);

    @Nested
    class ValueTransactionsBean {
        @Test
        void context_shouldCreateValueTransactions_whenADatabaseIsConfigured() {
            runner.withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                    .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                    .run(context -> assertThat(context).hasSingleBean(ValueTransactions.class));
        }

        @Test
        void context_shouldStartWithoutValueTransactions_whenNoDatabaseIsConfigured() {
            runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ValueTransactions.class));
        }
    }

    @Nested
    class SubmitterBean {
        @Test
        void context_shouldCreateTheSubmitter_whenValueTransactionsAndASchedulerExist() {
            runner.withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                    .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                    .withBean(TaskScheduler.class, () -> mock(TaskScheduler.class))
                    .run(context -> {
                        assertThat(context).hasSingleBean(ValueTransactionSubmitter.class);
                        assertThat(context).hasSingleBean(BackgroundQueries.class);
                    });
        }

        @Test
        void context_shouldStartWithoutTheSubmitter_whenNoDatabaseIsConfigured() {
            runner.withBean(TaskScheduler.class, () -> mock(TaskScheduler.class))
                    .run(context ->
                            assertThat(context).hasNotFailed().doesNotHaveBean(ValueTransactionSubmitter.class));
        }
    }
}
