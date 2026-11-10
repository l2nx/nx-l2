/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.PassPhase;
import app.l2nx.highfive.network.protocol.StubAdapter;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.channel.OutboundQueue;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.test.RecordingExecutor;
import app.l2nx.network.test.TestChannels;
import app.l2nx.protocol.game.p267.crypt.GameFrameCipher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GameClientAdapterTest {
    private final RecordingExecutor executor = new RecordingExecutor();
    private EmbeddedChannel channel;
    private GameClientAdapter port;

    @BeforeEach
    void setUp() {
        channel = TestChannels.embedded();
        final NetworkMetrics metrics = Connections.metrics(channel);
        final OutboundQueue outbound = new OutboundQueue(NetworkProperties.defaults(), metrics);
        channel.pipeline().addLast(executor, outbound);
        final GameConnection connection =
                new GameConnection(channel, new GameFrameCipher(), outbound, metrics, NetworkProperties.defaults());
        connection.accept(new StubAdapter("stub", 273, 0), 273);
        port = new GameClientAdapter(connection);
    }

    @AfterEach
    void tearDown() {
        executor.runAll();
        channel.finishAndReleaseAll();
    }

    @Nested
    class Emit {
        @Test
        void emit_shouldWakeTheEventLoopInTheFlush_whenCalledInALivePass() {
            final Loop loop = new Loop(
                    "world",
                    new ManualGameClock(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC),
                    1L,
                    EngineProperties.defaults(),
                    new SimpleMeterRegistry());
            final List<Integer> wakeUpsBeforeFlush = new ArrayList<>();
            loop.addHook(PassPhase.FLUSH, (l, now) -> wakeUpsBeforeFlush.add(executor.immediate()));
            loop.offer(l -> port.emit(new StubAdapter.Shown(1)));

            loop.runPass(loop.getPassTime());

            assertThat(executor.lazy()).isEqualTo(1);
            assertThat(wakeUpsBeforeFlush).containsExactly(0);
            assertThat(executor.immediate()).isEqualTo(1);
        }

        @Test
        void emit_shouldWakeTheEventLoopAtOnce_whenCalledOffAnyLoop() {
            port.emit(new StubAdapter.Shown(1));

            assertThat(executor.lazy()).isZero();
            assertThat(executor.immediate()).isEqualTo(1);
        }
    }
}
