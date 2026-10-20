/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.gslink;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.backoff.BackOffExecution;

class GsLinkPropertiesTest {
    private static final String SECRET = "l2nx.gslink.secret=gslink-test-secret-0123456789abcdef";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(GsLinkAutoConfiguration.class));

    @Test
    void bind_shouldApplySpecDefaults_whenOnlyTheSecretIsSet() {
        runner.withPropertyValues(SECRET).run(context -> {
            final GsLinkProperties actual = context.getBean(GsLinkProperties.class);

            assertThat(actual.secret()).isEqualTo("gslink-test-secret-0123456789abcdef");
            assertThat(actual.reconnectDelay())
                    .isEqualTo(GsLinkProperties.ReconnectDelay.builder()
                            .initial(Duration.ofSeconds(1))
                            .max(Duration.ofSeconds(30))
                            .build());
        });
    }

    @Test
    void bind_shouldAcceptTheShortestSecret_whenItHas32Characters() {
        runner.withPropertyValues("l2nx.gslink.secret=01234567890123456789012345678901")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void bind_shouldFailStartup_whenNoSecretIsSet() {
        runner.run(context ->
                assertThat(context).hasFailed().getFailure().rootCause().isInstanceOf(BindValidationException.class));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "l2nx.gslink.secret=",
                "l2nx.gslink.secret=   ",
                "l2nx.gslink.secret=two words two words two words two words",
                "l2nx.gslink.secret=пароль-пароль-пароль-пароль-пароль",
                "l2nx.gslink.secret=0123456789012345678901234567890",
                "l2nx.gslink.reconnect-delay.initial=0s",
                "l2nx.gslink.reconnect-delay.initial=40s"
            })
    void bind_shouldFailStartup_whenAValueIsInvalid(String property) {
        runner.withPropertyValues(SECRET, property)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(BindValidationException.class));
    }

    @Test
    void bind_shouldNotPrintTheSecret_whenItIsRejected() {
        runner.withPropertyValues("l2nx.gslink.secret=leaky secret leaky secret leaky secret")
                .run(context ->
                        assertThat(context).hasFailed().getFailure().rootCause().hasMessageNotContaining("leaky"));
    }

    @Test
    void toString_shouldHideTheSecret() {
        final GsLinkProperties properties = GsLinkProperties.builder()
                .secret("visible-secret-visible-secret-visible")
                .build();

        assertThat(properties.toString()).doesNotContain("visible-secret-visible-secret-visible");
    }

    @Test
    void backOff_shouldDoubleUpToTheMaximum() {
        final BackOffExecution actual = GsLinkProperties.ReconnectDelay.builder()
                .initial(Duration.ofSeconds(1))
                .max(Duration.ofSeconds(30))
                .build()
                .backOff()
                .start();

        assertThat(LongStream.generate(actual::nextBackOff).limit(7))
                .containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L);
    }
}
