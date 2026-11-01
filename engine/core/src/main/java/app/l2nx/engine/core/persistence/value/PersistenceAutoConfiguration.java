/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Value transactions exist only in a server with a database; one without (a test context) starts without them. The
 * submitter and the background queries need a scheduler too, to ask the database again after it gave no answer.
 */
@AutoConfiguration(
        after = {TaskExecutionAutoConfiguration.class, TaskSchedulingAutoConfiguration.class},
        afterName = {
            "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
            "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"
        })
public class PersistenceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(
            value = {JdbcTemplate.class, PlatformTransactionManager.class},
            name = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public ValueTransactions valueTransactions(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME) Executor executor,
            GameClock clock,
            ObjectProvider<MeterRegistry> registry) {
        return new JdbcValueTransactions(
                jdbcTemplate,
                transactionManager,
                executor,
                clock.wallClock(),
                registry.getIfAvailable(() -> Metrics.globalRegistry));
    }

    // Declared after valueTransactions: a bean condition sees only the definitions registered before it.
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({ValueTransactions.class, TaskScheduler.class})
    public ValueTransactionSubmitter valueTransactionSubmitter(
            ValueTransactions transactions, TaskScheduler scheduler) {
        return new ValueTransactionSubmitter(transactions, scheduler);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(
            value = TaskScheduler.class,
            name = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public BackgroundQueries backgroundQueries(
            @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME) Executor executor,
            TaskScheduler scheduler) {
        return new BackgroundQueries(executor, scheduler);
    }
}
