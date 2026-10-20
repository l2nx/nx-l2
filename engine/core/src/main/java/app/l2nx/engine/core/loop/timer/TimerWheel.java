/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import app.l2nx.engine.core.loop.FailureHandler;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.PassPhase;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.agrona.DeadlineTimerWheel;

/**
 * Agrona's 1 ms wheel holds the current turn, a bucket per later turn holds the rest, and this class orders what falls
 * due since the wheel does not order timers inside one tick. Owner thread only.
 */
public final class TimerWheel {
    static final int TICKS_PER_WHEEL = 1024;
    private static final int TURN_SHIFT = Integer.numberOfTrailingZeros(TICKS_PER_WHEEL);
    private static final int TICK_MASK = TICKS_PER_WHEEL - 1;
    private static final int WORDS = TICKS_PER_WHEEL / Long.SIZE;

    static final Comparator<TimerEntry> ORDER = Comparator.comparingLong(TimerEntry::getDeadline)
            .thenComparingLong(TimerEntry::getSequence)
            .thenComparing(entry -> entry.getSpec().kind())
            .thenComparingLong(TimerEntry::getInsertion);

    private final DeadlineTimerWheel wheel;
    private final Long2ObjectOpenHashMap<TimerEntry> armed = new Long2ObjectOpenHashMap<>();
    // Agrona exposes no earliest deadline; spoke counts plus an occupancy bitmap find the next occupied tick.
    private final int[] spokeCounts = new int[TICKS_PER_WHEEL];
    private final long[] occupancy = new long[WORDS];
    private final PriorityQueue<TimerEntry> due = new PriorityQueue<>(ORDER);
    // Deadlines before the cursor tick, which cannot move back; in the cursor spoke they would report the cursor's
    // time.
    private final PriorityQueue<TimerEntry> behind = new PriorityQueue<>(ORDER);
    // Entries of later turns, by turn; in the wheel they would share spokes with the current turn and wake the loop for
    // nothing.
    private final Long2ObjectOpenHashMap<Bucket> buckets = new Long2ObjectOpenHashMap<>();
    private final LongHeapPriorityQueue occupiedTurns = new LongHeapPriorityQueue();
    private Bucket spareBucket;
    private long aheadCount;
    private final DeadlineTimerWheel.TimerHandler collector = this::collect;
    private final FailureHandler failures;
    private long nextSequence;
    private long nextInsertion;
    // Every entry with a deadline at or before the horizon has been collected.
    private long horizon;
    // An entry was armed at or before the horizon: it is due at the next run.
    private boolean immediate;
    // The last run stopped at the overdue cap with due entries left.
    private boolean backlog;
    private long collectLateBefore;
    private int overdueCollected;
    // Sequences whose periodic entry hit the owed-tick cap, with the deadline of the tick it still owes.
    private final Long2LongOpenHashMap owedTicks = new Long2LongOpenHashMap();

    @Getter
    private int lastOverdue;

    public TimerWheel(long startTime, FailureHandler failures) {
        this.wheel = new DeadlineTimerWheel(TimeUnit.MILLISECONDS, startTime, 1, TICKS_PER_WHEEL);
        this.failures = Objects.requireNonNull(failures, "failures");
        this.horizon = startTime - 1;
        owedTicks.defaultReturnValue(Long.MIN_VALUE);
    }

    /** A sequence for entries that must keep one position, such as the TICK and END entries of one effect. */
    public long nextSequence() {
        return nextSequence++;
    }

    public TimerEntry schedule(long deadline, TimerSpec spec) {
        return schedule(deadline, spec, nextSequence());
    }

    public TimerEntry schedule(long deadline, TimerSpec spec, long sequence) {
        final TimerEntry entry =
                new TimerEntry(Objects.requireNonNull(spec, "spec"), sequence, nextInsertion++, deadline);
        arm(entry);
        return entry;
    }

    public long size() {
        return wheel.timerCount() + behind.size() + aheadCount + due.size();
    }

    /** The earliest time a run can fire something; {@link Long#MAX_VALUE} when no entry is armed. */
    public long nextDeadline() {
        if (backlog || immediate) {
            return horizon + 1;
        }
        return Math.min(Math.min(firstDeadline(behind), firstDeadlineAhead()), nextOccupiedTick(cursorTick()));
    }

