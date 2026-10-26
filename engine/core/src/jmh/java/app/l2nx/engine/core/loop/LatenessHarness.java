/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.clock.SystemGameClock;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.engine.core.loop.timer.TimerHandler;
import app.l2nx.engine.core.loop.timer.TimerQueue;
import app.l2nx.engine.core.loop.timer.TimerSpec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import lombok.Builder;

/**
 * Firing lateness ({@code firedAt - deadline}) of spread-deadline timers under background CPU load, plus wake-ups and
 * {@link ThreadMXBean} CPU time over a window that includes each round's {@value #LEAD_MILLIS} ms idle lead. The loop
 * schedules on its own thread (counted); the pool and tick thread are scheduled from the harness thread (not).
 *
 * <p>A plain {@code main}: JMH has no result type for a distribution. Run one scheduler per JVM.
 */
public final class LatenessHarness {
    static final long LEAD_MILLIS = 200;
    private static final long LEAD_NANOS = TimeUnit.MILLISECONDS.toNanos(LEAD_MILLIS);
    private static final long NANOS_PER_MILLI = 1_000_000;
    private static final double[] QUANTILES = {0.5, 0.9, 0.99, 0.999, 1.0};

    enum Scheduler {
        ZERO_TICK,
        MOBIUS_STPE,
        TICK_100MS
    }

    private LatenessHarness() {}

    public static void main(String[] args) throws InterruptedException {
        final Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            final int at = arg.indexOf('=');
            if (at < 1) {
                throw new IllegalArgumentException("Expected key=value: " + arg);
            }
            options.put(arg.substring(0, at), arg.substring(at + 1));
        }
        final int timers = Integer.parseInt(options.getOrDefault("timers", "10000"));
        final int spread = Integer.parseInt(options.getOrDefault("spread", "1000"));
        final int warmup = Integer.parseInt(options.getOrDefault("warmup", "3"));
        final int rounds = Integer.parseInt(options.getOrDefault("rounds", "10"));
        final int load = Integer.parseInt(options.getOrDefault("load", "1"));
        EngineProperties settings = EngineProperties.defaults();
        if (options.containsKey("passMin")) {
            settings = settings.toBuilder()
                    .passMin(Duration.ofMillis(Long.parseLong(options.get("passMin"))))
                    .build();
        }
        final List<Scheduler> schedulers = options.containsKey("scheduler")
                ? List.of(Scheduler.valueOf(options.get("scheduler")))
                : List.of(Scheduler.values());

        System.out.printf(
                Locale.ROOT,
                "java %s, %s %s, %d CPUs; timers %d over %d ms, %d warm-up + %d measured rounds, %d load threads,"
                        + " pass-min %d ms%n",
                Runtime.version(),
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(),
                timers,
                spread,
                warmup,
                rounds,
                load,
                settings.passMin().toMillis());
        System.out.printf(
                Locale.ROOT,
                "%-12s %10s %10s %10s %10s %10s %10s %10s %11s   %s%n",
                "scheduler",
                "p50 us",
                "p90 us",
                "p99 us",
                "p99.9 us",
                "max us",
                "wakeups/s",
                "cpu ms/s",
                "cpu us/tmr",
                "p99 per round, min..max us");

