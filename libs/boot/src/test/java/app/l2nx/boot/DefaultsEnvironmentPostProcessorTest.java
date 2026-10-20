/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.boot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Runs the real environment preparation, so the defaults meet the application's own config data (test application.yaml).
 */
// Starting two Spring contexts at once races the logging system initialisation.
@Execution(ExecutionMode.SAME_THREAD)
class DefaultsEnvironmentPostProcessorTest {
    @Nested
    class PostProcessEnvironment {
        @Test
        void postProcessEnvironment_shouldKeepApplicationValue_whenKeyIsInBoth() {
            try (ConfigurableApplicationContext context = start()) {
                assertThat(context.getEnvironment().getProperty("server.address"))
                        .isEqualTo("0.0.0.0");
            }
        }

        @Test
        void postProcessEnvironment_shouldAddDefault_whenApplicationLacksKey() {
            try (ConfigurableApplicationContext context = start()) {
                final Environment environment = context.getEnvironment();
                assertThat(environment.getProperty("spring.datasource.hikari.pool-name"))
                        .isEqualTo("l2nx-db");
            }
        }
    }

    private static ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(EmptyConfiguration.class)
                .web(WebApplicationType.NONE)
                .run();
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration {}
}
