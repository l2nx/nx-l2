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
import app.l2nx.load.report.model.ServerWindow;
import app.l2nx.load.scenario.LoadScenario;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
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
     * @param serverMeters {@code null} reads no server meters, such as on the in-memory protocol
     * @throws IllegalArgumentException before anything starts, when the protocol, identities, settings or injection
     *     profile do not fit the scenario
     * @throws IllegalStateException before anything starts, when the server meters could not be read
     */
    public RunSummary execute(
            RunSettings settings,
            LoadScenario scenario,
            ClientProtocol protocol,
            IntFunction<LoadBotIdentity> identities,
            LatencyRecorder latencies,
            ServerMeterWindow serverMeters)
            throws InterruptedException {
        check(settings, scenario, protocol);
        final int bots = settings.injection().bots();
        final List<LoadBotIdentity> botIdentities = identities(bots, protocol, identities);
        if (serverMeters != null) {
            // Before any load bot exists: a run whose server meters cannot be read is not worth starting.
            serverMeters.checkReady();
        }
        final Instant startedAt = wallClock.instant();
        final RunTimeline timeline = new RunTimeline(settings.injection(), scenario.measuredPhase(), clock);
        final GeneratorLagMonitor lag = new GeneratorLagMonitor();
        final CountDownLatch stopped = new CountDownLatch(bots);
        final List<LoadBot> loadBots = new ArrayList<>(bots);
        final EventLoopGroup group = new MultiThreadIoEventLoopGroup(
                settings.eventLoopThreads(), new DefaultThreadFactory("l2nx-load"), protocol.ioHandlerFactory());
        log.info(
                "Load run {} on {} started: {} load bots, seed {}",
                scenario.id(),
                protocol.id(),
                bots,
                settings.seed());
        ServerWindow serverWindow = null;
        ServerStats server = null;
        Throwable failure = null;
        try {
            for (LoadBotIdentity identity : botIdentities) {
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
            final long drainedAt = timeline.end() + settings.intentTimeout().toNanos() + LOGOUT_ROUND_TRIP.toNanos();
            if (serverMeters != null) {
                sleepUntil(timeline.measuredStart());
                final Instant windowStart = wallClock.instant();
                serverMeters.start();
                final boolean measuredEnded = timeline.awaitMeasuredEnd(drainedAt);
                serverWindow = ServerWindow.of(windowStart, wallClock.instant());
                if (measuredEnded) {
                    server = serverMeters.finish(serverWindow);
                } else {
                    // Running the window over the drain would skew server values; the client side is unaffected.
                    log.warn(
                            "The measured phase did not end before the drain, the report has no server meters over {}",
                            serverWindow);
                }
            }
            // Load bots that close on a final state before the game may all be done long before the run's end.
            if (!stopped.await(Math.max(0, drainedAt - clock.getAsLong()), TimeUnit.NANOSECONDS)) {
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
        final RunSummary summary =
                summarize(settings, scenario, protocol, startedAt, latencies, lag, loadBots, serverWindow, server);
        log.info("Load run {} done: {} {}", summary.runId(), summary.validity(), summary.invalidReasons());
        return summary;
    }

    private static void check(RunSettings settings, LoadScenario scenario, ClientProtocol protocol) {
        if (!settings.scenario().equals(scenario.id())) {
            throw new IllegalArgumentException(
                    "Settings name scenario " + settings.scenario() + ", given " + scenario.id());
        }
        if (!settings.protocol().equals(protocol.id())) {
            throw new IllegalArgumentException(
                    "Settings name protocol " + settings.protocol() + ", given " + protocol.id());
        }
        final Set<IntentKind> lifecycle = LoadBot.lifecycleIntents(scenario.finalState());
        if (scenario.finalState().compareTo(protocol.lastState()) > 0) {
            throw new IllegalArgumentException("Protocol " + protocol.id() + " reaches up to " + protocol.lastState()
                    + ", scenario " + scenario.id() + " stops at " + scenario.finalState());
        }
        final Set<IntentKind> missing = EnumSet.noneOf(IntentKind.class);
        missing.addAll(lifecycle);
        missing.addAll(scenario.requires());
        missing.removeAll(protocol.capabilities());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Protocol " + protocol.id() + " cannot express " + missing
                    + " that scenario " + scenario.id() + " needs");
        }
    }

    private static List<LoadBotIdentity> identities(
            int bots, ClientProtocol protocol, IntFunction<LoadBotIdentity> identities) {
        final List<LoadBotIdentity> checked = new ArrayList<>(bots);
        for (int index = 0; index < bots; index++) {
            final LoadBotIdentity identity = identities.apply(index);
            if (identity.index() != index) {
                throw new IllegalArgumentException("Identity of load bot " + index + " has index " + identity.index());
            }
            try {
                protocol.checkIdentity(identity);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Protocol " + protocol.id() + " cannot carry load bot " + index + ": " + e.getMessage(), e);
            }
            checked.add(identity);
        }
        return checked;
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
        // The quiet period may run up to the timeout, so wait for both plus a margin.
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
            ServerWindow serverWindow,
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
        final int reachedFinalState =
                (int) loadBots.stream().filter(LoadBot::reachedFinalState).count();
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
                .measuredPhase(scenario.measuredPhase())
                .finalState(scenario.finalState())
                .startedAt(startedAt)
                .serverCommit(settings.serverCommit())
                .cpuAffinity(settings.cpuAffinity())
                .validity(reasons.isEmpty() ? RunValidity.VALID : RunValidity.INVALID)
                .invalidReasons(List.copyOf(reasons))
                .intents(intents)
                .steps(latencies.stepStats())
                .generatorLagP99Millis(lagP99 / NANOS_PER_MILLI)
                .timeoutShare(sent == 0 ? 0 : (double) timeouts / sent)
                .rejectedShare(sent == 0 ? 0 : (double) errors / sent)
                .stoppedShare(stoppedShare)
                .reachedFinalState(reachedFinalState)
                .serverWindow(serverWindow)
                .server(server)
                .build();
    }
}
