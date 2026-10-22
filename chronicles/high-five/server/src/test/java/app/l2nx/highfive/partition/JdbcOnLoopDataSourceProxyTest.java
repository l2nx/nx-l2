/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class JdbcOnLoopDataSourceProxyTest {
    private static final DataSource POOL = mock(DataSource.class);

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(Beans.class, JdbcOnLoopDataSourceProxy.class);

    @Nested
    class PostProcessAfterInitialization {
        @Test
        void postProcessAfterInitialization_shouldWrapTheDataSource_whenOneIsDefined() {
            runner.run(context -> {
                final DataSource actual = context.getBean(DataSource.class);

                assertThat(actual).isInstanceOf(ProxyDataSource.class);
                assertThat(((ProxyDataSource) actual).getDataSource()).isSameAs(POOL);
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        DataSource dataSource() {
            return POOL;
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
