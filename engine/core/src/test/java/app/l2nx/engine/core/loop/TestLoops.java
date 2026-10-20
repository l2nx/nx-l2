/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.replay.PassRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.ZoneOffset;

final class TestLoops {
    static final long T0 = GameClock.ORIGIN_MILLIS;
    static final Instant WALL_START = Instant.parse("2026-10-19T12:00:00Z");
    static final long SEED = 42L;

    private TestLoops() {}

    static ManualGameClock clock() {
        return new ManualGameClock(WALL_START, ZoneOffset.UTC);
    }

    static Loop loop(GameClock clock) {
        return loop("test", clock, new SimpleMeterRegistry());
    }

    static Loop loop(String name, GameClock clock, MeterRegistry registry) {
        return new Loop(name, clock, SEED, EngineProperties.defaults(), registry, PassRecorder.NOOP);
    }
}
