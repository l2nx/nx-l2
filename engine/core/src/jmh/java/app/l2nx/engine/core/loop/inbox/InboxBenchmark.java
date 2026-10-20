/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.inbox;

import java.util.concurrent.TimeUnit;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.jctools.queues.atomic.MpscUnboundedAtomicArrayQueue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Group)
public class InboxBenchmark {
    private static final Object ITEM = new Object();
    private static final int CHUNK_SIZE = 1024;
    private static final int MAX_DEPTH = 1 << 16;

    @Param({"atomic", "unsafe", "inbox"})
    public String queue;

    private Inbox<Object> inbox;
    private MessagePassingQueue<Object> raw;

    @Setup
    public void setUp() {
        switch (queue) {
            case "atomic" -> raw = new MpscUnboundedAtomicArrayQueue<>(CHUNK_SIZE);
            case "unsafe" -> raw = new MpscUnboundedArrayQueue<>(CHUNK_SIZE);
            case "inbox" -> inbox = new Inbox<>();
            default -> throw new IllegalArgumentException("Unknown queue: " + queue);
        }
    }

    @Benchmark
    @Group("mpsc")
    @GroupThreads(3)
    public boolean offer() {
        if (inbox != null) {
            return inbox.size() < MAX_DEPTH && inbox.offer(ITEM);
        }
        return raw.size() < MAX_DEPTH && raw.offer(ITEM);
    }

    @Benchmark
    @Group("mpsc")
    @GroupThreads(1)
    public Object poll() {
        return inbox != null ? inbox.poll() : raw.poll();
    }
}
