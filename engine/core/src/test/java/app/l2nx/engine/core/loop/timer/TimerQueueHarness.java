/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives a {@link TimerQueue} and a {@link TimerQueueModel} per time base with the same actions; after every pass both
 * fired the same ids in the same order and agree on the next deadline, the size and the overdue count.
 */
final class TimerQueueHarness {
    static final int OVERDUE_CAP = 3;
    static final long PASS_MIN = 5;

    private final Map<TimeBase, Side> sides = new EnumMap<>(TimeBase.class);
    private long gameNow = 1_000;
    private long wallNow = 1_000_000;
    private int nextId;

    TimerQueueHarness() {
        for (TimeBase base : TimeBase.values()) {
            sides.put(base, new Side());
        }
    }

    void schedule(TimeBase base, long delay, long period, Long childDelay) {
        sides.get(base).schedule(nextId++, now(base) + delay, period, childDelay);
    }

    void cancel(TimeBase base, int index) {
        sides.get(base).cancel(index);
    }

    void advance(long game, long wall) {
        gameNow += game;
        wallNow += wall;
    }

    void wallSetback(long back) {
        wallNow -= back;
    }

    void runPass() {
        sides.get(TimeBase.GAME).runDue(gameNow);
        sides.get(TimeBase.WALL).runDue(wallNow);
    }

    private long now(TimeBase base) {
        return base == TimeBase.GAME ? gameNow : wallNow;
    }

    @Override
    public String toString() {
        return "TimerQueueHarness[game=" + gameNow + ", wall=" + wallNow + "]";
    }

    private static final class Side {
        private final TimerQueue queue = new TimerQueue((phase, source, failure) -> {
            throw failure;
        });
        private final TimerQueueModel model = new TimerQueueModel();
        private final Map<Integer, TimerEntry> entries = new HashMap<>();
        private final List<Integer> ids = new ArrayList<>();
        private final List<Integer> fired = new ArrayList<>();

        void schedule(int id, long deadline, long period, Long childDelay) {
            entries.put(id, queue.schedule(deadline, spec(id, period, childDelay)));
            model.schedule(id, deadline, period, childDelay);
            ids.add(id);
        }

        void cancel(int index) {
            if (ids.isEmpty()) {
                return;
            }
            final int id = ids.get(index % ids.size());
            final boolean actual = queue.cancel(entries.get(id));
            assertThat(actual).as("cancel of %d", id).isEqualTo(model.cancel(id));
        }

        void runDue(long now) {
            fired.clear();
            queue.runDue(null, now, OVERDUE_CAP, now - PASS_MIN);
            final List<Integer> expected = model.runDue(now, OVERDUE_CAP, now - PASS_MIN);
            assertThat(fired).as("fired at %d", now).isEqualTo(expected);
            assertThat(queue.nextDeadline()).as("next deadline after %d", now).isEqualTo(model.nextDeadline());
            assertThat(queue.size()).as("size after %d", now).isEqualTo(model.size());
            assertThat(queue.getLastOverdue()).as("overdue at %d", now).isEqualTo(model.lastOverdue());
        }

        // The child is a handler arming a timer while timers fire, at, before or after its own deadline.
        private TimerSpec spec(int id, long period, Long childDelay) {
            return TimerSpec.builder()
                    .handler((loop, entry) -> {
                        fired.add(id);
                        if (childDelay != null) {
                            final int child = id + TimerQueueModel.CHILD;
                            entries.put(child, queue.schedule(entry.getDeadline() + childDelay, spec(child, 0, null)));
                            ids.add(child);
                        }
                    })
                    .target(id)
                    .period(period)
                    .build();
        }
    }
}
