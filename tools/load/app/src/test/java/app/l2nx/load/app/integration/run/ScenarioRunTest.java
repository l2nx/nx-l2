/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.integration.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import app.l2nx.database.PostgresDatabase;
import app.l2nx.load.app.LoadApplication;
import app.l2nx.load.app.LoadProperties;
import app.l2nx.load.app.integration.database.LoadPostgres;
import app.l2nx.load.app.scenario.CrowdPvpLoadScenario;
import app.l2nx.load.app.scenario.PveLoadScenario;
import app.l2nx.load.app.scenario.RoamLoadScenario;
import app.l2nx.load.app.seed.model.CharacterKit;
import app.l2nx.load.app.seed.model.CharacterPlan;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.protocol.p267.P267Protocol;
import app.l2nx.load.report.ReportWriter;
import app.l2nx.load.report.model.RunSummary;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.SpringApplication;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The real servers, each in its own process: all ship an {@code application.yaml} on the classpath root and resolve
 * {@code ./config} and {@code ./data} against the working directory, so one JVM cannot hold them. The distributions
 * have no geodata, so movement runs on straight lines.
 */
@Execution(ExecutionMode.SAME_THREAD)
@EnabledIfSystemProperty(named = "l2nx.load.test.game-distribution", matches = ".+")
@ResourceLock("spring-boot-logging")
class ScenarioRunTest {
    private static final String PROMETHEUS_IMAGE = "prom/prometheus:v3.13.5";
    private static final String GSLINK_SECRET = "load-harness-integration-test-secret";
    private static final Pattern LOGIN_READY = Pattern.compile("Started LoginApplication");
    private static final Pattern GAME_REGISTERED = Pattern.compile("Registered on login as Server (\\d+)");
    private static final Duration START_TIMEOUT = Duration.ofMinutes(4);
    private static final Duration SCRAPE_TIMEOUT = Duration.ofSeconds(30);
    private static final int BOTS = 20;

    private static final double MAX_TIMEOUT_SHARE = 0.05;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final Path loginDistribution = Path.of(System.getProperty("l2nx.load.test.login-distribution"));
    private final Path gameDistribution = Path.of(System.getProperty("l2nx.load.test.game-distribution"));
    private final LoadProperties.Database database = LoadPostgres.uniqueDatabase();
    private final List<ServerProcess> servers = new ArrayList<>();
    private GenericContainer<?> prometheus;
    private int loginPort;
    private int gamePort;
    private int gsLinkPort;
    private int loginManagementPort;
    private int gameManagementPort;
    private int serverId;

    private Path root;

    @BeforeEach
    void prepareDirectory() throws IOException {
        root = Files.createTempDirectory(Files.createDirectories(Path.of("build", "scenario-run")), "run");
    }

    @AfterEach
    void stop() throws InterruptedException {
        stopServers();
        if (prometheus != null) {
            prometheus.stop();
        }
    }

