/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true)
public final class TimerEntry implements Comparable<TimerEntry> {
    final TimerQueue queue;

    @Getter
    @ToString.Include
    private final TimerSpec spec;

    @ToString.Include
    private final long sequence;

    @Getter
    @ToString.Include
    long deadline;

    // Pending: in the heap or scheduled during the running pass; false once it fired, was dropped stale or purged.
    boolean queued;

    @Getter(AccessLevel.PACKAGE)
    boolean cancelled;

    TimerEntry(TimerQueue queue, TimerSpec spec, long sequence, long deadline) {
        this.queue = queue;
        this.spec = spec;
        this.sequence = sequence;
        this.deadline = deadline;
    }

    boolean isPeriodic() {
        return spec.period() > 0;
    }

    @Override
    public int compareTo(TimerEntry other) {
        final int byDeadline = Long.compare(deadline, other.deadline);
        return byDeadline != 0 ? byDeadline : Long.compare(sequence, other.sequence);
    }
}
