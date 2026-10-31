/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import app.l2nx.boot.ServerExit;
import app.l2nx.boot.ServerExitCode;
import app.l2nx.boot.integration.BootSmokeTest;
import app.l2nx.database.PostgresDatabase;
import app.l2nx.engine.core.partition.PartitionRuntime;
import app.l2nx.engine.core.session.SessionFactory;
import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.highfive.GameServer;
import app.l2nx.highfive.Shutdown;
import app.l2nx.highfive.WorldLoader;
import app.l2nx.highfive.WorldShutdown;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.login.ListingAttribute;
import app.l2nx.highfive.login.LoginPort;
import app.l2nx.highfive.login.command.AuthenticationResult;
import app.l2nx.highfive.network.protocol.GameProtocolAdapter;
import app.l2nx.highfive.network.protocol.GameProtocolProperties;
import app.l2nx.highfive.network.protocol.GameProtocolRegistry;
import app.l2nx.highfive.network.protocol.p267.P267Adapter;
import app.l2nx.highfive.session.SessionKey;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.crypt.GameXorCipher;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The engine keeps static state, so the nested classes share one context and run in a fixed order, the engine stop last. */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "l2nx.gslink.secret=high-five-application-test-secret",
            // Port 1 refuses at once, so the GS-link only retries in the background; 9014 may be another session's.
            "spring.grpc.client.channel.login.target=static://127.0.0.1:1"
        })
