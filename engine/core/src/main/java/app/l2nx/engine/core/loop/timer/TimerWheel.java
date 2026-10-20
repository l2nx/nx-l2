/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import app.l2nx.engine.core.loop.FailureHandler;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.PassPhase;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
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

    static final Comparator<TimerEntry> ORDER = Comparator.comparingLong(TimerEntry::getDueAt)
            .thenComparingLong(TimerEntry::getDeadline)
            .thenComparingLong(TimerEntry::getSequence)
            .thenComparing(entry -> entry.getSpec().kind())
            .thenComparingLong(TimerEntry::getInsertion);

    private final DeadlineTimerWheel wheel;
    private final Long2ObjectOpenHashMap<TimerEntry> armed = new Long2ObjectOpenHashMap<>();
    // Agrona exposes no earliest deadline; spoke counts plus an occupancy bitmap find the next occupied tick.
    private final int[] spokeCounts = new int[TICKS_PER_WHEEL];
    private final long[] occupancy = new long[WORDS];
    private final PriorityQueue<TimerEntry> due = new PriorityQueue<>(ORDER);
    // Behind the cursor (it cannot move back) or left by the overdue cap; totally ordered, so the cap can stop
    // anywhere.
    private final PriorityQueue<TimerEntry> behind = new PriorityQueue<>(ORDER);
    // Later turns, by turn: in the wheel they would share spokes with this turn and wake the loop for nothing.
    private final Long2ObjectOpenHashMap<Bucket> buckets = new Long2ObjectOpenHashMap<>();
    private final LongHeapPriorityQueue occupiedTurns = new LongHeapPriorityQueue();
    private Bucket spareBucket;
    private long aheadCount;
    private final DeadlineTimerWheel.TimerHandler collector = this::collect;
    private final FailureHandler failures;
    private long nextSequence;
    private long nextInsertion;
    // Every entry due at or before the horizon has been collected.
    private long horizon;
    // An entry scheduled at or before it is due 1 ms later: at its own deadline it would sort ahead of due entries and,
    // re-armed under the overdue cap, starve them.
    private long lastRunTime;
    private boolean immediate;
    private boolean backlog;
    private Loop collectLoop;
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
        this.lastRunTime = startTime - 1;
        owedTicks.defaultReturnValue(Long.MIN_VALUE);
    }

    /** A sequence for entries that must keep one position, such as the TICK and END entries of one effect. */
    public long nextSequence() {
        return nextSequence++;
    }

    public TimerEntry schedule(long deadline, TimerSpec spec) {
        return schedule(deadline, spec, nextSequence());
    }

    /**
     * An entry with a deadline at or before the last run keeps its deadline and counts as overdue, but is due 1 ms
     * after that run, behind every entry already due.
     */
    public TimerEntry schedule(long deadline, TimerSpec spec, long sequence) {
        final TimerEntry entry = new TimerEntry(
                Objects.requireNonNull(spec, "spec"),
                sequence,
                nextInsertion++,
                deadline,
                Math.max(deadline, lastRunTime + 1));
        arm(entry);
        return entry;
    }

    public long size() {
        return wheel.timerCount() + behind.size() + aheadCount + due.size();
    }

    /** The earliest time a run can fire something; {@link Long#MAX_VALUE} when no entry is armed. */
    public long nextDueAt() {
        if (backlog || immediate) {
            return horizon + 1;
        }
        return Math.min(Math.min(firstDueAt(behind), firstDueAtAhead()), nextOccupiedTick(cursorTick()));
    }

    /**
     * Called before the inbox phase can schedule. After a clock set back, the last run time drops below the new clock
     * and entries scheduled late under the old clock become due at the new one.
     */
    public void passStarted(long now) {
        if (now >= lastRunTime) {
            return;
        }
        lastRunTime = now - 1;
        horizon = Math.min(horizon, now - 1);
        final ArrayList<TimerEntry> late = new ArrayList<>();
        takeLate(late);
        for (int i = 0, n = late.size(); i < n; i++) {
            final TimerEntry entry = late.get(i);
            entry.dueAt = Math.max(entry.deadline, now);
            arm(entry);
        }
    }

    /**
     * Fires the entries due by {@code now} in {@code (due time, deadline, sequence, kind)} order. At most
     * {@code overdueCap} entries with a deadline before {@code lateBefore} are fired per run, and at most
     * {@code owedCap} ticks per periodic entry; the rest waits for the next run.
     */
    public int runDue(Loop loop, long now, int overdueCap, int owedCap, long lateBefore) {
        // Plain assignment: after a wall clock set back, new entries may again be due before the old cursor.
        lastRunTime = now;
        collectLoop = loop;
        collectDue(now, overdueCap, lateBefore);
        int fired = 0;
        int overdue = 0;
        TimerEntry entry;
        while ((entry = due.poll()) != null) {
            if (entry.deadline < lateBefore && overdue >= overdueCap) {
                // Collection takes a wheel tick whole, and late schedules share one tick.
                spill(entry);
                break;
            }
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

    private void spill(TimerEntry first) {
        horizon = Math.min(horizon, first.dueAt - 1);
        backlog = true;
        TimerEntry entry = first;
        do {
            entry.owed = 0;
            behind.add(entry);
        } while ((entry = due.poll()) != null);
    }

    private void rearm(TimerEntry entry, int owedCap) {
        entry.deadline += entry.getSpec().period();
        // The ticks of an entry scheduled late stay behind the entries due before it, in deadline order with them.
        entry.dueAt = Math.max(entry.deadline, entry.dueAt);
        entry.owed++;
        if (entry.deadline <= horizon && entry.owed < owedCap) {
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
                final long occupied = wheel.timerCount() == 0 ? firstDueAtAhead() : nextOccupiedTick(tick);
                if (occupied > now) {
                    break;
                }
                // Safe to wind forward: every skipped spoke is empty and every entry ahead is at or after it.
                advanceTo(occupied);
                tick = occupied;
            }
            // The wheel does not order a tick, so the cap is checked between ticks; firing holds it inside one.
            if (overdueCollected >= overdueCap) {
                horizon = tick - 1;
                backlog = true;
                return;
            }
            // Polling with the tick's own end expires only that tick's entries, so ticks are taken in order.
            wheel.poll(tick + 1, collector, Integer.MAX_VALUE);
            enterTurn();
            tick = cursorTick();
        }
        advanceTo(now + 1);
        horizon = now;
        backlog = false;
    }

    /**
     * Takes the entries behind the cursor that are due by {@code now} one by one, in order; {@code false} when the
     * overdue cap stopped it and the wheel must wait.
     */
    private boolean collectBehind(long now, int overdueCap) {
        TimerEntry first;
        while ((first = behind.peek()) != null && first.dueAt <= now) {
            if (overdueCollected >= overdueCap) {
                horizon = first.dueAt - 1;
                backlog = true;
                return false;
            }
            take(behind.poll());
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
        if (entry.isPeriodic()) {
            owedTicks.remove(entry.getSequence());
        }
        // A cancelled entry is dropped here, so mass cancellation does not use up the overdue cap.
        if (!isCurrent(collectLoop, entry)) {
            return;
        }
        if (entry.deadline < collectLateBefore) {
            overdueCollected++;
        }
        due.add(entry);
    }

    private void takeLate(ArrayList<TimerEntry> late) {
        for (Iterator<TimerEntry> entries = behind.iterator(); entries.hasNext(); ) {
            final TimerEntry entry = entries.next();
            if (entry.dueAt > entry.deadline) {
                entries.remove();
                late.add(entry);
            }
        }
        for (ObjectIterator<Long2ObjectMap.Entry<TimerEntry>> timers =
                        armed.long2ObjectEntrySet().fastIterator();
                timers.hasNext(); ) {
            final Long2ObjectMap.Entry<TimerEntry> timer = timers.next();
            final TimerEntry entry = timer.getValue();
            if (entry.dueAt > entry.deadline) {
                final long timerId = timer.getLongKey();
                timers.remove();
                wheel.cancelTimer(timerId);
                release(spokeOfTimer(timerId));
                late.add(entry);
            }
        }
        boolean emptied = false;
        for (Bucket bucket : buckets.values()) {
            final int before = late.size();
            bucket.entries.removeIf(entry -> entry.dueAt > entry.deadline && late.add(entry));
            if (late.size() != before) {
                aheadCount -= late.size() - before;
                bucket.firstDueAt = Long.MAX_VALUE;
                for (int i = 0, n = bucket.entries.size(); i < n; i++) {
                    bucket.firstDueAt = Math.min(bucket.firstDueAt, bucket.entries.get(i).dueAt);
                }
                emptied |= bucket.entries.isEmpty();
            }
        }
        if (emptied) {
            // The turn heap has no removal; a set back is rare, so it is rebuilt from the buckets left.
            buckets.values().removeIf(bucket -> bucket.entries.isEmpty());
            occupiedTurns.clear();
            for (LongIterator turns = buckets.keySet().iterator(); turns.hasNext(); ) {
                occupiedTurns.enqueue(turns.nextLong());
            }
        }
    }

    private void arm(TimerEntry entry) {
        final long cursor = cursorTick();
        if (entry.dueAt < cursor) {
            behind.add(entry);
        } else if (turnOf(entry.dueAt) != turnOf(cursor)) {
            armAhead(entry);
        } else {
            armWheel(entry);
        }
        if (entry.dueAt <= horizon) {
            immediate = true;
        }
    }

    private void armWheel(TimerEntry entry) {
        final long timerId = wheel.scheduleTimer(entry.dueAt);
        armed.put(timerId, entry);
        occupy(spokeOfTimer(timerId));
    }

    private void advanceTo(long tick) {
        wheel.currentTickTime(tick);
        enterTurn();
    }

    private void armAhead(TimerEntry entry) {
        final long turn = turnOf(entry.dueAt);
        Bucket bucket = buckets.get(turn);
        if (bucket == null) {
            bucket = spareBucket == null ? new Bucket() : spareBucket;
            spareBucket = null;
            buckets.put(turn, bucket);
            occupiedTurns.enqueue(turn);
        }
        bucket.entries.add(entry);
        bucket.firstDueAt = Math.min(bucket.firstDueAt, entry.dueAt);
        aheadCount++;
    }

    // The cursor never passes a turn with a bucket: it winds at most to a bucket's first due time.
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
        bucket.firstDueAt = Long.MAX_VALUE;
        spareBucket = bucket;
    }

    private long firstDueAtAhead() {
        return occupiedTurns.isEmpty() ? Long.MAX_VALUE : buckets.get(occupiedTurns.firstLong()).firstDueAt;
    }

    private static long firstDueAt(PriorityQueue<TimerEntry> entries) {
        final TimerEntry first = entries.peek();
        return first == null ? Long.MAX_VALUE : first.dueAt;
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
        private long firstDueAt = Long.MAX_VALUE;
    }
}
