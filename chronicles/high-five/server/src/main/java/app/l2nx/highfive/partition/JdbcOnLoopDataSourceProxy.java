/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

// TODO: drop once E4 moves JDBC off the loop (#69).
@Component
@RequiredArgsConstructor
class JdbcOnLoopDataSourceProxy implements BeanPostProcessor {
    // A provider: a post-processor's own dependencies would be created before the post-processors that configure them.
    private final ObjectProvider<MeterRegistry> registry;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource)) {
            return ProxyDataSourceBuilder.create(beanName, dataSource)
                    .methodListener(new JdbcOnLoopListener(registry.getObject()))
                    .build();
        }
        return bean;
    }
}
