/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.config;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.network.NetworkAutoConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

class NetworkPropertiesTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(NetworkAutoConfiguration.class));

    @Test
    void bind_shouldApplySpecDefaults_whenNothingIsSet() {
        runner.run(context -> {
            final NetworkProperties actual = context.getBean(NetworkProperties.class);

            assertThat(actual).isEqualTo(NetworkProperties.defaults());
            assertThat(actual.transport()).isEqualTo(Transport.AUTO);
            assertThat(actual.maxFrame()).isEqualTo(DataSize.ofKilobytes(16));
            assertThat(actual.readTimeout()).isEqualTo(Duration.ofSeconds(120));
            assertThat(actual.pingInterval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(actual.pingMisses()).isEqualTo(3);
            assertThat(actual.handshakeTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(actual.authTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(actual.inboundQueueLimit()).isEqualTo(256);
            assertThat(actual.packetsPerDrain()).isEqualTo(16);
            assertThat(actual.packetsPerSecond()).isEqualTo(200);
            assertThat(actual.decodeErrorLimit())
                    .isEqualTo(NetworkProperties.DecodeErrorLimit.builder()
                            .count(10)
                            .window(Duration.ofSeconds(60))
                            .build());
            assertThat(actual.writeBufferLow()).isEqualTo(DataSize.ofKilobytes(32));
            assertThat(actual.writeBufferHigh()).isEqualTo(DataSize.ofKilobytes(128));
            assertThat(actual.outboundLimit()).isEqualTo(DataSize.ofMegabytes(1));
            assertThat(actual.slowClientTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(actual.connectionsPerIp()).isEqualTo(10);
            assertThat(actual.acceptPerIpPerSecond()).isEqualTo(5);
            assertThat(actual.maxConnections()).isEqualTo(10_000);
            assertThat(actual.gslink().secret()).isNull();
            assertThat(actual.gslink().tls()).isFalse();
            assertThat(actual.gslink().pingInterval()).isEqualTo(Duration.ofSeconds(15));
            assertThat(actual.gslink().reconnectDelay())
                    .isEqualTo(NetworkProperties.ReconnectDelay.builder()
                            .initial(Duration.ofSeconds(1))
                            .max(Duration.ofSeconds(30))
                            .build());
            assertThat(actual.shutdownQuietPeriod()).isEqualTo(Duration.ofSeconds(2));
            assertThat(actual.shutdownTimeout()).isEqualTo(Duration.ofSeconds(15));
        });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "l2nx.network.max-frame=0",
                "l2nx.network.ping-misses=0",
                "l2nx.network.inbound-queue-limit=0",
                "l2nx.network.packets-per-drain=0",
                "l2nx.network.packets-per-second=0",
                "l2nx.network.connections-per-ip=0",
                "l2nx.network.max-connections=0",
                "l2nx.network.worker-threads=-1",
                "l2nx.network.read-timeout=0s",
                "l2nx.network.shutdown-timeout=0s",
                "l2nx.network.shutdown-quiet-period=-1s"
            })
    void bind_shouldFailStartup_whenValueIsInvalid(String property) {
        runner.withPropertyValues(property)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "l2nx.network.write-buffer-low=256KB",
                "l2nx.network.write-buffer-low=-1B",
                "l2nx.network.write-buffer-high=3GB",
                "l2nx.network.shutdown-quiet-period=20s"
            })
    void bind_shouldFailStartup_whenCrossFieldRuleIsViolated(String property) {
        runner.withPropertyValues(property)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class));
    }
}
