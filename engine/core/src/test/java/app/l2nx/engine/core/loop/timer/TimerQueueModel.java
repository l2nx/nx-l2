/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reference for {@link TimerQueue}: a plain list, scanned for its first entry at every step. */
final class TimerQueueModel {
    /** Id offset of the entry a firing entry schedules from inside its handler. */
    static final int CHILD = 1_000_000;

    private static final Comparator<Entry> ORDER =
            Comparator.comparingLong((Entry entry) -> entry.deadline).thenComparingLong(entry -> entry.sequence);

    private final List<Entry> pending = new ArrayList<>();
    private final Map<Integer, Entry> byId = new HashMap<>();
    private long nextSequence;
    private int lastOverdue;

    /** {@code childDelay}: {@code null} for none, else the handler schedules a child at its deadline plus it. */
    void schedule(int id, long deadline, long period, Long childDelay) {
        pending.add(create(id, deadline, period, childDelay));
    }

    boolean cancel(int id) {
        return pending.remove(byId.get(id));
    }

    long nextDeadline() {
        return pending.stream().mapToLong(entry -> entry.deadline).min().orElse(Long.MAX_VALUE);
    }

    int size() {
        return pending.size();
    }

    int lastOverdue() {
        return lastOverdue;
    }

    List<Integer> runDue(long now, int overdueCap, long lateBefore) {
        final List<Integer> fired = new ArrayList<>();
        lastOverdue = 0;
        while (true) {
            final Entry first = pending.stream().min(ORDER).orElse(null);
            if (first == null || first.deadline > now) {
                break;
            }
            final boolean late = first.deadline < lateBefore;
            if (late && lastOverdue == overdueCap) {
                break;
            }
            pending.remove(first);
            if (late) {
                lastOverdue++;
            }
            fired.add(first.id);
            if (first.childDelay != null) {
                // Armed during the run: never before now + 1, so never in this run.
                final long deadline = Math.max(first.deadline + first.childDelay, now + 1);
                pending.add(create(first.id + CHILD, deadline, 0, null));
            }
            if (first.period > 0) {
                first.deadline += first.period * ((now - first.deadline) / first.period + 1);
                pending.add(first);
            }
        }
        return fired;
    }

    private Entry create(int id, long deadline, long period, Long childDelay) {
        final Entry entry = new Entry(id, nextSequence++, period, childDelay);
        entry.deadline = deadline;
        byId.put(id, entry);
        return entry;
    }

    private static final class Entry {
        private final int id;
        private final long sequence;
        private final long period;
        private final Long childDelay;
        private long deadline;

        private Entry(int id, long sequence, long period, Long childDelay) {
            this.id = id;
            this.sequence = sequence;
            this.period = period;
            this.childDelay = childDelay;
        }
    }
}