// Imported explicitly: the nested test classes would not detect a configuration nested in the outer one.
@Import(HighFiveApplicationTest.LifecycleOrder.class)
class HighFiveApplicationTest extends BootSmokeTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final byte CHARACTER_SELECTION_INFO = 0x09;
    private static final byte[] XOR_KEY_TAIL = {
        (byte) 0xC8, (byte) 0x27, (byte) 0x93, (byte) 0x01, (byte) 0xA1, (byte) 0x6C, (byte) 0x31, (byte) 0x97
    };

    static {
        storeWorldState();
    }

    @MockitoBean
    ServerExit serverExit;

    @Autowired
    GameServer gameServer;

    @Autowired
    ConfigurableApplicationContext context;

    @Nested
    @Order(1)
    class Start {
        @Autowired
        GameProtocolRegistry protocolRegistry;

        @Test
        void readiness_shouldBeUp_whenStarted() throws Exception {
            final HttpResponse<String> actual = get("/actuator/health/readiness");

            assertThat(actual.statusCode()).isEqualTo(200);
            final JsonNode body = JSON.readTree(actual.body());
            assertThat(body.path("status").asString()).isEqualTo("UP");
            assertThat(body.path("components").path("db").path("status").asString())
                    .isEqualTo("UP");
        }

        @Test
        void clientPort_shouldAcceptTcp_whenStarted() throws Exception {
            try (Socket socket = connect(ServerConfig.PORT_GAME)) {
                assertThat(socket.isConnected()).isTrue();
            }
        }

        @Test
        void clientPort_shouldAnswerTheProtocolVersionWithTheKeyPacket() throws Exception {
            try (Socket socket = connect(ServerConfig.PORT_GAME)) {
                socket.getOutputStream().write(new byte[] {7, 0, 0x0E, 0x11, 0x01, 0, 0});
                final DataInputStream in = new DataInputStream(socket.getInputStream());
                final int length = in.readUnsignedByte() | (in.readUnsignedByte() << 8);
                final byte[] key = new byte[length - 2];
                in.readFully(key);

                assertThat(key[0]).isEqualTo((byte) 0x2E);
                assertThat(key[1]).as("protocol accepted").isEqualTo((byte) 1);
            }
        }

        @Test
        void start_shouldLoadTheWorldOnItsLoop_beforeThePortOpens() {
            final List<String> actual = LifecycleOrder.EVENTS.stream()
                    .takeWhile(event -> !event.startsWith("ready"))
                    .toList();

            assertThat(actual)
                    .containsExactly("load on l2nx-loop-world", "world loaded, port closed", "LivenessState CORRECT");
        }

        @Test
        void readiness_shouldAcceptTraffic_afterTheApplicationIsReady() {
            final List<String> actual = LifecycleOrder.EVENTS.stream()
                    .dropWhile(event -> !event.startsWith("ready"))
                    .limit(2)
                    .toList();

            assertThat(actual).containsExactly("ready, port open", "ReadinessState ACCEPTING_TRAFFIC");
        }

        @Test
        void worldLoop_shouldRunOnItsOwnThread_whenStarted() {
            final boolean actual = Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(thread -> thread.isAlive() && thread.getName().equals("l2nx-loop-world"));

            assertThat(actual).isTrue();
        }

        @Test
        void protocolRegistry_shouldHoldOnlyTheP267Adapter_whileLegacyRoutesRemain() {
            final List<GameProtocolAdapter> adapters = protocolRegistry.adapters();
            final int legacyRoutes = adapters.stream()
                    .filter(adapter -> P267Adapter.ID.equals(adapter.id()))
                    .mapToInt(GameProtocolAdapter::legacyInboundCount)
                    .sum();

            assertThat(legacyRoutes).isPositive();
            assertThat(adapters).extracting(GameProtocolAdapter::id).containsExactly(P267Adapter.ID);
            assertThat(context.getBean(GameProtocolProperties.class).allowLegacyWithMultipleAdapters())
                    .as("the production configuration never allows a second adapter beside legacy routes")
                    .isFalse();
        }
    }

    @Nested
    @Order(2)
    class Management {
        @Test
        void health_shouldBeUp() throws Exception {
            final HttpResponse<String> actual = get("/actuator/health");

            assertThat(actual.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(actual.body()).path("status").asString()).isEqualTo("UP");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"build", "git", "java", "os", "process"})
        void info_shouldExposeSection(String section) throws Exception {
            final HttpResponse<String> actual = get("/actuator/info");

            assertThat(actual.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(actual.body()).path(section).isObject())
                    .as("info section %s", section)
                    .isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"taskScheduler", "applicationTaskExecutor"})
        void prometheus_shouldExposeExecutorMetrics(String executor) throws Exception {
            final HttpResponse<String> actual = get("/actuator/prometheus");

            assertThat(actual.statusCode()).isEqualTo(200);
            assertThat(actual.body())
                    .as("executor %s", executor)
                    .containsPattern("executor_pool_size_threads\\{[^}]*name=\"" + executor + "\"");
        }

        @Test
        void prometheus_shouldExposeTheSessionDrainExecutor() throws Exception {
            final HttpResponse<String> actual = get("/actuator/prometheus");

            assertThat(actual.statusCode()).isEqualTo(200);
            assertThat(actual.body()).containsPattern("executor_pool_max_threads\\{[^}]*name=\"l2nx-game-packets\"");
        }

        @Test
        void prometheus_shouldExposePlayersOnline() throws Exception {
            final HttpResponse<String> actual = get("/actuator/prometheus");

            assertThat(actual.statusCode()).isEqualTo(200);
            assertThat(actual.body()).contains("l2nx_players_online");
        }

        @Test
        void prometheus_shouldExposeEmptyWorldInbox() {
            // Legacy timers keep posting to the inbox, so a single scrape can catch it mid-drain.
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                final HttpResponse<String> actual = get("/actuator/prometheus");

                assertThat(actual.statusCode()).isEqualTo(200);
                assertThat(actual.body())
                        .containsPattern("l2nx_engine_inbox_depth\\{[^}]*loop=\"world\"[^}]*\\} 0\\.0");
            });
        }

        @Test
        void prometheus_shouldCountWorldPasses_whenLegacyTimersRun() {
            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> assertThat(get("/actuator/prometheus").body())
                            .containsPattern(
                                    "(?m)^l2nx_engine_pass_seconds_count\\{[^}]*loop=\"world\"[^}]*\\} [1-9]"));
        }
    }

    @Nested
    @Order(3)
    class Countdown {
        @Test
        void startShutdown_shouldExitWithShutdownCode_whenCountdownEnds() {
            Shutdown.getInstance().startShutdown(null, 0, false);

            verify(serverExit, timeout(10_000)).exit(ServerExitCode.SHUTDOWN);
        }
    }

    @Nested
    @Order(4)
    class Stop {
        @Autowired
        NetworkProperties networkProperties;

        @Autowired
        SessionFactory sessionFactory;

        @Test
        void stop_shouldStopPortWorldRuntimeAndNetworkInOrder_withinTheShutdownTimeout_whenASessionIsAuthenticated()
                throws Exception {
            try (Socket client = authenticatedClient("stoptest")) {
                final int eventsBeforeStop = LifecycleOrder.EVENTS.size();
                final long start = System.nanoTime();

                context.stop();

                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(networkProperties.shutdownTimeout());
                assertThat(sessionFactory.openSessions()).isZero();
                assertStoppedInOrder(eventsBeforeStop);
            }
        }

        private void assertStoppedInOrder(int eventsBeforeStop) {
            assertThat(LifecycleOrder.EVENTS.subList(eventsBeforeStop, LifecycleOrder.EVENTS.size()))
                    .containsExactly(
                            "port stopped, world running",
                            "world shutdown on l2nx-loop-world",
                            "world stopped, runtime running",
                            "runtime stopped, network running");
            assertThat(gameServer.isRunning()).isFalse();
            verifyNoInteractions(serverExit);
        }

        private Socket authenticatedClient(String login) throws IOException {
            final Socket socket = connect(ServerConfig.PORT_GAME);
            try {
                socket.setSoTimeout(10_000);
                final OutputStream out = socket.getOutputStream();
                final DataInputStream in = new DataInputStream(socket.getInputStream());
                out.write(new byte[] {7, 0, 0x0E, 0x11, 0x01, 0, 0});
                final byte[] keyPacket = readFrame(in);
                assertThat(keyPacket[0]).isEqualTo((byte) 0x2E);
                final byte[] key = new byte[16];
                System.arraycopy(keyPacket, 2, key, 0, 8);
                System.arraycopy(XOR_KEY_TAIL, 0, key, 8, 8);
                final GameXorCipher outbound = new GameXorCipher();
                outbound.setKey(key);
                final GameXorCipher inbound = new GameXorCipher();
                inbound.setKey(key);

                out.write(encryptedFrame(outbound, authLogin(login)));

                byte[] frame;
                do {
                    frame = readFrame(in);
                    inbound.decrypt(Unpooled.wrappedBuffer(frame), 0, frame.length);
                } while (frame[0] != CHARACTER_SELECTION_INFO);
                return socket;
            } catch (IOException | RuntimeException | Error e) {
                socket.close();
                throw e;
            }
        }

        private static byte[] authLogin(String login) {
            final ByteBuf body = Unpooled.buffer();
            body.writeByte(0x2B);
            for (char c : login.toCharArray()) {
                body.writeShortLE(c);
            }
            body.writeShortLE(0);
            body.writeIntLE(2).writeIntLE(1).writeIntLE(3).writeIntLE(4);
            return ByteBufUtil.getBytes(body);
        }

        private static byte[] encryptedFrame(GameXorCipher cipher, byte[] body) {
            final ByteBuf frame = Unpooled.buffer(body.length + 2);
            frame.writeShortLE(body.length + 2);
            frame.writeBytes(body);
            cipher.encrypt(frame, 2, body.length);
            return ByteBufUtil.getBytes(frame);
        }

        private static byte[] readFrame(DataInputStream in) throws IOException {
            final int length = in.readUnsignedByte() | (in.readUnsignedByte() << 8);
            final byte[] body = new byte[length - 2];
            in.readFully(body);
            return body;
        }
    }

    /**
     * Probes look the beans up when they run: a bean they depended on would start before them.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class LifecycleOrder {
        static final List<String> EVENTS = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        WorldLoader recordingWorldLoader(ServerExit serverExit) {
            return new WorldLoader(serverExit) {
                @Override
                public void load() {
                    super.load();
                    EVENTS.add("load on " + Thread.currentThread().getName());
                }
            };
        }

        @Bean
        @Primary
        WorldShutdown recordingWorldShutdown() {
            return new WorldShutdown() {
                @Override
                public void run() {
                    EVENTS.add("world shutdown on " + Thread.currentThread().getName());
                    super.run();
                }
            };
        }

        @Bean
        SmartLifecycle betweenWorldAndPort(ApplicationContext context) {
            return new Probe(
                    SmartLifecycle.DEFAULT_PHASE - 1,
                    () -> state(world(context), "world loaded", "world not loaded") + ", "
                            + (listening() ? "port open" : "port closed"),
                    () -> (listening() ? "port open" : "port stopped") + ", "
                            + state(world(context), "world running", "world stopped"));
        }

        @Bean
        SmartLifecycle betweenRuntimeAndWorld(ApplicationContext context) {
            return new Probe(
                    PartitionRuntime.PHASE + 1,
                    () -> null,
                    () -> state(world(context), "world running", "world stopped") + ", "
                            + state(context.getBean(PartitionRuntime.class), "runtime running", "runtime stopped"));
        }

        @Bean
        SmartLifecycle betweenNetworkAndRuntime(ApplicationContext context) {
            return new Probe(
                    PartitionRuntime.PHASE - 1,
                    () -> null,
                    () -> state(context.getBean(PartitionRuntime.class), "runtime running", "runtime stopped") + ", "
                            + state(context.getBean(GameServer.class), "network running", "network stopped"));
        }

        @Bean
        @Primary
        LoginPort acceptingLoginPort(ObjectProvider<CommandDispatcher> commands) {
            return new LoginPort() {
                @Override
                public boolean authenticate(UUID requestId, String login, SessionKey key) {
                    commands.getObject()
                            .submit(AuthenticationResult.builder()
                                    .requestId(requestId)
                                    .accountId(UUID.nameUUIDFromBytes(login.getBytes(StandardCharsets.UTF_8)))
                                    .accepted(true)
                                    .build());
                    return true;
                }

                @Override
                public void accountsEntered(List<UUID> accountIds) {}

                @Override
                public void accountLeft(UUID accountId) {}

                @Override
                public void changeAccessLevel(UUID accountId, int level) {}

                @Override
                public void reportRoute(UUID accountId, List<String> route) {}

                @Override
                public void requestTempBan(UUID accountId, String ip, Duration duration) {}

                @Override
                public boolean changePassword(
                        UUID requestId, UUID accountId, String characterName, String oldPassword, String newPassword) {
                    return false;
                }

                @Override
                public void reportCharacters(UUID accountId, int count, List<Instant> deleteTimes) {}

                @Override
                public void publishListing(Map<ListingAttribute, Integer> attributes) {}
            };
        }

        @Bean
        ApplicationListener<ApplicationReadyEvent> readyRecorder() {
            return event -> EVENTS.add("ready, " + (listening() ? "port open" : "port closed"));
        }

        @Bean
        ApplicationListener<AvailabilityChangeEvent<?>> availabilityRecorder() {
            return event -> {
                final AvailabilityState state = event.getState();
                EVENTS.add(state.getClass().getSimpleName() + " " + state);
            };
        }

        private static Lifecycle world(ApplicationContext context) {
            return context.getBean("worldLifecycle", Lifecycle.class);
        }

        private static String state(Lifecycle lifecycle, String running, String stopped) {
            return lifecycle.isRunning() ? running : stopped;
        }

        private static boolean listening() {
            try (Socket socket = connect(ServerConfig.PORT_GAME)) {
                return socket.isConnected();
            } catch (IOException e) {
                return false;
            }
        }
    }

    private static final class Probe implements SmartLifecycle {
        private final int phase;
        private final Supplier<String> onStart;
        private final Supplier<String> onStop;
        private volatile boolean running;

        Probe(int phase, Supplier<String> onStart, Supplier<String> onStop) {
            this.phase = phase;
            this.onStart = onStart;
            this.onStop = onStop;
        }

        @Override
        public void start() {
            record(onStart.get());
            running = true;
        }

        @Override
        public void stop() {
            record(onStop.get());
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            return phase;
        }

        private static void record(String event) {
            if (event != null) {
                LifecycleOrder.EVENTS.add(event);
            }
        }
    }

    private static void storeWorldState() {
        final JdbcClient jdbc =
                JdbcClient.create(PostgresDatabase.migratedSuperuserSchema("game", "db/liquibase-changelog.yaml"));
        jdbc.sql("""
            INSERT INTO castle_states (castle_id, tax_percent, treasury, siege_at, registration_ends_at)
            VALUES (1, 10, 1000, now() + interval '7 days', now() + interval '6 days')\
            """).update();
        jdbc.sql("INSERT INTO fort_states (fort_id, siege_at) VALUES (101, now() + interval '1 day')")
                .update();
        jdbc.sql("INSERT INTO clan_hall_states (clan_hall_id, paid_until, paid) VALUES (22, now() + interval '7"
                        + " days', true)")
                .update();
        jdbc.sql("""
            INSERT INTO siegable_clan_hall_states (clan_hall_id, next_siege_at)
            VALUES (21, now() + interval '7 days')\
            """).update();
        jdbc.sql("""
            INSERT INTO grand_boss_states (npc_id, status, respawn_at, current_hp, current_mp)
            VALUES (29001, 3, now() + interval '1 day', 1, 1)\
            """).update();
        jdbc.sql("""
            INSERT INTO raid_boss_states (npc_id, respawn_at, current_hp, current_mp)
            VALUES (25001, now() + interval '1 day', 1, 1)\
            """).update();
        jdbc.sql("INSERT INTO territory_states (territory_id, owned_ward_territory_ids) VALUES (81, '{81}')")
                .update();
    }
}
