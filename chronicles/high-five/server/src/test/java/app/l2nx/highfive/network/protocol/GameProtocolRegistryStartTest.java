/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.network.protocol.highfive.HighFiveAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GameProtocolRegistryStartTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withBean(HighFiveAdapter.class)
            .withBean("secondAdapter", GameProtocolAdapter.class, () -> new StubAdapter("second", 1, 0))
            .withBean(GameProtocolRegistry.class);

    @Test
    void start_shouldFail_whenASecondAdapterJoinsTheLegacyRoutes() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("refuses a second protocol adapter");
        });
    }

    @Test
    void start_shouldRegisterBothAdapters_whenATestAllowsIt() {
        runner.withPropertyValues("l2nx.game.protocol.allow-legacy-with-multiple-adapters=true")
                .run(context -> assertThat(
                                context.getBean(GameProtocolRegistry.class).adapters())
                        .extracting(GameProtocolAdapter::id)
                        .containsExactlyInAnyOrder(HighFiveAdapter.ID, "second"));
    }

    @EnableConfigurationProperties(GameProtocolProperties.class)
    static class PropertiesConfiguration {}
}
