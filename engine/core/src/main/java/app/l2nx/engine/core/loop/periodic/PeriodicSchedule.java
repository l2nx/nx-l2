/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.periodic;

import app.l2nx.engine.core.loop.FailureHandler;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.PassPhase;
import java.util.ArrayList;
import java.util.List;

/** Owner thread only. */
public final class PeriodicSchedule {
    private final List<Slot> slots = new ArrayList<>();

    public void add(PeriodicSystem system, long now, PeriodicMetrics metrics) {
        final long cadence = system.cadence().toMillis();
        if (cadence < 1) {
            throw new IllegalArgumentException(
                    "Cadence of system " + system.name() + " must be at least 1 ms: " + system.cadence());
        }
        for (Slot slot : slots) {
            if (slot.system.name().equals(system.name())) {
                throw new IllegalArgumentException("System already registered: " + system.name());
            }
        }
        slots.add(new Slot(system, cadence, metrics.system(system.name()), now + cadence));
    }

    public long nextDue() {
        long next = Long.MAX_VALUE;
        for (int i = 0; i < slots.size(); i++) {
            next = Math.min(next, slots.get(i).next);
        }
        return next;
    }

    public void runDue(Loop loop, long now, PeriodicMetrics metrics, FailureHandler failures) {
        for (int i = 0; i < slots.size(); i++) {
            final Slot slot = slots.get(i);
            if (now < slot.next) {
                continue;
            }
            final long start = metrics.monotonicTime();
            try {
                slot.system.run(loop, now);
            } catch (RuntimeException e) {
                failures.onFailure(PassPhase.SYSTEMS, slot.system, e);
            }
            slot.meters.recordRun(metrics.monotonicTime() - start);
            // Missed runs are skipped, not replayed in a burst; the next run stays on the cadence grid.
            final long missed = (now - slot.next) / slot.cadence;
            if (missed > 0) {
                slot.meters.recordSkipped(missed);
            }
            slot.next += slot.cadence * (missed + 1);
        }
    }

    private static final class Slot {
        private final PeriodicSystem system;
        private final long cadence;
        private final PeriodicMetrics.SystemMeters meters;
        private long next;

        private Slot(PeriodicSystem system, long cadence, PeriodicMetrics.SystemMeters meters, long next) {
            this.system = system;
            this.cadence = cadence;
            this.meters = meters;
            this.next = next;
        }
    }
}
