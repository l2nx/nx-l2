/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.session.SessionState;
import app.l2nx.highfive.network.protocol.StubAdapter;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.channel.OutboundQueue;
import app.l2nx.network.channel.PipelineNames;
import app.l2nx.network.channel.guard.ConnectionDeadlines;
import app.l2nx.network.channel.guard.IdlePolicy;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.crypt.GameFrameCipher;
import app.l2nx.network.test.TestChannels;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameConnectionTest {
    private final NetworkProperties properties = NetworkProperties.defaults();
    private EmbeddedChannel channel;
    private GameConnection connection;

    @BeforeEach
    void setUp() {
        channel = TestChannels.embedded();
        IdlePolicy.install(channel.pipeline(), properties);
        channel.pipeline().addLast(PipelineNames.OUTBOUND, new OutboundQueue(properties));
        channel.pipeline()
                .addLast(
                        PipelineNames.DEADLINES,
                        new ConnectionDeadlines(properties.handshakeTimeout(), properties.authTimeout()));
        connection = new GameConnection(channel, new GameFrameCipher(), properties);
        channel.attr(GameConnection.KEY).set(connection);
        connection.accept(new StubAdapter("stub", 273, 0), 273);
    }

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    @Test
    void authed_shouldStopTheAuthDeadline() {
        connection.stateChanged(SessionState.CONNECTED, SessionState.AUTHED);

        channel.advanceTimeBy(properties.authTimeout().toSeconds(), TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();

        assertThat(Connections.closeReason(channel)).isNotEqualTo(CloseReason.TIMEOUT);
    }

    @Test
    void authed_shouldPingOnReadIdle_onTheCharacterSelectionScreen() {
        connection.stateChanged(SessionState.CONNECTED, SessionState.AUTHED);

        idleFor(properties.pingInterval().toSeconds());

        assertThat(channel.isOpen()).isTrue();
        assertThat(pingOpcode()).isEqualTo(0xD9);
    }

    @Test
    void connected_shouldNotPing() {
        idleFor(properties.pingInterval().toSeconds());

        assertThat((Object) channel.readOutbound()).isNull();
    }

    @Test
    void execute_shouldRunTheTaskOnTheEventLoop() {
        final boolean[] ran = new boolean[1];

        connection.execute(() -> ran[0] = channel.eventLoop().inEventLoop());
        channel.runPendingTasks();

        assertThat(ran[0]).isTrue();
    }

    private void idleFor(long seconds) {
        channel.runPendingTasks();
        channel.advanceTimeBy(seconds, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();
        channel.runPendingTasks();
    }

    private int pingOpcode() {
        final ByteBuf ping = channel.readOutbound();
        try {
            return ping.getUnsignedByte(ping.readerIndex() + 2);
        } finally {
            ping.release();
        }
    }
}
