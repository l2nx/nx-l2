/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import app.l2nx.engine.core.loop.FailureHandler;
import app.l2nx.engine.core.loop.PassPhase;
import app.l2nx.engine.core.session.command.Command;
import app.l2nx.network.metrics.RunTimers;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code l2nx.legacy.packet} keeps the name, tags and description of the packet executor's meter, so its series
 * continue; the drain's failures use the loop's {@code l2nx.engine.failures} with the drain as the loop.
 */
@Slf4j
public final class SessionMetrics {
    public static final String COMMAND = "l2nx.session.command";
    public static final String LEGACY_PACKET = "l2nx.legacy.packet";
    public static final String FAILURES = "l2nx.engine.failures";
    public static final String REJECTED = "l2nx.session.rejected";
    public static final String DRAIN = "l2nx-game-packets";
    public static final String QUEUE_DEPTH = "l2nx.session.queue.depth";

    private final RunTimers commands;
    private final RunTimers legacyPackets;
    private final Counter inboxFailures;
    private final MeterProvider<Counter> rejected;
    private final DistributionSummary queueDepth;

    public SessionMetrics(MeterRegistry registry, int queueLimit) {
        commands = new RunTimers(registry, COMMAND, "Run time of commands in the session drain");
        legacyPackets = new RunTimers(registry, LEGACY_PACKET, "Run time of client packets");
        inboxFailures = Counter.builder(FAILURES)
                .tag("loop", DRAIN)
                .tag("phase", PassPhase.INBOX.tag())
                .register(registry);
        rejected = Counter.builder(REJECTED)
                .description("Session inputs not run because the game context they need is missing")
                .withRegistry(registry);
        // No session tag: one series per connection would be unbounded.
        queueDepth = DistributionSummary.builder(QUEUE_DEPTH)
                .description("Items in a session queue right after an enqueue")
                .publishPercentileHistogram()
                .maximumExpectedValue((double) queueLimit)
                .register(registry);
    }

    public static SessionMetrics noop() {
        return new SessionMetrics(new SimpleMeterRegistry(), Integer.MAX_VALUE);
    }

    void command(Command command, Runnable dispatch) {
        commands.run(command.getClass(), dispatch);
    }

    void legacyPacket(Runnable packet) {
        legacyPackets.run(packet.getClass(), packet);
    }

    void queued(int depth) {
        queueDepth.record(depth);
    }

    // Rare by design, so the per-call meter lookup is fine.
    void rejected(Object item, String context) {
        rejected.withTags(RunTimers.TYPE_TAG, item.getClass().getName(), "context", context)
                .increment();
    }

    public FailureHandler drainFailures() {
        return (phase, source, failure) -> {
            inboxFailures.increment();
            log.error("{} failed in the {} phase of the session drain", source, phase.tag(), failure);
        };
    }
}
