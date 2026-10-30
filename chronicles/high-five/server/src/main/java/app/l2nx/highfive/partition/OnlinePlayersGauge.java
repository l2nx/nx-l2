/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.PassPhase;
import app.l2nx.highfive.entity.World;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Published by the world loop each pass, so a scrape never reads the player map from its own thread. */
@Component
class OnlinePlayersGauge {
    private volatile int online;

    OnlinePlayersGauge(MeterRegistry registry) {
        Gauge.builder("l2nx.players.online", this, gauge -> gauge.online)
                .description("Players in the world, offline traders included")
                .strongReference(true)
                .register(registry);
    }

    void install(Loop world) {
        world.addHook(PassPhase.FLUSH, (loop, now) -> online = World.getPlayersCount());
    }
}
