/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import app.l2nx.load.bot.LoadBot;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.injection.RunTimeline;
import app.l2nx.load.latency.GeneratorLagMonitor;
import app.l2nx.load.latency.LatencyRecorder;
import app.l2nx.load.protocol.ClientProtocol;
import app.l2nx.load.report.ReportWriter;
import app.l2nx.load.report.model.IntentStats;
import app.l2nx.load.report.model.InvalidReason;
import app.l2nx.load.report.model.RunSummary;
import app.l2nx.load.report.model.RunValidity;
import app.l2nx.load.report.model.ServerStats;
import app.l2nx.load.scenario.LoadScenario;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class LoadRun {
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final Duration LOGOUT_ROUND_TRIP = Duration.ofSeconds(2);
    private static final Duration SHUTDOWN_QUIET_PERIOD = Duration.ofMillis(100);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final LongSupplier clock;
    private final InstantSource wallClock;

    public LoadRun() {
        this(System::nanoTime, InstantSource.system());
    }

    LoadRun(LongSupplier clock, InstantSource wallClock) {
        this.clock = clock;
        this.wallClock = wallClock;
    }

    /**
     * @param serverMeters reads the server meters; required when {@code settings.prometheusUrl()} is set, ignored
     *     otherwise
     * @throws IllegalArgumentException before anything starts, when the protocol cannot express an intent the scenario
     *     or the lifecycle needs, or the settings name another scenario or protocol
     */
    public RunSummary execute(
            RunSettings settings,
            LoadScenario scenario,
            ClientProtocol protocol,
            IntFunction<LoadBotIdentity> identities,
            LatencyRecorder latencies,
            ServerMeterWindow serverMeters)
            throws InterruptedException {
        check(settings, scenario, protocol, serverMeters);
        final int bots = settings.injection().bots();
        final Instant startedAt = wallClock.instant();
        final GeneratorLagMonitor lag = new GeneratorLagMonitor();
        final CountDownLatch stopped = new CountDownLatch(bots);
        final List<LoadBot> loadBots = new ArrayList<>(bots);
        final EventLoopGroup group = new MultiThreadIoEventLoopGroup(
                settings.eventLoopThreads(), new DefaultThreadFactory("l2nx-load"), NioIoHandler.newFactory());
        final RunTimeline timeline = new RunTimeline(settings.injection(), clock);
        log.info(
                "Load run {} on {} started: {} load bots, seed {}",
                scenario.id(),
                protocol.id(),
                bots,
                settings.seed());
        ServerStats server = null;
        Throwable failure = null;
        try {
            for (int index = 0; index < bots; index++) {
                final LoadBotIdentity identity = identities.apply(index);
                if (identity.index() != index) {
                    throw new IllegalArgumentException(
                            "Identity of load bot " + index + " has index " + identity.index());
                }
                final LoadBot bot = new LoadBot(
                        identity,
                        settings.seed(),
                        group.next(),
                        protocol,
                        scenario,
                        timeline,
                        latencies,
                        lag,
                        settings.intentTimeout(),
                        stopped::countDown);
                loadBots.add(bot);
                bot.start();
            }
            if (settings.prometheusUrl() != null) {
                server = readServerMeters(timeline, settings.scrapeInterval(), serverMeters);
            }
            sleepUntil(timeline.end());
            final long drainNanos = timeline.end()
                    + settings.intentTimeout().toNanos()
                    + LOGOUT_ROUND_TRIP.toNanos()
                    - clock.getAsLong();
            if (!stopped.await(Math.max(0, drainNanos), TimeUnit.NANOSECONDS)) {
                log.info("{} load bots did not log out in time and are closed", stopped.getCount());
            }
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            loadBots.forEach(LoadBot::close);
            try {
                shutDown(group);
            } catch (RuntimeException e) {
                if (failure == null) {
                    throw e;
                }
                failure.addSuppressed(e);
            }
        }
        final RunSummary summary = summarize(settings, scenario, protocol, startedAt, latencies, lag, loadBots, server);
        log.info("Load run {} done: {} {}", summary.runId(), summary.validity(), summary.invalidReasons());
        return summary;
    }

    private static void check(
            RunSettings settings, LoadScenario scenario, ClientProtocol protocol, ServerMeterWindow serverMeters) {
        if (!settings.scenario().equals(scenario.id())) {
            throw new IllegalArgumentException(
                    "Settings name scenario " + settings.scenario() + ", given " + scenario.id());
        }
        if (!settings.protocol().equals(protocol.id())) {
            throw new IllegalArgumentException(
                    "Settings name protocol " + settings.protocol() + ", given " + protocol.id());
        }
        final Set<IntentKind> missing = EnumSet.copyOf(LoadBot.LIFECYCLE_INTENTS);
        missing.addAll(scenario.requires());
        missing.removeAll(protocol.capabilities());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Protocol " + protocol.id() + " cannot express " + missing
                    + " that scenario " + scenario.id() + " needs");
        }
        if (settings.prometheusUrl() != null && serverMeters == null) {
            throw new IllegalArgumentException("A Prometheus URL is set but no server meter window is given");
        }
    }

    private ServerStats readServerMeters(RunTimeline timeline, Duration scrapeInterval, ServerMeterWindow meters)
            throws InterruptedException {
        sleepUntil(timeline.steadyStart());
        meters.start();
        final long interval = scrapeInterval.toNanos();
        for (long at = timeline.steadyStart() + interval; at - timeline.steadyEnd() < 0; at += interval) {
            sleepUntil(at);
            meters.sample();
        }
        sleepUntil(timeline.steadyEnd());
        return meters.finish();
    }

    private void sleepUntil(long time) throws InterruptedException {
        final long delay = time - clock.getAsLong();
        if (delay > 0) {
            TimeUnit.NANOSECONDS.sleep(delay);
        }
    }

    private static void shutDown(EventLoopGroup group) {
        final Future<?> terminated = group.shutdownGracefully(
                SHUTDOWN_QUIET_PERIOD.toMillis(), SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        // The quiet period may run up to the timeout, so the wait allows both plus a margin for the last tasks.
        final long waitMillis = SHUTDOWN_QUIET_PERIOD.toMillis() + SHUTDOWN_TIMEOUT.toMillis() + 1_000;
        // The summary reads load bot state written on the event loops: it is safe only once they terminated.
        if (!terminated.awaitUninterruptibly(waitMillis)) {
            throw new IllegalStateException("Load bot event loops did not terminate in " + waitMillis + " ms");
        }
    }

    private static RunSummary summarize(
            RunSettings settings,
            LoadScenario scenario,
            ClientProtocol protocol,
            Instant startedAt,
            LatencyRecorder latencies,
            GeneratorLagMonitor lag,
            List<LoadBot> loadBots,
            ServerStats server) {
        final Map<IntentKind, IntentStats> intents = latencies.stats();
        final long sent = intents.values().stream().mapToLong(IntentStats::sent).sum();
        final long timeouts =
                intents.values().stream().mapToLong(IntentStats::timeouts).sum();
        final long errors =
                intents.values().stream().mapToLong(IntentStats::errors).sum();
        final long stoppedOnError =
                loadBots.stream().filter(LoadBot::stoppedOnError).count();
        final double stoppedShare = (double) stoppedOnError / loadBots.size();
        final long lagP99 = lag.p99Nanos();
        final List<InvalidReason> reasons = new ArrayList<>();
        if (lagP99 > settings.maxGeneratorLagP99().toNanos()) {
            reasons.add(InvalidReason.GENERATOR_LAG);
        }
        if (stoppedShare > settings.maxStoppedShare()) {
            reasons.add(InvalidReason.LOAD_BOTS_STOPPED);
        }
        return RunSummary.builder()
                .runId(ReportWriter.runId(scenario.id(), startedAt, settings.seed()))
                .scenario(scenario.id())
                .protocol(protocol.id())
                .seed(settings.seed())
                .injection(settings.injection())
                .startedAt(startedAt)
                .serverCommit(settings.serverCommit())
                .cpuAffinity(settings.cpuAffinity())
                .validity(reasons.isEmpty() ? RunValidity.VALID : RunValidity.INVALID)
                .invalidReasons(List.copyOf(reasons))
                .intents(intents)
                .generatorLagP99Millis(lagP99 / NANOS_PER_MILLI)
                .timeoutShare(sent == 0 ? 0 : (double) timeouts / sent)
                .rejectedShare(sent == 0 ? 0 : (double) errors / sent)
                .stoppedShare(stoppedShare)
                .server(server)
                .build();
    }
}
