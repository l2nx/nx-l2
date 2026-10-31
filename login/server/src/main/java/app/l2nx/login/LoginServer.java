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
package app.l2nx.login;

import app.l2nx.boot.ServerExit;
import app.l2nx.boot.ServerExitCode;
import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.gameserver.GameServerTable;
import app.l2nx.login.network.LoginChannelInitializer;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.network.LoginKeys;
import app.l2nx.login.protocol.LoginVariant;
import app.l2nx.login.protocol.c621.LoginRoutes;
import app.l2nx.login.protocol.c621.ServerListMapper;
import app.l2nx.login.protocol.c621.handler.RequestAuthLoginHandler;
import app.l2nx.login.session.AddressBans;
import app.l2nx.login.session.LoginController;
import app.l2nx.network.bootstrap.NetworkServer;
import app.l2nx.network.channel.guard.ConnectionLimiter;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.exec.BlockingTaskExecutor;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.transport.NetworkEventLoops;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.Channel;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * @author BazookaRpm
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LoginServer implements SmartLifecycle {

    private static final Path BANNED_IPS_FILE = Path.of("./banned_ip.cfg");

    private final LoginProperties properties;
    private final LoginController loginController;
    private final GameServerTable gameServerTable;
    private final AddressBans addressBans;
    private final LoginRoutes routes;
    private final ServerListMapper serverLists;
    private final TaskScheduler taskScheduler;
    private final ServerExit serverExit;
    private final MeterRegistry meterRegistry;
    private final NetworkEventLoops eventLoops;
    private final NetworkProperties networkProperties;
    private final NetworkMetrics networkMetrics;
    private final List<NetworkServer> clientServers = new ArrayList<>();
    private BlockingTaskExecutor blockingTasks;
    private ScheduledFuture<?> restartTask;

    @Getter
    private volatile boolean running;

    /**
     * On failure, shuts down whatever already started before rethrowing: Spring calls {@link #stop()} only on started components.
     */
    @Override
    public void start() {
        try {
            initializeCoreServices();
            loadBanFile(BANNED_IPS_FILE);
            configureScheduledRestart();
            startLoginClientListener();
        } catch (RuntimeException | Error e) {
            shutdownServices();
            throw e;
        }

        running = true;
    }

    // Before Boot's gRPC server (Integer.MAX_VALUE): game servers register only against the loaded server table, and
    // their streams end before the client listeners close.
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1;
    }

    @Override
    public void stop() {
        shutdownServices();
        running = false;
    }

    private void shutdownServices() {
        clientServers.forEach(NetworkServer::stopAccepting);
        clientServers.forEach(NetworkServer::stopReading);
        clientServers.forEach(server -> server.close(LoginServer::closeForShutdown));
        clientServers.clear();
        if (blockingTasks != null) {
            blockingTasks.close();
            blockingTasks = null;
        }

        if (restartTask != null) {
            restartTask.cancel(false);
            restartTask = null;
        }

        loginController.shutdown();
    }

    private static void closeForShutdown(Channel channel) {
        final LoginClient client = LoginClient.of(channel);
        if (client == null) {
            channel.close();
        } else {
            client.closeForShutdown();
        }
    }

    private void initializeCoreServices() {
        try {
            loginController.load();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "LoginController initialization failed due to security configuration error.", e);
        }

        gameServerTable.load();
    }

    private void configureScheduledRestart() {
        if (!properties.scheduledRestart().enabled()) {
            return;
        }

        final Duration restartDelay = properties.scheduledRestart().delay();
        log.info("Login server restart scheduled in {}.", restartDelay);
        restartTask = taskScheduler.schedule(() -> shutdown(true), Instant.now().plus(restartDelay));
    }

    private void startLoginClientListener() {
        final LoginProperties.Client client = properties.client();
        blockingTasks = new BlockingTaskExecutor("l2nx-login-tasks", client.maxConcurrentTasks(), meterRegistry);
        final ConnectionLimiter limiter = new ConnectionLimiter(networkProperties, addressBans::isBanned);
        client.ports().stream()
                .map(LoginProperties.ClientListener::variant)
                .distinct()
                .forEach(this::warmUp);
        for (LoginProperties.ClientListener listener : client.ports()) {
            clientServers.add(NetworkServer.bind(
                    "login-" + listener.variant().id(),
                    new InetSocketAddress(client.host(), listener.port()),
                    eventLoops,
                    networkProperties,
                    networkMetrics,
                    limiter,
                    new LoginChannelInitializer(
                            listener.variant(),
                            networkProperties,
                            blockingTasks,
                            () -> new LoginKeys(
                                    loginController.getScrambledRSAKeyPair(),
                                    LoginKeys.newBlowfishKey(LoginController.SECURE_RANDOM)),
                            LoginController.SECURE_RANDOM,
                            loginController::removeAuthedLoginClientIfCurrent,
                            routes,
                            serverLists)));
        }
    }

    private void warmUp(LoginVariant variant) {
        try {
            switch (variant) {
                case C621 ->
                    RequestAuthLoginHandler.warmUp(
                            loginController.getScrambledRSAKeyPair().getPrivateKey());
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA is unavailable for the login variant " + variant.id() + ".", e);
        }
    }

    void loadBanFile(Path path) {
        if (!Files.isRegularFile(path)) {
            log.warn(
                    "IP bans configuration file ({}) does not exist or is not a regular file. No IP bans have been"
                            + " loaded.",
                    path.toAbsolutePath());
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            int lineNumber = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                processBanLine(path, ++lineNumber, line);
            }
        } catch (IOException e) {
            log.warn(
                    "Error while reading IP bans configuration file ({}). Details: {}",
                    path.toAbsolutePath(),
                    e.getMessage(),
                    e);
        }

        log.info("Loaded {} IP Bans.", addressBans.size());
    }

    private void processBanLine(Path source, int lineNumber, String rawLine) {
        final String line = rawLine.trim();
        if (line.isEmpty() || (line.charAt(0) == '#')) {
            return;
        }
        final int comment = line.indexOf('#');
        final String[] tokens =
                (comment >= 0 ? line.substring(0, comment) : line).trim().split("\\s+");
        long durationMillis = 0;
        if (tokens.length > 1) {
            try {
                durationMillis = Long.parseLong(tokens[1]);
            } catch (NumberFormatException e) {
                log.warn("Skipped the IP ban in {} at line {}: invalid duration '{}'.", source, lineNumber, tokens[1]);
                return;
            }
        }
        if (!addressBans.add(tokens[0], durationMillis)) {
            log.warn("Skipped the IP ban in {} at line {}: '{}' is not an IP address.", source, lineNumber, tokens[0]);
        }
    }

    public void shutdown(boolean restart) {
        serverExit.exit(restart ? ServerExitCode.RESTART : ServerExitCode.SHUTDOWN);
    }
}
