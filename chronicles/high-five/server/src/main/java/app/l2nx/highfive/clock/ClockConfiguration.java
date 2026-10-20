/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.clock;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.clock.SystemGameClock;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {
    @Bean
    GameClock gameClock() {
        return new SystemGameClock(Clock.systemDefaultZone());
    }
}
