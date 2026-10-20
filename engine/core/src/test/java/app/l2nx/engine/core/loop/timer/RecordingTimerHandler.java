/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import app.l2nx.engine.core.loop.Loop;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Records each fired entry as {@code "<target>:<kind>@<deadline>"}; a target with a set generation drops others. */
public final class RecordingTimerHandler implements TimerHandler {
    public final List<String> fired = new ArrayList<>();
    private final Map<Integer, Integer> generations = new HashMap<>();

    public void setGeneration(int target, int generation) {
        generations.put(target, generation);
    }

    public TimerSpec spec(int target, TimerKind kind) {
        return TimerSpec.builder().handler(this).target(target).kind(kind).build();
    }

    public TimerSpec periodic(int target, long period) {
        return TimerSpec.builder()
                .handler(this)
                .target(target)
                .kind(TimerKind.TICK)
                .period(period)
                .build();
    }

    @Override
    public boolean isCurrent(Loop loop, TimerEntry entry) {
        final Integer generation = generations.get(entry.getSpec().target());
        return generation == null || generation == entry.getSpec().generation();
    }

    @Override
    public void onTimer(Loop loop, TimerEntry entry) {
        fired.add(entry.getSpec().target() + ":" + entry.getSpec().kind() + "@" + entry.getDeadline());
    }
}
