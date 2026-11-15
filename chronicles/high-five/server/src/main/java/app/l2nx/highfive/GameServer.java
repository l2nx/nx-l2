/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive;

import app.l2nx.engine.core.partition.PartitionRuntime;
import app.l2nx.engine.core.session.SessionFactory;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.mechanics.punishment.PunishmentAffect;
import app.l2nx.highfive.mechanics.punishment.PunishmentType;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.highfive.network.gslink.LoginServerLink;
import app.l2nx.highfive.network.pipeline.GameChannelInitializer;
import app.l2nx.highfive.network.protocol.GameProtocolRegistry;
import app.l2nx.network.bootstrap.NetworkServer;
import app.l2nx.network.channel.guard.ConnectionLimiter;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.transport.NetworkEventLoops;
import app.l2nx.protocol.game.p267.crypt.GameXorKeys;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import java.time.Duration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * The client port and the GS-link. Stops last, after the world save and the partition runtime, so the farewell and
 * account logouts still reach the clients and the login server.
 */
@Component
@Slf4j
public class GameServer implements SmartLifecycle {
    public static final int PHASE = PartitionRuntime.PHASE - 256;

    private final LoginServerLink loginServerLink;
    private final LoginService loginService;
    private final NetworkEventLoops eventLoops;
    private final NetworkProperties networkProperties;
    private final NetworkMetrics networkMetrics;
    private final SessionFactory sessionFactory;
    private final GameProtocolRegistry protocolRegistry;
    private volatile NetworkServer gameServer;

    @Getter
    private volatile boolean running;

    public GameServer(
            LoginServerLink loginServerLink,
            LoginService loginService,
            NetworkEventLoops eventLoops,
            NetworkProperties networkProperties,
            NetworkMetrics networkMetrics,
            SessionFactory sessionFactory,
            GameProtocolRegistry protocolRegistry) {
        this.loginServerLink = loginServerLink;
        this.loginService = loginService;
        this.eventLoops = eventLoops;
        this.networkProperties = networkProperties;
        this.networkMetrics = networkMetrics;
        this.sessionFactory = sessionFactory;
        this.protocolRegistry = protocolRegistry;
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        try {
            final NetworkServer server = gameServer;
            if (server != null) {
                runStep("close the client connections", () -> server.close(GameServer::closeForShutdown));
            }
            leaveLoginServer();
        } finally {
            running = false;
        }
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    public void openPort() {
        final NetworkServer server = NetworkServer.bind(
                "game",
                new InetSocketAddress(ServerConfig.PORT_GAME),
                eventLoops,
                networkProperties,
                networkMetrics,
                new ConnectionLimiter(
                        networkProperties,
                        address -> PunishmentManager.getInstance()
                                .hasPunishment(address.getHostAddress(), PunishmentAffect.IP, PunishmentType.BAN)),
                new GameChannelInitializer(
                        networkProperties,
                        protocolRegistry,
                        sessionFactory,
                        GameClient::new,
                        GameXorKeys::getRandomKey));
        gameServer = server;

        try {
            loginServerLink.start();
        } catch (RuntimeException | Error e) {
            // No client may connect while the failed start closes the context and the world saves.
            gameServer = null;
            server.close();
            throw e;
        }
    }

    /** The open client port, {@code null} before {@link #openPort()} and after a failed one. */
    NetworkServer clientPort() {
        return gameServer;
    }

    public void stopPort() {
        final NetworkServer server = gameServer;
        if (server != null) {
            server.stopAccepting();
            server.stopReading();
        }
    }

    /**
     * Sessions log out from their drain after the channel closes, so the link stays open until all have: each
     * AccountLeft precedes the last status. {@code logoutAll} frees the rest; every step runs even if one failed.
     */
    void leaveLoginServer() {
        runStep("wait for the sessions to close", this::awaitSessionsClosed);
        runStep("log out the remaining accounts", loginService::logoutAll);
        runStep("shut the GS-link down", loginServerLink::shutdown);
    }

    private void awaitSessionsClosed() {
        final Duration timeout = networkProperties.shutdownTimeout();
        if (!sessionFactory.awaitClosed(timeout)) {
            log.warn(
                    "{} sessions had not closed within {}; their accounts are logged out without them.",
                    sessionFactory.openSessions(),
                    timeout);
        }
    }

    private static void runStep(String step, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("Shutdown failed to {}.", step, e);
        }
    }

    private static void closeForShutdown(Channel channel) {
        final GameConnection connection = GameConnection.of(channel);
        if (connection == null) {
            channel.close();
        } else {
            connection.closeForShutdown();
        }
    }
}
