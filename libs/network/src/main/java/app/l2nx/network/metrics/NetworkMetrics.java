/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.metrics;

import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.metrics.model.Direction;
import app.l2nx.network.metrics.model.DropReason;
import app.l2nx.network.transport.NetworkEventLoops;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.netty4.NettyAllocatorMetrics;
import io.micrometer.core.instrument.binder.netty4.NettyEventExecutorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufAllocatorMetricProvider;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class NetworkMetrics {
    public static final AttributeKey<NetworkMetrics> KEY = AttributeKey.valueOf("l2nx.network.metrics");

    private final MeterRegistry registry;
    private final AtomicInteger connections = new AtomicInteger();
    private final Map<Direction, Counter> bytes = new EnumMap<>(Direction.class);
    private final Map<DropReason, Counter> dropped = new EnumMap<>(DropReason.class);
    private final Map<CloseReason, Counter> closed = new EnumMap<>(CloseReason.class);
    private final ClassValue<Counter> commands;
    private final ClassValue<Map<Direction, Counter>> legacyPackets;
    private final Map<String, Counter> decodeErrors = new ConcurrentHashMap<>();
    private final Map<EventExecutor, Counter> handoffs = new ConcurrentHashMap<>();
    private final Counter unwritable;

    public NetworkMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("l2nx.network.connections", connections, AtomicInteger::get)
                .description("Open client and GS-link connections")
                .register(registry);
        for (Direction direction : Direction.values()) {
            bytes.put(
                    direction,
                    Counter.builder("l2nx.network.bytes")
                            .baseUnit("bytes")
                            .tag("direction", direction.tag())
                            .register(registry));
        }
        for (DropReason reason : DropReason.values()) {
            dropped.put(
                    reason,
                    Counter.builder("l2nx.network.dropped")
                            .tag("reason", reason.tag())
                            .register(registry));
        }
        for (CloseReason reason : CloseReason.values()) {
            closed.put(
                    reason,
                    Counter.builder("l2nx.network.closed")
                            .tag("reason", reason.tag())
                            .register(registry));
        }
        commands = new ClassValue<>() {
            @Override
            protected Counter computeValue(Class<?> type) {
                return Counter.builder("l2nx.network.commands")
                        .tag("type", type.getName())
                        .register(registry);
            }
        };
        legacyPackets = new ClassValue<>() {
            @Override
            protected Map<Direction, Counter> computeValue(Class<?> type) {
                final Map<Direction, Counter> counters = new EnumMap<>(Direction.class);
                for (Direction direction : Direction.values()) {
                    counters.put(
                            direction,
                            Counter.builder("l2nx.network.legacy.packets")
                                    .tag("type", type.getName())
                                    .tag("direction", direction.tag())
                                    .register(registry));
                }
                return counters;
            }
        };
        unwritable = Counter.builder("l2nx.network.unwritable")
                .description("Times a connection became unwritable")
                .register(registry);
    }

    public static NetworkMetrics noop() {
        return new NetworkMetrics(new SimpleMeterRegistry());
    }

    public void connectionOpened() {
        connections.incrementAndGet();
    }

    public void connectionClosed() {
        connections.decrementAndGet();
    }

    public int connections() {
        return connections.get();
    }

    public void bytes(Direction direction, int count) {
        bytes.get(direction).increment(count);
    }

    public void command(Class<?> type) {
        commands.get(type).increment();
    }

    public void legacyPacket(Class<?> type, Direction direction) {
        legacyPackets.get(type).get(direction).increment();
    }

    public void decodeError(String state) {
        decodeErrors
                .computeIfAbsent(
                        state,
                        s -> Counter.builder("l2nx.network.decode.errors")
                                .tag("state", s)
                                .register(registry))
                .increment();
    }

    public void dropped(DropReason reason) {
        dropped.get(reason).increment();
    }

    public void closed(CloseReason reason) {
        closed.get(reason).increment();
    }

    public void unwritable() {
        unwritable.increment();
    }

    /**
     * Called once per connection, on its event loop, where the loop's thread is known without blocking. Tags follow
     * Micrometer's Netty binder, so the series joins {@code netty.eventexecutor.tasks.pending}.
     */
    public Counter handoffs(EventExecutor executor) {
        return handoffs.computeIfAbsent(
                executor,
                e -> Counter.builder("l2nx.network.outbound.handoffs")
                        .description("Outbound drains handed to an event loop")
                        .tag("group", "io")
                        .tag("name", threadName(e))
                        .register(registry));
    }

    private static String threadName(EventExecutor executor) {
        return executor instanceof SingleThreadEventExecutor single
                ? single.threadProperties().name()
                : executor.getClass().getSimpleName();
    }

    public void bindEventLoops(NetworkEventLoops loops) {
        new NettyEventExecutorMetrics(loops.boss(), Tags.of("group", "boss")).bindTo(registry);
        new NettyEventExecutorMetrics(loops.workers(), Tags.of("group", "io")).bindTo(registry);
    }

    public void bindAllocator(ByteBufAllocator allocator) {
        if (allocator instanceof ByteBufAllocatorMetricProvider provider) {
            new NettyAllocatorMetrics(provider).bindTo(registry);
        }
    }
}