    @Test
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    void run_shouldPlayEveryScenario_onASeededDatabase() throws Exception {
        allocatePorts();
        prepareGameDistribution();
        startPrometheus();
        assertThat(harness("db-create")).isZero();

        startServers("seed", "load,load-seed");
        final int seedExit = harness(
                "seed",
                "--l2nx.load.ramp=5s",
                "--l2nx.load.warm-up=0s",
                "--l2nx.load.steady=90s",
                "--l2nx.load.ramp-down=0s");
        stopServers();

        assertThat(seedExit).isZero();
        assertSeededCharacters();
        assertThat(harness("db-snapshot")).isZero();

        final Path roam = play(RoamLoadScenario.ID);
        final RunSummary actual = summary(roam);
        assertThat(actual.reachedFinalState()).as("all in the game").isEqualTo(BOTS);
        assertThat(actual.stoppedShare()).as("stopped").isZero();
        assertThat(actual.timeoutShare()).as("timed out").isZero();
        assertThat(actual.rejectedShare()).as("rejected").isZero();
        assertThat(actual.loggedOut()).as("logged out").isEqualTo(BOTS);
        assertThat(actual.intents().get(IntentKind.MOVE_TO).acknowledged()).isPositive();
        assertThat(actual.intents().get(IntentKind.SAY).acknowledged()).isPositive();
        assertThat(actual.inboundFrames()).isNotNull();
        assertThat(actual.inboundFrames().frames()).isPositive();
        assertThat(actual.server()).as("server meters").isNotNull();
        assertThat(actual.server().loops()).containsKey("world");
        assertThat(roam.resolve(ReportWriter.metricsFile("game", "start"))).isNotEmptyFile();
        assertThat(roam.resolve(ReportWriter.metricsFile("game", "end"))).isNotEmptyFile();
        assertThat(roam.resolve(ReportWriter.metricsFile("login", "start"))).isNotEmptyFile();
        assertThat(roam.resolve(ReportWriter.metricsFile("login", "end"))).isNotEmptyFile();
        final Path gameEnd = roam.resolve(ReportWriter.metricsFile("game", "end"));
        assertThat(bucketBounds(gameEnd, "l2nx_session_input_seconds_bucket"))
                .as("the input time buckets of the profile load")
                .anySatisfy(bound -> assertThat(bound).isCloseTo(25e-6, within(1e-12)))
                .anySatisfy(bound -> assertThat(bound).isCloseTo(1.25e-3, within(1e-12)));
        assertThat(bucketBounds(gameEnd, "l2nx_engine_pass_seconds_bucket"))
                .as("the pass buckets of the profile load")
                .anySatisfy(bound -> assertThat(bound).isCloseTo(0.25e-3, within(1e-12)))
                .anySatisfy(bound -> assertThat(bound).isCloseTo(4.75e-3, within(1e-12)));
        assertThat(Files.readString(gameEnd)).contains("l2nx_engine_pass_phase_seconds_count{");
        assertThat(bucketBounds(gameEnd, "l2nx_engine_pass_phase_seconds_bucket"))
                .as("the phases take no pass bounds")
                .isEmpty();

        assertFought(summary(play(PveLoadScenario.ID)));
        final RunSummary crowd = summary(play(CrowdPvpLoadScenario.ID));
        assertFought(crowd);
        // Monsters of the hunting spot die before a cast is due, characters of the same level do not.
        assertThat(crowd.intents().get(IntentKind.USE_SKILL).acknowledged())
                .as("casts")
                .isPositive();
    }

    private static void assertFought(RunSummary actual) {
        assertThat(actual.reachedFinalState()).as("all in the game").isEqualTo(BOTS);
        assertThat(actual.stoppedShare()).as("stopped").isZero();
        assertThat(actual.timeoutShare()).as("timeouts").isLessThan(MAX_TIMEOUT_SHARE);
        assertThat(actual.intents().get(IntentKind.ATTACK).acknowledged())
                .as("hits")
                .isPositive();
    }

    private Path play(String scenario) throws IOException, InterruptedException {
        assertThat(harness("db-reset")).isZero();
        startServers(scenario, "load");
        awaitScrapes();
        final int exit = harness(
                "run",
                "--l2nx.load.scenario=" + scenario,
                "--l2nx.load.ramp=5s",
                "--l2nx.load.warm-up=2s",
                "--l2nx.load.steady=30s",
                "--l2nx.load.ramp-down=3s",
                "--l2nx.load.seed=42",
                // A Windows timer tick alone can exceed the default, and this test is about the servers.
                "--l2nx.load.max-generator-lag-p99=1s",
                "--l2nx.load.prometheus.max-clock-skew=5s");
        stopServers();
        assertThat(exit).isZero();
        return reportOf(scenario);
    }

    private void allocatePorts() throws IOException {
        try (ServerSocket login = freeSocket();
                ServerSocket game = freeSocket();
                ServerSocket gsLink = freeSocket();
                ServerSocket loginManagement = freeSocket();
                ServerSocket gameManagement = freeSocket()) {
            loginPort = login.getLocalPort();
            gamePort = game.getLocalPort();
            gsLinkPort = gsLink.getLocalPort();
            loginManagementPort = loginManagement.getLocalPort();
            gameManagementPort = gameManagement.getLocalPort();
        }
    }

    private static ServerSocket freeSocket() throws IOException {
        return new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    }

