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
import app.l2nx.load.report.model.LevelStats;
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
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.HdrHistogram.Histogram;

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

    public RunSummary execute(
            RunSettings settings,
            LoadScenario scenario,
            ClientProtocol protocol,
            IntFunction<LoadBotIdentity> identities,
            LatencyRecorder latencies,
            ServerMeterWindow serverMeters)
            throws InterruptedException {
        return execute(settings, scenario, protocol, identities, latencies, serverMeters, runId -> {});
    }

    /**
     * @param serverMeters {@code null} reads no server meters, such as on the in-memory protocol
     * @param claimRunId takes the run id before any load bot starts and throws to refuse a run whose report could not
     *     be written; the id is known only at the start
     * @throws IllegalArgumentException before anything starts, when the inputs do not fit the scenario
     * @throws IllegalStateException before anything starts, when the server meters could not be read
     */
    public RunSummary execute(
            RunSettings settings,
            LoadScenario scenario,
            ClientProtocol protocol,
            IntFunction<LoadBotIdentity> identities,
            LatencyRecorder latencies,
            ServerMeterWindow serverMeters,
            Consumer<String> claimRunId)
            throws InterruptedException {
        check(settings, scenario, protocol);
        final int bots = settings.injection().totalBots();
        final List<LoadBotIdentity> botIdentities = identities(bots, protocol, identities);
        // Before any load bot exists: a run whose server meters cannot be read is not worth starting.
        final Map<String, Instant> serverStartedAt = serverMeters == null ? null : serverMeters.checkReady();
        final Instant startedAt = wallClock.instant();
        final String runId = ReportWriter.runId(scenario.id(), startedAt, settings.seed());
        claimRunId.accept(runId);
        final RunTimeline timeline = new RunTimeline(settings.injection(), scenario.measuredPhase(), clock);
        final GeneratorLagMonitor lag = new GeneratorLagMonitor(timeline.levels());
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
        final List<ServerWindow> levelWindows = new ArrayList<>();
        final List<ServerStats> levelServers = new ArrayList<>();
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
                        settings.lifecycleTimeout(),
                        stopped::countDown);
                loadBots.add(bot);
                bot.start();
            }
            final long drainedAt = timeline.end() + settings.intentTimeout().toNanos() + LOGOUT_ROUND_TRIP.toNanos();
            if (serverMeters != null && timeline.levels() > 0) {
                sleepUntil(timeline.measuredStart());
                serverMeters.start();
                for (int level = 0; level < timeline.levels(); level++) {
                    sleepUntil(timeline.levelMeasuredStart(level));
                    final Instant levelStart = wallClock.instant();
                    sleepUntil(timeline.levelEnd(level));
                    levelWindows.add(ServerWindow.of(levelStart, wallClock.instant()));
                }
                // Values over every level would mix their loads: the levels carry the server values, the run none.
                serverMeters.end();
                // After the last level: a query between levels could outlast the next level's ramp and warm-up.
                for (ServerWindow window : levelWindows) {
                    levelServers.add(serverMeters.measure(window));
                }
            } else if (serverMeters != null) {
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
        final RunSummary summary = summarize(
                runId,
                settings,
                scenario,
                protocol,
                startedAt,
                latencies,
                lag,
                loadBots,
                serverStartedAt,
                serverWindow,
                server,
                timeline,
                levelWindows,
                levelServers);
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
        RunTimeline.check(settings.injection(), scenario.measuredPhase());
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
            String runId,
            RunSettings settings,
            LoadScenario scenario,
            ClientProtocol protocol,
            Instant startedAt,
            LatencyRecorder latencies,
            GeneratorLagMonitor lag,
            List<LoadBot> loadBots,
            Map<String, Instant> serverStartedAt,
            ServerWindow serverWindow,
            ServerStats server,
            RunTimeline timeline,
            List<ServerWindow> levelWindows,
            List<ServerStats> levelServers) {
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
        final List<LevelStats> levels = levels(settings, timeline, lag, loadBots, levelWindows, levelServers);
        return RunSummary.builder()
                .reportVersion(RunSummary.REPORT_VERSION)
                .runId(runId)
                .scenario(scenario.id())
                .protocol(protocol.id())
                .seed(settings.seed())
                .injection(settings.injection())
                .measuredPhase(scenario.measuredPhase())
                .finalState(scenario.finalState())
                .intentTimeout(settings.intentTimeout())
                .lifecycleTimeout(settings.lifecycleTimeout())
                .startedAt(startedAt)
                .serverCommit(settings.serverCommit())
                .environment(EnvironmentProbe.collect())
                .cpuAffinity(settings.cpuAffinity())
                .validity(reasons.isEmpty() ? RunValidity.VALID : RunValidity.INVALID)
                .invalidReasons(List.copyOf(reasons))
                .intents(intents)
                .steps(latencies.stepStats())
                .generatorLagP99Millis(lagP99 / NANOS_PER_MILLI)
                .timeoutShare(share(timeouts, sent))
                .rejectedShare(share(errors, sent))
                .stoppedShare(stoppedShare)
                .reachedFinalState(reachedFinalState)
                .notAtFinalStateShare((double) (loadBots.size() - reachedFinalState) / loadBots.size())
                .loggedOut(
                        (int) loadBots.stream().filter(LoadBot::logoutConfirmed).count())
                .inboundFrames(latencies.frameStats())
                .serverStartedAt(serverStartedAt)
                .serverWindow(serverWindow)
                .server(server)
                .levels(levels)
                .capacity(
                        levels == null
                                ? null
                                : CapacityJudge.judge(
                                        levels,
                                        settings.gate().passBudget(),
                                        settings.gate().maxOverBudgetShare(),
                                        settings.gate().maxTimeoutAndErrorShare(),
                                        settings.gate().maxNotAtFinalStateShare()))
                .build();
    }

    /**
     * {@code null} for a run without levels; a level the run read no server meters for has none. Shares follow the
     * run's: intents by the level of their intended time, load bots of the level by their state at its end.
     */
    private static List<LevelStats> levels(
            RunSettings settings,
            RunTimeline timeline,
            GeneratorLagMonitor lag,
            List<LoadBot> loadBots,
            List<ServerWindow> windows,
            List<ServerStats> servers) {
        if (timeline.levels() == 0) {
            return null;
        }
        final List<LevelStats> levels = new ArrayList<>(timeline.levels());
        for (int level = 0; level < timeline.levels(); level++) {
            final ServerStats server = level < servers.size() ? servers.get(level) : null;
            final Histogram levelLag = lag.levelHistogram(level);
            final long lagNanos = levelLag.getValueAtPercentile(99.0);
            final boolean lagMeasured = levelLag.getTotalCount() > 0;
            final int bots = timeline.levelBots(level);
            final List<LoadBot> present = loadBots.subList(0, bots);
            final long end = timeline.levelEnd(level);
            long sent = 0;
            long timeouts = 0;
            long errors = 0;
            for (LoadBot bot : loadBots) {
                sent += bot.levelSent(level);
                timeouts += bot.levelTimeouts(level);
                errors += bot.levelErrors(level);
            }
            final long stopped = present.stream()
                    .filter(bot -> bot.stoppedOnErrorBefore(end))
                    .count();
            final long notAtFinalState = present.stream()
                    .filter(bot -> !bot.reachedFinalStateBefore(end))
                    .count();
            levels.add(LevelStats.builder()
                    .bots(bots)
                    .window(level < windows.size() ? windows.get(level) : null)
                    .passP99Millis(server == null ? null : server.worstPassP99Millis())
                    .overBudgetShare(server == null ? null : server.worstOverBudgetShare())
                    .busyShare(busyShare(server))
                    .inputP99Millis(inputP99(server))
                    .timeoutShare(share(timeouts, sent))
                    .rejectedShare(share(errors, sent))
                    .stoppedShare(share(stopped, bots))
                    .notAtFinalStateShare(share(notAtFinalState, bots))
                    .generatorLagP99Millis(lagMeasured ? lagNanos / NANOS_PER_MILLI : null)
                    .valid(lagMeasured
                            && lagNanos <= settings.maxGeneratorLagP99().toNanos())
                    .build());
        }
        return levels;
    }

    private static double share(long part, long of) {
        return of == 0 ? 0 : (double) part / of;
    }

    private static Map<String, Double> busyShare(ServerStats server) {
        if (server == null) {
            return Map.of();
        }
        final Map<String, Double> busy = new TreeMap<>();
        server.timedLoops().forEach((loop, stats) -> {
            if (stats.busyShare() != null) {
                busy.put(loop, stats.busyShare());
            }
        });
        return busy;
    }

    private static Map<String, Double> inputP99(ServerStats server) {
        if (server == null || server.inputs() == null) {
            return Map.of();
        }
        final Map<String, Double> p99 = new TreeMap<>();
        server.inputs().forEach((type, stats) -> {
            if (stats.p99Millis() != null) {
                p99.put(type, stats.p99Millis());
            }
        });
        return p99;
    }
}
