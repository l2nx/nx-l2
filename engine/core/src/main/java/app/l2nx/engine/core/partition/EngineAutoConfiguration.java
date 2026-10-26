/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.partition;

import app.l2nx.boot.ServerExit;
import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.security.SecureRandom;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(EngineProperties.class)
public class EngineAutoConfiguration {
    // Stop timeout equals Spring's shutdown-phase timeout so both give up on a hanging hook together.
    @Bean
    @ConditionalOnMissingBean
    public PartitionRuntime partitionRuntime(
            EngineProperties properties,
            GameClock clock,
            ObjectProvider<MeterRegistry> registry,
            ObjectProvider<LifecycleProperties> lifecycle,
            ServerExit serverExit) {
        final SecureRandom seeds = new SecureRandom();
        return new PartitionRuntime(
                properties,
                clock,
                registry.getIfAvailable(() -> Metrics.globalRegistry),
                seeds::nextLong,
                serverExit,
                lifecycle.getIfAvailable(LifecycleProperties::new).getTimeoutPerShutdownPhase());
    }
}