    private void prepareGameDistribution() throws IOException {
        final Path config = gameDistribution.resolve("config");
        Files.deleteIfExists(config.resolve("hexid.txt"));
        final Path server = config.resolve("Server.ini");
        final String patched = Files.readString(server, StandardCharsets.ISO_8859_1)
                .replaceAll("(?m)^GameserverPort = .*$", "GameserverPort = " + gamePort)
                .replaceAll("(?m)^GameserverHostname = .*$", "GameserverHostname = 127.0.0.1");
        Files.writeString(server, patched, StandardCharsets.ISO_8859_1);
    }

    private void startPrometheus() {
        Testcontainers.exposeHostPorts(gameManagementPort, loginManagementPort);
        prometheus = new GenericContainer<>(DockerImageName.parse(PROMETHEUS_IMAGE))
                .withCopyToContainer(Transferable.of(prometheusConfig()), "/etc/prometheus/prometheus.yml")
                .withExposedPorts(9090)
                .waitingFor(Wait.forHttp("/-/ready").forStatusCode(200));
        prometheus.start();
    }

    private String prometheusConfig() {
        return """
            global:
              scrape_interval: 1s
              scrape_timeout: 1s
            scrape_configs:
              - job_name: game
                metrics_path: /actuator/prometheus
                static_configs:
                  - targets: ["host.testcontainers.internal:%d"]
              - job_name: login
                metrics_path: /actuator/prometheus
                static_configs:
                  - targets: ["host.testcontainers.internal:%d"]
            """.formatted(gameManagementPort, loginManagementPort);
    }

    private String prometheusUrl() {
        return "http://" + prometheus.getHost() + ":" + prometheus.getMappedPort(9090);
    }

    private void startServers(String phase, String profiles) throws IOException, InterruptedException {
        final ServerProcess login = ServerProcess.start(
                "login server",
                loginDistribution,
                "app.l2nx.login.boot.LoginApplication",
                root.resolve("login-" + phase + ".log"),
                serverArguments(
                        profiles,
                        "l2nx_login",
                        loginManagementPort,
                        "--l2nx.login.client.host=127.0.0.1",
                        "--l2nx.login.client.ports[0].port=" + loginPort,
                        "--l2nx.login.client.ports[0].variant=c621",
                        "--spring.grpc.server.port=" + gsLinkPort));
        servers.add(login);
        login.awaitLog(LOGIN_READY, START_TIMEOUT);
        final ServerProcess game = ServerProcess.start(
                "game server",
                gameDistribution,
                "app.l2nx.highfive.boot.HighFiveApplication",
                root.resolve("game-" + phase + ".log"),
                serverArguments(
                        profiles,
                        "l2nx_game",
                        gameManagementPort,
                        "--spring.grpc.client.channel.login.target=static://127.0.0.1:" + gsLinkPort));
        servers.add(game);
        serverId = Integer.parseInt(game.awaitLog(GAME_REGISTERED, START_TIMEOUT));
        awaitReadiness(gameManagementPort);
    }

    private List<String> serverArguments(String profiles, String role, int managementPort, String... extra) {
        final PostgreSQLContainer container = PostgresDatabase.container();
        final String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getFirstMappedPort() + "/"
                + database.name();
        final List<String> arguments = new ArrayList<>(List.of(
                "--spring.profiles.active=" + profiles,
                "--spring.datasource.url=" + url,
                // The roles of LoadPostgres cannot log in; the superuser stands in for them.
                "--spring.datasource.username=" + container.getUsername(),
                "--spring.datasource.password=" + container.getPassword(),
                "--server.port=" + managementPort,
                "--l2nx.gslink.secret=" + GSLINK_SECRET));
        arguments.addAll(List.of(extra));
        return arguments;
    }

