/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.gslink;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.grpc.client.autoconfigure.CompositeChannelFactoryAutoConfiguration;
import org.springframework.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration;
import org.springframework.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GsLinkAutoConfigurationTest {
    private static final String SECRET = "l2nx.gslink.secret=guard-test-secret-0123456789abcdef";

    @Nested
    class ServerSide {
        // Port 0: a context that starts binds an ephemeral loopback port; a refused one never binds.
        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SslAutoConfiguration.class, GrpcServerAutoConfiguration.class, GsLinkAutoConfiguration.class))
                .withUserConfiguration(EchoLoginServer.class)
                .withPropertyValues(SECRET, "spring.grpc.server.port=0");

        @Test
        void start_shouldListen_whenBoundToLoopbackWithoutTls() {
            runner.withPropertyValues("spring.grpc.server.address=127.0.0.1")
                    .run(context ->
                            assertThat(context).hasNotFailed().hasSingleBean(SharedSecretServerInterceptor.class));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"0.0.0.0", "192.0.2.10"})
        void start_shouldBeRefused_whenBoundElsewhereWithoutTls(String address) {
            runner.withPropertyValues("spring.grpc.server.address=" + address)
                    .run(context -> assertThat(context)
                            .hasFailed()
                            .getFailure()
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("spring.grpc.server.address"));
        }

        @Test
        void start_shouldBeRefused_whenNoAddressIsSetWithoutTls() {
            runner.run(context -> assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("every interface"));
        }
    }

    @Nested
    class ClientSide {
        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SslAutoConfiguration.class,
                        GrpcClientAutoConfiguration.class,
                        CompositeChannelFactoryAutoConfiguration.class,
                        GsLinkAutoConfiguration.class))
                .withPropertyValues(SECRET);

        @ParameterizedTest(name = "{0}")
        @ValueSource(
                strings = {
                    "static://127.0.0.1:9014",
                    "static://localhost:9014",
                    "127.0.0.1:9014",
                    "dns:///localhost:9014",
                    "dns://192.0.2.53/localhost:9014",
                    "tcp://127.0.0.1:9014",
                    "static://[::1]:9014"
                })
        void channel_shouldBeCreated_whenTheTargetIsLocal(String target) {
            runner.withPropertyValues("spring.grpc.client.channel.login.target=" + target)
                    .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ManagedChannel.class));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(
                strings = {
                    "static://192.0.2.10:9014",
                    "tcp://192.0.2.10:9014",
                    "login.invalid:9014",
                    "dns:///192.0.2.10:9014",
                    "dns://127.0.0.1/192.0.2.10:9014",
                    "dns:///127.0.0.1.login.invalid:9014"
                })
        void channel_shouldBeRefused_whenARemoteTargetHasNoTls(String target) {
            runner.withPropertyValues("spring.grpc.client.channel.login.target=" + target)
                    .run(context -> assertThat(context)
                            .hasFailed()
                            .getFailure()
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("spring.grpc.client.channel.login.target"));
        }

        @ParameterizedTest(name = "{0}, ssl.enabled={1}")
        @CsvSource({
            "static://login host:9014, false",
            "static://login host:9014, true",
            "in-process:gslink, false",
            "in-process:gslink, true",
            "static://:9014, false",
            "static://:9014, true",
            "dns:localhost:9014, false",
            "dns://127.0.0.1, false",
            "dns://127.0.0.1, true",
            "unix:/tmp/gslink.sock, false",
            "xds:///login:9014, true"
        })
        void channel_shouldBeRefused_whenTheTargetIsNotAHostAndPort(String target, boolean tls) {
            runner.withPropertyValues(
                            "spring.grpc.client.channel.login.target=" + target,
                            "spring.grpc.client.channel.login.ssl.enabled=" + tls)
                    .run(context -> assertThat(context)
                            .hasFailed()
                            .getFailure()
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("spring.grpc.client.channel.login.target " + target)
                            .hasMessageContaining("is not a host and port"));
        }

        @Test
        void channel_shouldBeCreated_whenARemoteTargetUsesTls() {
            runner.withPropertyValues(
                            "spring.grpc.client.channel.login.target=static://192.0.2.10:9014",
                            "spring.grpc.client.channel.login.ssl.enabled=true")
                    .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ManagedChannel.class));
        }

        @Test
        void channel_shouldBeRefused_whenCertificateValidationIsBypassed() {
            runner.withPropertyValues(
                            "spring.grpc.client.channel.login.target=static://192.0.2.10:9014",
                            "spring.grpc.client.channel.login.ssl.enabled=true",
                            "spring.grpc.client.channel.login.bypass-certificate-validation=true")
                    .run(context -> assertThat(context)
                            .hasFailed()
                            .getFailure()
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("bypass-certificate-validation"));
        }

        @Test
        void channel_shouldNotBeCreated_whenNoLoginTargetIsSet() {
            runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ManagedChannel.class));
        }
    }
}
