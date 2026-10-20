/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.identity;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdentityConfiguration {
    // The client may still address a despawned object for a while; its handle is not reused before then.
    @Bean
    WireHandleAllocator wireHandleAllocator(
            GameClock gameClock, @Value("${l2nx.game.handle-quarantine:5m}") Duration quarantine) {
        return new WireHandleAllocator(gameClock.wallClock(), quarantine);
    }

    @Bean
    IdentityRegistry identityRegistry(WireHandleAllocator wireHandleAllocator) {
        return new IdentityRegistry(wireHandleAllocator);
    }
}