    private static void awaitReadiness(int managementPort) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + managementPort + "/actuator/health/readiness"))
                .build();
        final Instant deadline = Instant.now().plus(START_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            try {
                if (HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    return;
                }
            } catch (IOException e) {
            }
            TimeUnit.MILLISECONDS.sleep(250);
        }
        throw new IllegalStateException("The game server was not ready in " + START_TIMEOUT);
    }

    private void awaitScrapes() throws IOException, InterruptedException {
        final Instant deadline = Instant.now().plus(SCRAPE_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (up("game") && up("login")) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(250);
        }
        throw new IllegalStateException("Prometheus did not scrape both servers in " + SCRAPE_TIMEOUT);
    }

    private boolean up(String job) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(
                        URI.create(prometheusUrl() + "/api/v1/query?query=up%7Bjob%3D%22" + job + "%22%7D"))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body().contains("\"1\"]");
    }

    private void stopServers() throws InterruptedException {
        for (int index = servers.size() - 1; index >= 0; index--) {
            servers.get(index).close();
        }
        servers.clear();
    }

    private int harness(String command, String... options) {
        final List<String> arguments = new ArrayList<>(List.of(
                command,
                "--l2nx.load.protocol=" + P267Protocol.ID,
                "--l2nx.load.bots=" + BOTS,
                "--l2nx.load.event-loop-threads=2",
                "--l2nx.load.output-dir=" + root.resolve("reports"),
                "--l2nx.load.p267.login-host=127.0.0.1",
                "--l2nx.load.p267.login-port=" + loginPort,
                "--l2nx.load.p267.server-id=" + Math.max(1, serverId),
                "--l2nx.load.database.admin-url=" + database.adminUrl(),
                "--l2nx.load.database.user=" + database.user(),
                "--l2nx.load.database.password=" + database.password(),
                "--l2nx.load.database.name=" + database.name(),
                "--l2nx.load.database.template=" + database.template()));
        if (prometheus != null) {
            arguments.add("--l2nx.load.prometheus.url=" + prometheusUrl());
            arguments.add("--l2nx.load.prometheus.targets[0].job=game");
            arguments.add("--l2nx.load.prometheus.targets[0].metrics-url=http://127.0.0.1:" + gameManagementPort
                    + "/actuator/prometheus");
            arguments.add("--l2nx.load.prometheus.targets[1].job=login");
            arguments.add("--l2nx.load.prometheus.targets[1].metrics-url=http://127.0.0.1:" + loginManagementPort
                    + "/actuator/prometheus");
        }
        arguments.addAll(List.of(options));
        return SpringApplication.exit(SpringApplication.run(LoadApplication.class, arguments.toArray(String[]::new)));
    }

    private void assertSeededCharacters() throws SQLException {
        final PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(database.adminUrl());
        source.setDatabaseName(database.name());
        source.setUser(database.user());
        source.setPassword(database.password());
        try (Connection connection = source.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                    SELECT class_id, character_level, access_level, x, y
                    FROM game.characters
                    WHERE name = ?
                    """)) {
            for (CharacterPlan plan : CharacterPlan.values()) {
                for (int index = 0; index < BOTS; index++) {
                    statement.setString(1, plan.characterName("load", index));
                    try (ResultSet row = statement.executeQuery()) {
                        final Position expected = plan.position(index, BOTS);
                        assertThat(row.next()).as("%s %d", plan, index).isTrue();
                        assertThat(row.getInt("class_id"))
                                .isEqualTo(CharacterKit.of(index).classId());
                        assertThat(row.getInt("character_level")).isEqualTo(CharacterKit.LEVEL);
                        assertThat(row.getInt("access_level"))
                                .as("no admin rights outlive load-seed")
                                .isZero();
                        assertThat(row.getInt("x")).isEqualTo(expected.x());
                        assertThat(row.getInt("y")).isEqualTo(expected.y());
                    }
                }
            }
        }
    }

    private Path reportOf(String scenario) throws IOException {
        try (Stream<Path> reports = Files.list(root.resolve("reports"))) {
            return reports.filter(report -> report.getFileName().toString().startsWith(scenario + "-"))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static List<Double> bucketBounds(Path snapshot, String bucket) throws IOException {
        final Pattern bound =
                Pattern.compile("^" + Pattern.quote(bucket) + "\\{.*le=\"([^\"]+)\".*$", Pattern.MULTILINE);
        return bound.matcher(Files.readString(snapshot))
                .results()
                .map(match -> match.group(1))
                .filter(le -> !le.equals("+Inf"))
                .map(Double::valueOf)
                .toList();
    }

    private static RunSummary summary(Path report) {
        return new ReportWriter().readSummary(report.resolve(ReportWriter.SUMMARY_FILE));
    }
}
