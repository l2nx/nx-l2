/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.engine.core.session.command.CommandHandler;
import app.l2nx.engine.core.session.command.CommandRegistry;
import app.l2nx.engine.core.session.command.SessionDirectory;
import app.l2nx.network.NetworkAutoConfiguration;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.exec.BlockingTaskExecutor;
import app.l2nx.network.metrics.NetworkMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.util.function.SingletonSupplier;

@AutoConfiguration(after = NetworkAutoConfiguration.class)
public class SessionAutoConfiguration {
    public static final String AUTHED_DRAIN_SCHEDULER = "authedDrainScheduler";

    @Bean
    @ConditionalOnMissingBean
    public CommandRegistry commandRegistry(ObjectProvider<CommandHandler<?>> handlers) {
        return new CommandRegistry(handlers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionMetrics sessionMetrics(NetworkProperties properties, ObjectProvider<MeterRegistry> registry) {
        return new SessionMetrics(
                registry.getIfAvailable(() -> Metrics.globalRegistry), properties.inboundQueueLimit());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(name = "sessionDrainExecutor")
    public BlockingTaskExecutor sessionDrainExecutor(
            NetworkProperties properties, ObjectProvider<MeterRegistry> registry) {
        return new BlockingTaskExecutor(
                SessionMetrics.DRAIN,
                properties.maxConcurrentDrains(),
                registry.getIfAvailable(() -> Metrics.globalRegistry));
    }

    // The directory is resolved on the first addressed command and kept: a context without one starts, and a
    // directory that itself needs the dispatcher forms no cycle.
    @Bean
    @ConditionalOnMissingBean
    public CommandDispatcher commandDispatcher(
            CommandRegistry commandRegistry,
            ObjectProvider<SessionDirectory> sessionDirectory,
            NetworkMetrics networkMetrics) {
        final SingletonSupplier<SessionDirectory> directory = SingletonSupplier.of(sessionDirectory::getObject);
        return new CommandDispatcher(
                commandRegistry, address -> directory.obtain().find(address), networkMetrics);
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionFactory sessionFactory(
            NetworkProperties properties,
            BlockingTaskExecutor sessionDrainExecutor,
            CommandDispatcher commandDispatcher,
            SessionMetrics sessionMetrics,
            @Qualifier(AUTHED_DRAIN_SCHEDULER) ObjectProvider<DrainScheduler> authedDrainScheduler) {
        final DrainScheduler pool = DrainScheduler.pool(sessionDrainExecutor::execute, sessionMetrics.drainFailures());
        return new SessionFactory(
                Session.Limits.of(properties),
                pool,
                authedDrainScheduler.getIfAvailable(() -> pool),
                commandDispatcher,
                sessionMetrics);
    }
}