    /**
     * Fires the entries due by {@code now} in {@code (deadline, sequence, kind)} order. At most {@code overdueCap}
     * entries with a deadline before {@code lateBefore} are taken per run, and at most {@code owedCap} ticks per
     * periodic entry; the rest waits for the next run.
     */
    public int runDue(Loop loop, long now, int overdueCap, int owedCap, long lateBefore) {
        collectDue(now, overdueCap, lateBefore);
        int fired = 0;
        int overdue = 0;
        TimerEntry entry;
        while ((entry = due.poll()) != null) {
            if (!isCurrent(loop, entry)) {
                continue;
            }
            if (entry.getSpec().kind() == TimerKind.END && owesTickBefore(entry)) {
                // Damage and healing owed by a periodic effect are applied before it ends, not lost.
                arm(entry);
                continue;
            }
            if (entry.deadline < lateBefore) {
                overdue++;
            }
            fire(loop, entry);
            fired++;
            if (entry.isPeriodic() && isCurrent(loop, entry)) {
                rearm(entry, owedCap);
            }
        }
        lastOverdue = overdue;
        return fired;
    }

    private void rearm(TimerEntry entry, int owedCap) {
        entry.deadline += entry.getSpec().period();
        entry.owed++;
        if (entry.deadline <= horizon && entry.owed < owedCap) {
            // An owed tick of this pass: it joins the due entries in global deadline order.
            due.add(entry);
        } else {
            if (entry.deadline <= horizon) {
                owedTicks.put(entry.getSequence(), entry.deadline);
            }
            entry.owed = 0;
            arm(entry);
        }
    }

    private boolean owesTickBefore(TimerEntry end) {
        final long owed = owedTicks.get(end.getSequence());
        return owed != Long.MIN_VALUE && owed <= end.deadline;
    }

    private void collectDue(long now, int overdueCap, long lateBefore) {
        immediate = false;
        collectLateBefore = lateBefore;
        overdueCollected = 0;
        if (!collectBehind(now, overdueCap)) {
            return;
        }
        long tick = cursorTick();
        if (tick > now) {
            // The same time again, or a wall clock set back: every entry of the wheel is at or after the cursor.
            horizon = now;
            backlog = false;
            return;
        }
        while (tick <= now) {
            if (spokeCounts[spokeOf(tick)] == 0) {
                // With the wheel empty the next entry is the first one ahead, which may be turns away.
                final long occupied = wheel.timerCount() == 0 ? firstDeadlineAhead() : nextOccupiedTick(tick);
                if (occupied > now) {
                    break;
                }
                // Safe to wind forward: every skipped spoke is empty and every entry ahead is at or after it.
                advanceTo(occupied);
                tick = occupied;
            }
            // The cap is checked between ticks, never inside one, so (deadline, sequence) order holds across passes.
            if (overdueCollected >= overdueCap) {
                horizon = tick - 1;
                backlog = true;
                return;
            }
            // Polling with the tick's own end expires only that tick's deadlines, so ticks are taken in order.
            wheel.poll(tick + 1, collector, Integer.MAX_VALUE);
            enterTurn();
            tick = cursorTick();
        }
        advanceTo(now + 1);
        horizon = now;
        backlog = false;
    }

    /**
     * Takes due entries behind the cursor one millisecond at a time, like wheel ticks; {@code false} when the overdue
     * cap stopped it and the wheel must wait.
     */
    private boolean collectBehind(long now, int overdueCap) {
        TimerEntry first;
        while ((first = behind.peek()) != null && first.deadline <= now) {
            if (overdueCollected >= overdueCap) {
                horizon = first.deadline - 1;
                backlog = true;
                return false;
            }
            final long deadline = first.deadline;
            while ((first = behind.peek()) != null && first.deadline == deadline) {
                take(behind.poll());
            }
        }
        return true;
    }

    private boolean collect(TimeUnit timeUnit, long now, long timerId) {
        final TimerEntry entry = armed.remove(timerId);
        release(spokeOfTimer(timerId));
        take(entry);
        return true;
    }

    private void take(TimerEntry entry) {
        if (entry.deadline < collectLateBefore) {
            overdueCollected++;
        }
        if (entry.isPeriodic()) {
            owedTicks.remove(entry.getSequence());
        }
        due.add(entry);
    }

