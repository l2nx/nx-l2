/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.periodic;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.loop.Loop;
import java.time.Duration;
import java.util.List;

public final class TestSystem implements PeriodicSystem {
    private final String name;
    private final Duration cadence;
    private final List<String> log;

    public TestSystem(String name, Duration cadence, List<String> log) {
        this.name = name;
        this.cadence = cadence;
        this.log = log;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Duration cadence() {
        return cadence;
    }

    @Override
    public void run(Loop loop, long now) {
        log.add(name + "@" + (now - GameClock.ORIGIN_MILLIS));
    }
}