        final Load background = new Load(load);
        try {
            for (Scheduler scheduler : schedulers) {
                try (Subject subject = open(scheduler, settings)) {
                    run(scheduler, subject, timers, spread, warmup, rounds);
                }
            }
        } finally {
            background.stop();
        }
    }

    private static Subject open(Scheduler scheduler, EngineProperties settings) {
        return switch (scheduler) {
            case ZERO_TICK -> new ZeroTickSubject(settings);
            case MOBIUS_STPE -> new ExecutorSubject();
            case TICK_100MS -> new TickSubject();
        };
    }

    private static void run(Scheduler scheduler, Subject subject, int timers, int spread, int warmup, int rounds)
            throws InterruptedException {
        final long[] all = new long[timers * rounds];
        final long[] roundP99 = new long[rounds];
        long startNanos = 0;
        long startWakeups = 0;
        long startCpuNanos = 0;
        double seconds = 0;
        long wakeups = 0;
        long cpuNanos = 0;
        for (int round = 0; round < warmup + rounds; round++) {
            if (round == warmup) {
                startNanos = System.nanoTime();
                startWakeups = subject.wakeups();
                startCpuNanos = subject.cpuNanos();
            }
            final SplittableRandom random = new SplittableRandom(round);
            final long base = subject.boundary(System.nanoTime() + LEAD_NANOS);
            final long[] deadlines = new long[timers];
            for (int i = 0; i < timers; i++) {
                deadlines[i] = base + random.nextInt(spread) * NANOS_PER_MILLI;
            }
            final Round current = new Round(deadlines);
            subject.schedule(current);
            if (!current.done.await(spread + 10_000L, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(scheduler + " left " + current.done.getCount() + " timers unfired");
            }
            if (round == warmup + rounds - 1) {
                seconds = (System.nanoTime() - startNanos) / 1e9;
                wakeups = subject.wakeups() - startWakeups;
                cpuNanos = subject.cpuNanos() - startCpuNanos;
            }
            if (round >= warmup) {
                final int measured = round - warmup;
                System.arraycopy(current.lateness, 0, all, measured * timers, timers);
                final long[] sorted = current.lateness.clone();
                Arrays.sort(sorted);
                roundP99[measured] = quantile(sorted, 0.99);
            }
        }
        Arrays.sort(all);
        Arrays.sort(roundP99);
        if (all[0] < 0) {
            throw new IllegalStateException(scheduler + " fired a timer " + -all[0] + " ns early");
        }
        final StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%-12s", scheduler));
        for (double quantile : QUANTILES) {
            line.append(String.format(Locale.ROOT, " %10.1f", quantile(all, quantile) / 1_000.0));
        }
        line.append(String.format(
                Locale.ROOT,
                " %10.1f %10.2f %11.3f   %.1f..%.1f",
                wakeups / seconds,
                cpuNanos / 1e6 / seconds,
                cpuNanos / 1e3 / all.length,
                roundP99[0] / 1_000.0,
                roundP99[rounds - 1] / 1_000.0));
        System.out.println(line);
    }

    // Nearest rank, no interpolation.
    private static long quantile(long[] sorted, double quantile) {
        final int rank = (int) Math.ceil(quantile * sorted.length);
        return sorted[Math.max(0, rank - 1)];
    }

    // -1 from the bean, a thread that is gone or unmeasured, counts as nothing.
    private static long threadCpuNanos(Thread thread) {
        return Math.max(0, ManagementFactory.getThreadMXBean().getThreadCpuTime(thread.threadId()));
    }

    private static final class Round {
        private final long[] deadlines;
        private final long[] lateness;
        private final CountDownLatch done;

        private Round(long[] deadlines) {
            this.deadlines = deadlines;
            this.lateness = new long[deadlines.length];
            this.done = new CountDownLatch(deadlines.length);
        }

        private void fired(int index) {
            lateness[index] = System.nanoTime() - deadlines[index];
            done.countDown();
        }
    }

    private interface Subject extends AutoCloseable {
        /** The first instant at or after {@code nanos} this scheduler can name as a deadline. */
        long boundary(long nanos);

        void schedule(Round round);

        long wakeups();

        long cpuNanos();

        @Override
        void close() throws InterruptedException;
    }

    private static final class ZeroTickSubject implements Subject {
        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        private final long origin;
        private final Loop loop;
        private final LoopThread thread;

        private ZeroTickSubject(EngineProperties settings) {
            origin = System.nanoTime();
            final SystemGameClock clock = new SystemGameClock(Clock.systemUTC());
            loop = new Loop("lateness", clock, 1L, settings, registry);
            thread = new LoopThread("lateness", loop, registry, (failed, failure) -> {});
            thread.start();
        }

        // The origin predates the loop's clock start, which can only overstate lateness.
        @Override
        public long boundary(long nanos) {
            return origin + Math.ceilDiv(nanos - origin, NANOS_PER_MILLI) * NANOS_PER_MILLI;
        }

        @Override
        public void schedule(Round round) {
            final TimerHandler handler =
                    (owner, entry) -> round.fired(entry.getSpec().key());
            final boolean accepted = loop.offer(owner -> {
                final TimerQueue timers = owner.timers(TimeBase.GAME);
                for (int i = 0; i < round.deadlines.length; i++) {
                    final long deadline = GameClock.ORIGIN_MILLIS + (round.deadlines[i] - origin) / NANOS_PER_MILLI;
                    timers.schedule(
                            deadline,
                            TimerSpec.builder().handler(handler).key(i).build());
                }
            });
            if (!accepted || !thread.getThread().isAlive()) {
                throw new IllegalStateException("Loop thread is not running; see its log");
            }
        }

        @Override
        public long wakeups() {
            return registry.get("l2nx.engine.pass").timer().count();
        }

        @Override
        public long cpuNanos() {
            return threadCpuNanos(thread.getThread());
        }

        @Override
        public void close() throws InterruptedException {
            thread.requestStop();
            thread.join();
            thread.close();
        }
    }

    private static final class ExecutorSubject implements Subject {
        private final ClassicThreadPools pools = new ClassicThreadPools();
        private final ScheduledThreadPoolExecutor pool = pools.scheduled();

        @Override
        public long boundary(long nanos) {
            return nanos;
        }

        @Override
        public void schedule(Round round) {
            for (int i = 0; i < round.deadlines.length; i++) {
                final int index = i;
                pool.schedule(() -> round.fired(index), round.deadlines[i] - System.nanoTime(), TimeUnit.NANOSECONDS);
            }
        }

        // A task counts once it has returned, so a read right after the last timer fired may miss a few.
        @Override
        public long wakeups() {
            return pool.getCompletedTaskCount();
        }

        @Override
        public long cpuNanos() {
            return pools.cpuNanos();
        }

        @Override
        public void close() throws InterruptedException {
            ClassicThreadPools.shutdown(pool);
        }
    }

    private static final class TickSubject implements Subject {
        private static final long TICK_MILLIS = 100;

        private final Thread thread;
        private final LongAdder ticks = new LongAdder();
        private volatile Published published;
        private volatile boolean running = true;

        private TickSubject() {
            thread = Thread.ofPlatform().name("tick").daemon(true).start(this::run);
        }

        @Override
        public long boundary(long nanos) {
            return nanos;
        }

        @Override
        public void schedule(Round next) {
            final Integer[] byDeadline = new Integer[next.deadlines.length];
            for (int i = 0; i < byDeadline.length; i++) {
                byDeadline[i] = i;
            }
            Arrays.sort(byDeadline, Comparator.comparingLong(i -> next.deadlines[i]));
            published = Published.builder()
                    .round(next)
                    .order(Arrays.stream(byDeadline).mapToInt(Integer::intValue).toArray())
                    .build();
        }

        // The GameTimeController shape: the tick's end is fixed before its work, the sleep takes what is left.
        private void run() {
            Published current = null;
            int cursor = 0;
            while (running) {
                final long next = System.currentTimeMillis() + TICK_MILLIS;
                ticks.increment();
                final Published latest = published;
                if (latest != current) {
                    current = latest;
                    cursor = 0;
                }
                if (current != null) {
                    final long now = System.nanoTime();
                    final int[] order = current.order();
                    while (cursor < order.length && current.round().deadlines[order[cursor]] <= now) {
                        current.round().fired(order[cursor++]);
                    }
                }
                final long sleep = next - System.currentTimeMillis();
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }

        @Override
        public long wakeups() {
            return ticks.sum();
        }

        @Override
        public long cpuNanos() {
            return threadCpuNanos(thread);
        }

        @Override
        public void close() throws InterruptedException {
            running = false;
            thread.interrupt();
            thread.join();
        }

        @Builder(toBuilder = true)
        private record Published(Round round, int[] order) {}
    }

    private static final class Load {
        private final List<Thread> threads = new ArrayList<>();
        private volatile boolean running = true;
        // Written once at the end, so the JIT cannot drop the spin loop as dead code.
        private volatile long sink;

        private Load(int count) {
            for (int i = 0; i < count; i++) {
                threads.add(Thread.ofPlatform().name("load-" + i).daemon(true).start(this::spin));
            }
        }

        private void spin() {
            long state = Thread.currentThread().threadId();
            while (running) {
                for (int i = 0; i < 1_000; i++) {
                    state ^= state << 13;
                    state ^= state >>> 7;
                    state ^= state << 17;
                }
            }
            sink = state;
        }

        private void stop() throws InterruptedException {
            running = false;
            for (Thread thread : threads) {
                thread.join();
            }
        }
    }
}
