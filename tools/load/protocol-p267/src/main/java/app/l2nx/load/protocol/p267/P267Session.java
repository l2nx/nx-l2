/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267;

import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.Login;
import app.l2nx.load.bot.intent.SelectServer;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.protocol.ClientSession;
import app.l2nx.load.protocol.SessionListener;
import app.l2nx.load.protocol.p267.game.GameExchange;
import app.l2nx.load.protocol.p267.game.GameTicket;
import app.l2nx.network.bootstrap.NetworkClient;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.transport.ResolvedTransport;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;

/** One load bot through the login and game servers; confined to its event loop. */
final class P267Session implements ClientSession {
    private final P267Settings settings;
    private final ResolvedTransport transport;
    private final NetworkProperties network;
    private final EventLoop loop;
    private final SessionListener listener;
    private final LoginExchange login;
    private GameExchange game;
    private boolean closed;

    P267Session(
            P267Settings settings,
            ResolvedTransport transport,
            NetworkProperties network,
            EventLoop loop,
            SessionListener listener) {
        this.settings = settings;
        this.transport = transport;
        this.network = network;
        this.loop = loop;
        this.listener = listener;
        login = new LoginExchange(settings.serverId(), listener, this::loggedIn);
    }

    /** The connect runs as a later task, so no listener call and no connect failure lands inside {@code open}. */
    void start() {
        loop.execute(this::connect);
    }

    @Override
    public void send(Intent intent) {
        if (closed) {
            return;
        }
        switch (intent) {
            case Login loginIntent -> login.login(loginIntent);
            case SelectServer _ -> login.selectServer();
            default -> {
                if (game == null) {
                    fail(new IllegalStateException("Protocol " + P267Protocol.ID + " cannot send " + intent.kind()
                            + " before the game server"));
                } else {
                    game.send(intent);
                }
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        login.close();
        if (game != null) {
            game.close();
        }
    }

    private void connect() {
        if (closed) {
            return;
        }
        NetworkClient.connect(
                        settings.login(),
                        loop,
                        transport,
                        settings.connectTimeout(),
                        network,
                        NetworkMetrics.noop(),
                        channel ->
                                login.install(channel, (int) network.maxFrame().toBytes()))
                .addListener((ChannelFuture future) -> {
                    if (!future.isSuccess()) {
                        login.connectFailed(future.cause());
                    }
                });
    }

    private void loggedIn(GameTicket ticket) {
        listener.stateChanged(LoadBotState.GAME_CONNECT);
        // A load bot whose scenario ends here has closed the session by now.
        if (closed) {
            return;
        }
        final GameExchange exchange = new GameExchange(ticket, listener, System::nanoTime);
        game = exchange;
        NetworkClient.connect(
                        ticket.gameServer(),
                        loop,
                        transport,
                        settings.connectTimeout(),
                        network,
                        NetworkMetrics.noop(),
                        exchange::install)
                .addListener((ChannelFuture future) -> {
                    if (!future.isSuccess()) {
                        exchange.connectFailed(future.cause());
                    }
                });
    }

    private void fail(Throwable cause) {
        if (closed) {
            return;
        }
        close();
        listener.failed(cause);
    }
}