    private void arm(TimerEntry entry) {
        final long cursor = cursorTick();
        if (entry.deadline < cursor) {
            behind.add(entry);
        } else if (turnOf(entry.deadline) != turnOf(cursor)) {
            armAhead(entry);
        } else {
            armWheel(entry);
        }
        if (entry.deadline <= horizon) {
            immediate = true;
        }
    }

    private void armWheel(TimerEntry entry) {
        final long timerId = wheel.scheduleTimer(entry.deadline);
        armed.put(timerId, entry);
        occupy(spokeOfTimer(timerId));
    }

    private void advanceTo(long tick) {
        wheel.currentTickTime(tick);
        enterTurn();
    }

    private void armAhead(TimerEntry entry) {
        final long turn = turnOf(entry.deadline);
        Bucket bucket = buckets.get(turn);
        if (bucket == null) {
            bucket = spareBucket == null ? new Bucket() : spareBucket;
            spareBucket = null;
            buckets.put(turn, bucket);
            occupiedTurns.enqueue(turn);
        }
        bucket.entries.add(entry);
        bucket.first = Math.min(bucket.first, entry.deadline);
        aheadCount++;
    }

    // The cursor never passes a turn with a bucket: it winds at most to a bucket's first deadline.
    private void enterTurn() {
        if (occupiedTurns.isEmpty() || occupiedTurns.firstLong() != turnOf(cursorTick())) {
            return;
        }
        final Bucket bucket = buckets.remove(occupiedTurns.dequeueLong());
        final ArrayList<TimerEntry> entries = bucket.entries;
        for (int i = 0, n = entries.size(); i < n; i++) {
            armWheel(entries.get(i));
        }
        aheadCount -= entries.size();
        entries.clear();
        bucket.first = Long.MAX_VALUE;
        spareBucket = bucket;
    }

    private long firstDeadlineAhead() {
        return occupiedTurns.isEmpty() ? Long.MAX_VALUE : buckets.get(occupiedTurns.firstLong()).first;
    }

    private static long firstDeadline(PriorityQueue<TimerEntry> entries) {
        final TimerEntry first = entries.peek();
        return first == null ? Long.MAX_VALUE : first.deadline;
    }

    private boolean isCurrent(Loop loop, TimerEntry entry) {
        try {
            return entry.getSpec().handler().isCurrent(loop, entry);
        } catch (RuntimeException e) {
            failures.onFailure(PassPhase.DEADLINES, entry, e);
            return false;
        }
    }

    private void fire(Loop loop, TimerEntry entry) {
        try {
            entry.getSpec().handler().onTimer(loop, entry);
        } catch (RuntimeException e) {
            failures.onFailure(PassPhase.DEADLINES, entry, e);
        }
    }

    private void occupy(int spoke) {
        if (spokeCounts[spoke]++ == 0) {
            occupancy[spoke >>> 6] |= 1L << spoke;
        }
    }

    private void release(int spoke) {
        if (--spokeCounts[spoke] == 0) {
            occupancy[spoke >>> 6] &= ~(1L << spoke);
        }
    }

    private long nextOccupiedTick(long fromTick) {
        if (wheel.timerCount() == 0) {
            return Long.MAX_VALUE;
        }
        final int start = spokeOf(fromTick);
        int word = start >>> 6;
        // The first word only from the start spoke on; after a full turn the same word again, all of it.
        long bits = occupancy[word] & (-1L << start);
        for (int i = 0; i <= WORDS; i++) {
            if (bits != 0) {
                final int spoke = (word << 6) + Long.numberOfTrailingZeros(bits);
                return fromTick + ((spoke - start) & TICK_MASK);
            }
            word = (word + 1) & (WORDS - 1);
            bits = occupancy[word];
        }
        return Long.MAX_VALUE;
    }

    // Agrona's current tick time is the end of the cursor tick; with a 1 ms resolution the tick starts 1 ms earlier.
    private long cursorTick() {
        return wheel.currentTickTime() - 1;
    }

    private long turnOf(long tick) {
        return (tick - wheel.startTime()) >> TURN_SHIFT;
    }

    private int spokeOf(long tick) {
        return (int) ((tick - wheel.startTime()) & TICK_MASK);
    }

    private static int spokeOfTimer(long timerId) {
        return (int) (timerId >>> 32);
    }

    private static final class Bucket {
        private final ArrayList<TimerEntry> entries = new ArrayList<>();
        private long first = Long.MAX_VALUE;
    }
}
