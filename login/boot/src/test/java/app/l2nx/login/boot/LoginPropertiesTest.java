/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.boot;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.protocol.LoginVariant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LoginPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration.class);

    @Nested
    class Bind {
        @Test
        void bind_shouldStart_withShippedDefaults() {
            runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(LoginProperties.class));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(
                strings = {
                    "l2nx.login.client.host=",
                    "l2nx.login.client.max-concurrent-tasks=0",
                    "l2nx.login.game-server.port=0",
                    "l2nx.login.ban.failed-logins=0",
                    "l2nx.login.ban.duration=-1s",
                    "l2nx.login.flood-protection.max-connections-per-ip=0",
                    "l2nx.login.scheduled-restart.delay=0"
                })
        void bind_shouldFailStartup_whenValueIsInvalid(String property) {
            runner.withPropertyValues(property)
                    .run(context -> assertThat(context)
                            .hasFailed()
                            .getFailure()
                            .rootCause()
                            .isInstanceOf(BindValidationException.class));
        }

        @Test
        void bind_shouldReadVariantPorts() {
            runner.run(context -> {
                final LoginProperties.Client actual =
                        context.getBean(LoginProperties.class).client();
                assertThat(actual.ports()).containsExactly(new LoginProperties.ClientPort(2106, LoginVariant.C621));
                assertThat(actual.maxConcurrentTasks()).isEqualTo(32);
            });
        }

        // A source that lists ports replaces the shipped list whole, so each case lists complete entries.
        @ParameterizedTest(name = "port {0}")
        @ValueSource(ints = {0, 65536})
        void bind_shouldFailStartup_whenPortIsOutOfRange(int port) {
            runner.withPropertyValues(
                            "l2nx.login.client.ports[0].port=" + port, "l2nx.login.client.ports[0].variant=c621")
                    .run(context -> assertThat(context)
                            .hasFailed()
                            .getFailure()
                            .rootCause()
                            .isInstanceOf(BindValidationException.class));
        }

        @Test
        void bind_shouldFailStartup_whenTwoVariantsShareAPort() {
            runner.withPropertyValues(
                            "l2nx.login.client.ports[0].port=2106",
                            "l2nx.login.client.ports[0].variant=c621",
                            "l2nx.login.client.ports[1].port=2106",
                            "l2nx.login.client.ports[1].variant=c621")
                    .run(context -> assertThat(context)
                            .hasFailed()
                            .getFailure()
                            .rootCause()
                            .isInstanceOf(BindValidationException.class)
                            .hasMessageContaining("must not repeat a port"));
        }

        @Test
        void bind_shouldFailStartup_whenNoPortIsListed() {
            runner.withPropertyValues("l2nx.login.client.ports=")
                    .run(context -> assertThat(context)
                            .hasFailed()
                            .getFailure()
                            .rootCause()
                            .isInstanceOf(BindValidationException.class));
        }
    }

    @EnableConfigurationProperties(LoginProperties.class)
    static class PropertiesConfiguration {}
}
