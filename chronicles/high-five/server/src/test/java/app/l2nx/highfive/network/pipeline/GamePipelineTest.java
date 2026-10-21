/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.session.DrainScheduler;
import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.SessionFactory;
import app.l2nx.engine.core.session.SessionMetrics;
import app.l2nx.engine.core.session.SessionState;
import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.engine.core.session.command.CommandRegistry;
import app.l2nx.engine.core.session.command.SessionCommandHandler;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.highfive.network.protocol.GameProtocolAdapter;
import app.l2nx.highfive.network.protocol.GameProtocolProperties;
import app.l2nx.highfive.network.protocol.GameProtocolRegistry;
import app.l2nx.highfive.network.protocol.StubAdapter;
import app.l2nx.highfive.network.protocol.p267.P267Adapter;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.crypt.GameXorCipher;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.test.TestChannels;
import io.micrometer.core.instrument.Counter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GamePipelineTest {
    private static final byte[] KEY = {
        11, 12, 13, 14, 15, 16, 17, 18, (byte) 0xC8, 0x27, (byte) 0x93, 1, (byte) 0xA1, 0x6C, 0x31, (byte) 0x97
    };
    private static final int VERSION = 273;
    private static final String PROBE = StubAdapter.Probe.class.getName();

    private final List<Integer> probes = new ArrayList<>();
    // The client's halves of the rolling XOR; the server keys its own with the same key in the handshake.
    private final GameXorCipher clientOut = keyed();
    private final GameXorCipher clientIn = keyed();
    private EmbeddedChannel channel;

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    @Nested
    class Accept {
        @Test
        void accept_shouldCountTheSessionOpen() {
            final SessionFactory sessions = sessions(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);
            channel = TestChannels.embedded();

            initializer(sessions, new StubAdapter("stub", VERSION, 0), connection -> null)
                    .accept(channel);

            assertThat(sessions.openSessions()).isEqualTo(1);
        }

        @Test
        void accept_shouldLeaveNoSessionOpen_whenTheInitializerFails() {
            final SessionFactory sessions = sessions(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);
            final GameChannelInitializer initializer =
                    initializer(sessions, new StubAdapter("stub", VERSION, 0), connection -> {
                        throw new IllegalStateException("boom");
                    });
            channel = TestChannels.embedded();

            assertThatThrownBy(() -> initializer.accept(channel)).isInstanceOf(IllegalStateException.class);

            assertThat(sessions.openSessions()).isZero();
        }
    }

    @Nested
    class Handshake {
        @Test
        void version_shouldSelectTheAdapterAndSendTheKeyPacketInClear() {
            channel = channel(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            channel.writeInbound(version(VERSION));
            channel.runPendingTasks();

            final ByteBuf key = channel.readOutbound();
            assertThat(ByteBufUtil.getBytes(key)).startsWith(11, 0, 0x2E, 11, 12, 13);
            key.release();
            assertThat(GameConnection.of(channel).adapter()).isNotNull();
            assertThat(GameConnection.of(channel).protocolVersion()).isEqualTo(VERSION);
            assertThat(GameConnection.of(channel).cipher().isEnabled()).isTrue();
        }

        @Test
        void frame_shouldBeDecodedByTheNewAdapter_whenBufferedBehindTheVersion() {
            channel = channel(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            // Sent before the client saw the key, so in clear, as a real client would.
            channel.writeInbound(Unpooled.wrappedBuffer(version(VERSION), command(0x01, 42)));

            assertThat(probes).containsExactly(42);
        }

        @Test
        void unknownVersion_shouldSendTheRefusalAndCloseAfterTheWrite() {
            channel = channel(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            channel.writeInbound(version(140));
            channel.runPendingTasks();

            final ByteBuf refusal = channel.readOutbound();
            assertThat((int) refusal.getUnsignedByte(refusal.readerIndex() + 2)).isEqualTo(0x2E);
            assertThat(refusal.getUnsignedByte(refusal.readerIndex() + 3)).isZero();
            refusal.release();
            assertThat(channel.isOpen()).isFalse();
        }

        @Test
        void version_shouldBeRefused_whenTheP267AdapterDoesNotListTheRevision() {
            channel = channel(Session.Limits.of(NetworkProperties.defaults()), Runnable::run, new P267Adapter());

            channel.writeInbound(version(270));
            channel.runPendingTasks();

            final ByteBuf refusal = channel.readOutbound();
            assertThat((int) refusal.getUnsignedByte(refusal.readerIndex() + 2)).isEqualTo(0x2E);
            assertThat(refusal.getUnsignedByte(refusal.readerIndex() + 3)).isZero();
            refusal.release();
            assertThat(GameConnection.of(channel).adapter()).isNull();
            assertThat(channel.isOpen()).isFalse();
        }

        @Test
        void version_shouldSelectTheP267Adapter_whenItListsTheRevision() {
            channel = channel(Session.Limits.of(NetworkProperties.defaults()), Runnable::run, new P267Adapter());

            channel.writeInbound(version(271));
            channel.runPendingTasks();

            final ByteBuf key = channel.readOutbound();
            assertThat((int) key.getUnsignedByte(key.readerIndex() + 3)).isEqualTo(1);
            key.release();
            assertThat(GameConnection.of(channel).adapter()).isInstanceOf(P267Adapter.class);
            assertThat(channel.isOpen()).isTrue();
        }

        @Test
        void shortVersion_shouldBeRefused_andLaterFramesDropped() {
            channel = channel(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            channel.writeInbound(Unpooled.wrappedBuffer(
                    Unpooled.buffer().writeShortLE(5).writeByte(0x0E).writeShortLE(1), command(0x01, 5)));
            channel.runPendingTasks();

            final ByteBuf refusal = channel.readOutbound();
            assertThat((int) refusal.getUnsignedByte(refusal.readerIndex() + 2)).isEqualTo(0x2E);
            assertThat(refusal.getUnsignedByte(refusal.readerIndex() + 3)).isZero();
            refusal.release();
            assertThat(probes).isEmpty();
            assertThat(channel.isOpen()).isFalse();
        }

        @Test
        void unknownVersion_shouldDropFramesThatFollowTheRefusal() {
            channel = channel(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            channel.writeInbound(Unpooled.wrappedBuffer(version(140), command(0x01, 6)));
            channel.runPendingTasks();

            assertThat(probes).isEmpty();
            ((ByteBuf) channel.readOutbound()).release();
        }

        @Test
        void pingVersion_shouldCloseWithoutAnswer() {
            channel = channel(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            channel.writeInbound(version(-2));
            channel.runPendingTasks();

            assertThat((Object) channel.readOutbound()).isNull();
            assertThat(channel.isOpen()).isFalse();
            assertThat(Connections.closeReason(channel)).isNull();
        }

        @Test
        void handshakeTimeout_shouldClose_whenNoVersionArrives() {
            channel = channel(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            channel.advanceTimeBy(10, TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();

            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.TIMEOUT);
        }

        @Test
        void inboundFrames_shouldBeDecrypted_afterTheKeyPacket() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            send(command(0x01, 7));
            send(command(0x01, 8));

            assertThat(probes).containsExactly(7, 8);
        }
    }

    @Nested
    class Decoding {
        @Test
        void unknownOpcode_shouldBeCountedAndDropped() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            send(command(0x44, 0));

            assertThat(channel.isOpen()).isTrue();
            assertThat(count("l2nx.network.dropped", "reason", "unknown-opcode"))
                    .isEqualTo(1.0);
        }

        @Test
        void ignoredOpcode_shouldBeCountedAsIgnored() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);
            authed();

            send(command(0x04, 0));

            assertThat(count("l2nx.network.dropped", "reason", "ignored")).isEqualTo(1.0);
            assertThat(count("l2nx.network.dropped", "reason", "unknown-opcode"))
                    .isZero();
            assertThat(channel.isOpen()).isTrue();
        }

        @Test
        void consumedInput_shouldReachNeitherTheSessionNorADropCounter() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);
            authed();

            send(command(0x05, 0));

            assertThat(GameConnection.of(channel).session().queued()).isZero();
            assertThat(count("l2nx.network.dropped", "reason", "ignored")).isZero();
            assertThat(channel.isOpen()).isTrue();
        }

        @Test
        void decodeError_shouldClose_beforeAuthentication() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            send(command(StubAdapter.BAD_PRE_AUTH, 0));

            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.ERROR);
            assertThat(count("l2nx.network.decode.errors", "state", "CONNECTED"))
                    .isEqualTo(1.0);
        }

        @Test
        void serverBug_shouldCloseThroughTheTailHandler_notCountAsADecodeError() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            send(command(StubAdapter.NEITHER, 0));

            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.ERROR);
            assertThat(count("l2nx.network.decode.errors", "state", "CONNECTED"))
                    .isZero();
        }

        @Test
        void framesOfTheSameRead_shouldNotBeDecoded_afterTheConnectionClosed() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            channel.writeInbound(Unpooled.wrappedBuffer(
                    encrypted(command(StubAdapter.BAD_PRE_AUTH, 0)), encrypted(command(0x01, 9))));

            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.ERROR);
            assertThat(count("l2nx.network.commands", "type", PROBE)).isZero();
            assertThat(probes).isEmpty();
        }

        @Test
        void decodeErrors_shouldBeTolerated_afterAuthenticationUpToTheLimit() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);
            authed();

            for (int i = 0; i < 10; i++) {
                send(command(StubAdapter.BAD_AUTHED, 0));
            }
            assertThat(channel.isOpen()).isTrue();
            assertThat(count("l2nx.network.decode.errors", "state", "AUTHED")).isEqualTo(10.0);
            send(command(StubAdapter.BAD_AUTHED, 0));

            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.ERROR);
        }

        @Test
        void commands_shouldBeCountedByTheirFullyQualifiedType() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            send(command(0x01, 1));

            assertThat(count("l2nx.network.commands", "type", PROBE)).isEqualTo(1.0);
        }
    }

    @Nested
    class Authentication {
        @Test
        void packet_shouldBeDroppedUndecodedAndUnqueued_whenReadBeforeAuthentication() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), task -> {});

            send(command(0x02, 0));
            send(command(StubAdapter.BAD_AUTHED, 0));

            assertThat(count("l2nx.network.dropped", "reason", "unauthenticated"))
                    .isEqualTo(2.0);
            assertThat(count("l2nx.network.decode.errors", "state", "CONNECTED"))
                    .isZero();
            assertThat(count("l2nx.network.commands", "type", PROBE)).isZero();
            assertThat(GameConnection.of(channel).session().queued()).isZero();
            assertThat(channel.isOpen()).isTrue();
        }

        @Test
        void pingAnswer_shouldBeDroppedOnTheIoThread_whenReadBeforeAuthentication() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), task -> {});

            send(command(0x05, 0));

            assertThat(count("l2nx.network.dropped", "reason", "unauthenticated"))
                    .isEqualTo(1.0);
            assertThat(GameConnection.of(channel).session().queued()).isZero();
        }

        @Test
        void command_shouldExecute_afterAuthentication() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);
            authed();

            send(command(0x02, 3));

            assertThat(probes).containsExactly(3);
            assertThat(count("l2nx.network.dropped", "reason", "unauthenticated"))
                    .isZero();
        }
    }

    @Nested
    class Flood {
        @Test
        void queueOverflow_shouldClose_whileTheBucketHasTokens() {
            // Burst far above the queue and no drain: only the full queue can refuse.
            final Session.Limits limits = Session.Limits.of(NetworkProperties.defaults()).toBuilder()
                    .queueLimit(2)
                    .burst(1_000)
                    .build();
            channel = handshaken(limits, task -> {});

            send(command(0x01, 1));
            send(command(0x01, 2));
            assertThat(channel.isOpen()).isTrue();
            send(command(0x01, 3));

            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.FLOOD);
        }

        @Test
        void rateLimit_shouldClose_whileTheQueueHasRoom() {
            // Every command is drained at once, so the queue stays empty: only the bucket can refuse.
            final Session.Limits limits = Session.Limits.of(NetworkProperties.defaults()).toBuilder()
                    .queueLimit(64)
                    .perSecond(1)
                    .burst(2)
                    .build();
            channel = handshaken(limits, Runnable::run);

            send(command(0x01, 1));
            send(command(0x01, 2));
            assertThat(GameConnection.of(channel).session().queued()).isZero();
            assertThat(channel.isOpen()).isTrue();
            send(command(0x01, 3));

            assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.FLOOD);
            assertThat(probes).containsExactly(1, 2);
        }
    }

    @Nested
    class Ping {
        @Test
        void readIdle_shouldSendTheAdaptersPing_afterAuthentication() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);
            authed();

            assertThat(pingAfterIdle()).isTrue();
            assertThat(channel.isOpen()).isTrue();
        }

        @Test
        void readIdle_shouldNotPing_beforeAuthentication() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            channel.advanceTimeBy(NetworkProperties.defaults().pingInterval().toSeconds(), TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();
            channel.runPendingTasks();

            assertThat((Object) channel.readOutbound()).isNull();
            assertThat(channel.isOpen()).isTrue();
        }

        private boolean pingAfterIdle() {
            channel.advanceTimeBy(30, TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();
            channel.runPendingTasks();
            final ByteBuf ping = receive();
            try {
                return ping.getUnsignedByte(ping.readerIndex() + 2) == 0xD9;
            } finally {
                ping.release();
            }
        }
    }

    @Nested
    class Shutdown {
        @Test
        void closeForShutdown_shouldSendTheAdaptersServerClose_thenClose_inAnyState() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);
            authed();

            GameConnection.of(channel).closeForShutdown();
            channel.runPendingTasks();

            final ByteBuf close = receive();
            final int actual = close.getUnsignedByte(close.readerIndex() + 2);
            close.release();
            assertThat(actual).isEqualTo(StubAdapter.SERVER_CLOSE);
            assertThat(channel.isOpen()).isFalse();
        }

        @Test
        void closeForShutdown_shouldJustClose_beforeTheVersionHandshake() {
            channel = channel(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);

            GameConnection.of(channel).closeForShutdown();
            channel.runPendingTasks();

            assertThat((Object) channel.readOutbound()).isNull();
            assertThat(channel.isOpen()).isFalse();
        }

        @Test
        void closeForShutdown_shouldLeaveAConnectionThatIsAlreadyClosingWithItsOwnFrame() {
            channel = handshaken(Session.Limits.of(NetworkProperties.defaults()), Runnable::run);
            final GameConnection connection = GameConnection.of(channel);
            connection
                    .outbound()
                    .sendAndClose(Frames.allocate(channel.alloc(), 1).writeByte(0x7E));

            connection.closeForShutdown();
            channel.runPendingTasks();

            final ByteBuf only = receive();
            final int actual = only.getUnsignedByte(only.readerIndex() + 2);
            only.release();
            assertThat(actual).isEqualTo(0x7E);
            assertThat((Object) channel.readOutbound()).isNull();
        }
    }

    private EmbeddedChannel handshaken(Session.Limits limits, Executor drains) {
        final EmbeddedChannel embedded = channel(limits, drains);
        embedded.writeInbound(version(VERSION));
        embedded.runPendingTasks();
        ((ByteBuf) embedded.readOutbound()).release();
        return embedded;
    }

    private void send(ByteBuf frame) {
        channel.writeInbound(encrypted(frame));
    }

    private ByteBuf encrypted(ByteBuf frame) {
        clientOut.encrypt(frame, Frames.HEADER, frame.readableBytes() - Frames.HEADER);
        return frame;
    }

    private ByteBuf receive() {
        final ByteBuf frame = channel.readOutbound();
        clientIn.decrypt(frame, frame.readerIndex() + Frames.HEADER, frame.readableBytes() - Frames.HEADER);
        return frame;
    }

    private static GameXorCipher keyed() {
        final GameXorCipher cipher = new GameXorCipher();
        cipher.setKey(KEY.clone());
        return cipher;
    }

    private void authed() {
        GameConnection.of(channel).session().setState(SessionState.AUTHED);
        channel.runPendingTasks();
    }

    private EmbeddedChannel channel(Session.Limits limits, Executor drains) {
        return channel(limits, drains, new StubAdapter("stub", VERSION, 0));
    }

    private EmbeddedChannel channel(Session.Limits limits, Executor drains, GameProtocolAdapter adapter) {
        final EmbeddedChannel embedded = TestChannels.embedded();
        initializer(sessions(limits, drains), adapter, connection -> null).accept(embedded);
        embedded.pipeline().fireChannelActive();
        return embedded;
    }

    private SessionFactory sessions(Session.Limits limits, Executor drains) {
        final CommandRegistry registry = new CommandRegistry(List.of(new ProbeHandler(probes)));
        final CommandDispatcher dispatcher =
                new CommandDispatcher(registry, address -> Optional.empty(), NetworkMetrics.noop());
        final SessionMetrics metrics = SessionMetrics.noop();
        return new SessionFactory(
                limits,
                DrainScheduler.pool((drain, abandoned) -> drains.execute(drain), metrics.drainFailures()),
                dispatcher,
                metrics);
    }

    private static GameChannelInitializer initializer(
            SessionFactory sessions, GameProtocolAdapter adapter, Function<GameConnection, GameClient> clients) {
        final GameProtocolRegistry protocols =
                new GameProtocolRegistry(List.of(adapter), new GameProtocolProperties(false));
        return new GameChannelInitializer(NetworkProperties.defaults(), protocols, sessions, clients, KEY::clone);
    }

    private double count(String name, String tag, String value) {
        final Counter counter =
                TestChannels.registry(channel).find(name).tag(tag, value).counter();
        return counter == null ? 0 : counter.count();
    }

    private static ByteBuf version(int version) {
        return Unpooled.buffer().writeShortLE(7).writeByte(0x0E).writeIntLE(version);
    }

    private static ByteBuf command(int opcode, int value) {
        return Unpooled.buffer().writeShortLE(7).writeByte(opcode).writeIntLE(value);
    }

    private static final class ProbeHandler implements SessionCommandHandler<StubAdapter.Probe> {
        private final List<Integer> probes;

        private ProbeHandler(List<Integer> probes) {
            this.probes = probes;
        }

        @Override
        public void handle(Session session, StubAdapter.Probe command) {
            probes.add(command.value());
        }
    }
}
