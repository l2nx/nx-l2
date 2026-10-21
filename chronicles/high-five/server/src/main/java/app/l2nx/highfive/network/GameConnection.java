/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network;

import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.SessionState;
import app.l2nx.engine.core.session.SessionTransport;
import app.l2nx.highfive.network.protocol.GameProtocolAdapter;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.channel.OutboundQueue;
import app.l2nx.network.channel.guard.ConnectionDeadlines;
import app.l2nx.network.channel.guard.EventLoopTasks;
import app.l2nx.network.channel.guard.IdlePolicy;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.crypt.GameFrameCipher;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.local.SynchronizationStrategy;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Network state of one game connection. The adapter field is set once by the version handshake; the single packet
 * decoder reads it, so frames already buffered behind the version are decoded by the chosen adapter.
 */
public final class GameConnection implements SessionTransport {
    public static final AttributeKey<GameConnection> KEY = AttributeKey.valueOf("l2nx.game.connection");

    private final Channel channel;
    private final GameFrameCipher cipher;
    private final Bucket decodeErrors;
    private final AtomicInteger pingIds = new AtomicInteger();
    private volatile GameProtocolAdapter adapter;
    private volatile int protocolVersion;
    private Session session;
    private GameClient client;

    public GameConnection(Channel channel, GameFrameCipher cipher, NetworkProperties properties) {
        this.channel = channel;
        this.cipher = cipher;
        final NetworkProperties.DecodeErrorLimit limit = properties.decodeErrorLimit();
        // Only the IO thread decodes, so the bucket needs no synchronization.
        decodeErrors = Bucket.builder()
                .addLimit(l -> l.capacity(limit.count()).refillGreedy(limit.count(), limit.window()))
                .withSynchronizationStrategy(SynchronizationStrategy.NONE)
                .build();
    }

    public static GameConnection of(Channel channel) {
        return channel.attr(KEY).get();
    }

    public void bind(Session session, GameClient client) {
        this.session = session;
        this.client = client;
    }

    public Channel channel() {
        return channel;
    }

    public GameFrameCipher cipher() {
        return cipher;
    }

    public Session session() {
        return session;
    }

    public GameClient client() {
        return client;
    }

    public GameProtocolAdapter adapter() {
        return adapter;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public void accept(GameProtocolAdapter adapter, int version) {
        protocolVersion = version;
        this.adapter = adapter;
        final ConnectionDeadlines deadlines = ConnectionDeadlines.of(channel);
        if (deadlines != null) {
            deadlines.handshakeReached();
        }
    }

    public OutboundQueue outbound() {
        return OutboundQueue.of(channel);
    }

    public ByteBufAllocator alloc() {
        return channel.alloc();
    }

    public boolean allowDecodeError() {
        return decodeErrors.tryConsume(1);
    }

    @Override
    public void execute(Runnable task) {
        EventLoopTasks.run(channel.eventLoop(), task);
    }

    @Override
    public void stateChanged(SessionState from, SessionState to) {
        if ((from == SessionState.CONNECTED) && (to == SessionState.AUTHED)) {
            // Called under the session's transition lock from any thread: the pipeline work goes to the event loop.
            execute(this::authenticated);
        }
    }

    @Override
    public void close(CloseReason reason) {
        Connections.close(channel, reason);
    }

    /**
     * A connection still in its version handshake closes without a frame; one already closing keeps its own last frame
     * (a saved player's ServerClose).
     */
    public void closeForShutdown() {
        final GameProtocolAdapter current = adapter;
        final OutboundQueue queue = outbound();
        if ((current == null) || (queue == null)) {
            channel.close();
            return;
        }
        if (!queue.isClosing()) {
            queue.sendAndClose(current.serverCloseFrame(channel.alloc()));
        }
    }

    private void authenticated() {
        final ConnectionDeadlines deadlines = ConnectionDeadlines.of(channel);
        if (deadlines != null) {
            deadlines.authReached();
        }
        // The client sends nothing on the character selection screen, so the ping starts there and runs to the close.
        final IdlePolicy idle = IdlePolicy.of(channel);
        if (idle != null) {
            idle.startPinging(this::ping);
        }
    }

    private void ping() {
        final GameProtocolAdapter current = adapter;
        if (current != null) {
            outbound().send(current.pingFrame(channel.alloc(), pingIds.incrementAndGet()));
        }
    }
}
