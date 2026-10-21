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
import app.l2nx.gslink.v1.GsLinkProto.ListingStatus;
import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.controller.LoginController;
import app.l2nx.login.data.GameServerTable;
import app.l2nx.login.network.LoginChannelInitializer;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.network.LoginKeys;
import app.l2nx.login.protocol.LoginVariant;
import app.l2nx.login.protocol.c621.handler.RequestAuthLoginHandler;
import app.l2nx.network.bootstrap.NetworkServer;
import app.l2nx.network.channel.guard.ConnectionLimiter;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.exec.BlockingTaskExecutor;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.transport.NetworkEventLoops;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.Channel;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * @author BazookaRpm
 */
@Component
@Slf4j
public class LoginServer implements SmartLifecycle {

    private static final String BANNED_IPS_CONFIGURATION_PATH = "./banned_ip.cfg";

    @Getter
    private static LoginServer instance;

    private static volatile int status = ListingStatus.LISTING_STATUS_NORMAL_VALUE;

    @Getter
    private final LoginProperties properties;

    private final LoginController loginController;
    private final GameServerTable gameServerTable;
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

    public LoginServer(
            LoginProperties properties,
            LoginController loginController,
            GameServerTable gameServerTable,
            TaskScheduler taskScheduler,
            ServerExit serverExit,
            MeterRegistry meterRegistry,
            NetworkEventLoops eventLoops,
            NetworkProperties networkProperties,
            NetworkMetrics networkMetrics) {
        this.properties = properties;
        this.loginController = loginController;
        this.gameServerTable = gameServerTable;
        this.taskScheduler = taskScheduler;
        this.serverExit = serverExit;
        this.meterRegistry = meterRegistry;
        this.eventLoops = eventLoops;
        this.networkProperties = networkProperties;
        this.networkMetrics = networkMetrics;
        instance = this;
    }

    /**
     * On failure, shuts down whatever already started before rethrowing: Spring calls {@link #stop()} only on started components.
     */
    @Override
    public void start() {
        try {
            initializeCoreServices();
            initializeSecurityAndBans();
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

    private void initializeSecurityAndBans() {
        loadBanFile();
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
        final ConnectionLimiter limiter = new ConnectionLimiter(networkProperties, loginController::isBannedAddress);
        client.ports().stream()
                .map(LoginProperties.ClientPort::variant)
                .distinct()
                .forEach(this::warmUp);
        for (LoginProperties.ClientPort port : client.ports()) {
            clientServers.add(NetworkServer.bind(
                    "login-" + port.variant().id(),
                    new InetSocketAddress(client.host(), port.port()),
                    eventLoops,
                    networkProperties,
                    networkMetrics,
                    limiter,
                    new LoginChannelInitializer(
                            port.variant(),
                            networkProperties,
                            blockingTasks,
                            () -> new LoginKeys(
                                    loginController.getScrambledRSAKeyPair(), loginController.getBlowfishKey()),
                            LoginController.SECURE_RANDOM,
                            loginController::removeAuthedLoginClientIfCurrent)));
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

    public void loadBanFile() {
        final File bannedIpConfigurationFile = new File(BANNED_IPS_CONFIGURATION_PATH);
        if (!bannedIpConfigurationFile.exists() || !bannedIpConfigurationFile.isFile()) {
            log.warn(
                    "IP bans configuration file ({}) does not exist or is not a regular file. No IP bans have been"
                            + " loaded.",
                    bannedIpConfigurationFile.getAbsolutePath());
            return;
        }

        try (FileInputStream bannedIpFileInputStream = new FileInputStream(bannedIpConfigurationFile);
                InputStreamReader bannedIpReader = new InputStreamReader(bannedIpFileInputStream);
                LineNumberReader bannedIpLineReader = new LineNumberReader(bannedIpReader)) {
            String rawLine;
            while ((rawLine = bannedIpLineReader.readLine()) != null) {
                processBanLine(bannedIpConfigurationFile, bannedIpLineReader, rawLine);
            }
        } catch (IOException e) {
            log.warn(
                    "Error while reading IP bans configuration file ({}). Details: {}",
                    bannedIpConfigurationFile.getAbsolutePath(),
                    e.getMessage(),
                    e);
        }

        log.info(
                "Loaded {} IP Bans.",
                LoginController.getInstance().getBannedIps().size());
    }

    private void processBanLine(File sourceFile, LineNumberReader lineReader, String rawLine) {
        if (rawLine == null) {
            return;
        }

        final String trimmedLine = rawLine.trim();
        if (trimmedLine.isEmpty()) {
            return;
        }

        if (trimmedLine.charAt(0) == '#') {
            return;
        }

        final int commentIndex = trimmedLine.indexOf('#');
        final String definitionPart =
                (commentIndex >= 0) ? trimmedLine.substring(0, commentIndex).trim() : trimmedLine;
        if (definitionPart.isEmpty()) {
            log.warn(
                    "Skipped IP ban entry with empty definition in file ({}) at line {}.",
                    sourceFile.getAbsolutePath(),
                    lineReader.getLineNumber());
            return;
        }

        final String[] tokens = definitionPart.split("\\s+");
        if (tokens.length == 0) {
            log.warn(
                    "Skipped IP ban entry with no address token in file ({}) at line {}.",
                    sourceFile.getAbsolutePath(),
                    lineReader.getLineNumber());
            return;
        }

        final String ipAddress = tokens[0];
        long durationMillis = 0;

        if (tokens.length > 1) {
            final String durationToken = tokens[1];
            try {
                durationMillis = Long.parseLong(durationToken);
            } catch (NumberFormatException e) {
                log.warn(
                        "Skipped IP ban entry due to invalid duration token '{}' in file ({}) at line {}.",
                        durationToken,
                        sourceFile.getAbsolutePath(),
                        lineReader.getLineNumber());
                return;
            }
        }

        try {
            LoginController.getInstance().addBanForAddress(ipAddress, durationMillis);
        } catch (Exception e) {
            log.warn(
                    "Skipped IP ban registration for address '{}' from file ({}) at line {}. Reason: {}.",
                    ipAddress,
                    sourceFile.getAbsolutePath(),
                    lineReader.getLineNumber(),
                    e.getMessage());
        }
    }

    public void shutdown(boolean restart) {
        serverExit.exit(restart ? ServerExitCode.RESTART : ServerExitCode.SHUTDOWN);
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        LoginServer.status = status;
    }
}
