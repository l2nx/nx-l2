/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.engine.core.session.DrainScheduler;
import app.l2nx.engine.core.session.SessionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SessionDrainConfiguration {
    /**
     * The loop is looked up through the bridge on every use: it stays bound after the world stops, so a session of a
     * stopped world hears so from the loop itself.
     */
    @Bean(SessionAutoConfiguration.AUTHED_DRAIN_SCHEDULER)
    DrainScheduler authedDrainScheduler() {
        return DrainScheduler.loop(WorldPartition::loop);
    }
}
