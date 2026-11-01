/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.persistence.value.PersistenceAutoConfiguration;
import app.l2nx.engine.core.persistence.value.ValueTransactions;
import app.l2nx.engine.core.session.command.CommandHandler;
import app.l2nx.engine.mail.fixture.FakeItems;
import app.l2nx.engine.mail.fixture.FakeParticipants;
import app.l2nx.engine.mail.fixture.FakeTransactions;
import app.l2nx.engine.mail.fixture.TestItemStore;
import app.l2nx.engine.mail.system.MailExpiry;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class MailAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PersistenceAutoConfiguration.class, MailAutoConfiguration.class))
            .withBean(GameClock.class, () -> new ManualGameClock(Instant.EPOCH, ZoneOffset.UTC))
            .withBean(
                    TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME,
                    TaskExecutor.class,
                    SyncTaskExecutor::new)
            .withBean(TaskScheduler.class, ThreadPoolTaskScheduler::new)
            .withBean(JdbcClient.class, () -> JdbcClient.create(new SimpleDriverDataSource()))
            .withBean(ValueTransactions.class, FakeTransactions::new);

    @Nested
    class Conditions {
        @Test
        void context_shouldHaveThePostbox_whenTheChronicleImplementsTheSpi() {
            runner.withBean(MailParticipants.class, FakeParticipants::new)
                    .withBean(MailItems.class, FakeItems::new)
                    .withBean(MailItemStore.class, () -> new TestItemStore(null))
                    .run(context -> {
                        assertThat(context).hasSingleBean(MailService.class);
                        assertThat(context).hasSingleBean(MailExpiry.class);
                        assertThat(context).hasSingleBean(MailSettings.class);
                        assertThat(context.getBeansOfType(CommandHandler.class)).hasSize(11);
                    });
        }

        @Test
        void context_shouldStartWithoutThePostbox_whenTheChronicleHasNoSpi() {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(MailService.class);
                assertThat(context).doesNotHaveBean(MailSettings.class);
            });
        }
    }
}
