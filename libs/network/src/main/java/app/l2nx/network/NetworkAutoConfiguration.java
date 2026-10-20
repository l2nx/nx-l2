/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network;

import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.transport.NetworkEventLoops;
import app.l2nx.network.transport.TransportResolver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.ByteBufAllocator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(NetworkProperties.class)
public class NetworkAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public NetworkEventLoops networkEventLoops(NetworkProperties properties) {
        return new NetworkEventLoops(properties, TransportResolver.nativeAvailability());
    }

    // A provider keeps the bean independent of metrics auto-configuration order; no registry still starts.
    @Bean
    @ConditionalOnMissingBean
    public NetworkMetrics networkMetrics(ObjectProvider<MeterRegistry> registry, NetworkEventLoops loops) {
        final NetworkMetrics metrics = new NetworkMetrics(registry.getIfAvailable(SimpleMeterRegistry::new));
        metrics.bindEventLoops(loops);
        metrics.bindAllocator(ByteBufAllocator.DEFAULT);
        return metrics;
    }
}
