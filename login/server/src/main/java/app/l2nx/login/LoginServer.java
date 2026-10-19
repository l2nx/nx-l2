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
import app.l2nx.login.controller.LoginController;
import app.l2nx.login.data.GameServerTable;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.network.LoginPacketHandler;
import app.l2nx.login.network.gameserverpackets.receive.ServerStatus;
import app.l2nx.network.ConnectionManager;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Bootstraps and drives the login server lifecycle.<br>
 * Coordinates core service initialization and network listener startup.
 * <ul>
 * <li>Loads login controller and game server table.</li>
 * <li>Registers IP bans and starts listeners for game servers and login clients.</li>
 * </ul>
 * @author BazookaRpm
 */
@Component
public class LoginServer implements SmartLifecycle {
    // Logger.
    public static final Logger LOGGER = Logger.getLogger(LoginServer.class.getName());

    // Configuration paths.
    private static final String BANNED_IPS_CONFIGURATION_PATH = "./banned_ip.cfg";

    // Protocol.
    public static final int PROTOCOL_REV = 0x0106;

    // Singleton instance.
    private static LoginServer _instance;

    // Network listeners.
    private GameServerListener _gameServerListener;
    private ConnectionManager<LoginClient> clientConnectionManager;

    // Login server status.
    private static volatile int _loginStatus = ServerStatus.STATUS_NORMAL;

    private final LoginProperties properties;
    private final LoginController loginController;
    private final GameServerTable gameServerTable;
    private final TaskScheduler taskScheduler;
    private final ServerExit serverExit;
    private final MeterRegistry meterRegistry;
    private ScheduledFuture<?> restartTask;
    private volatile boolean running;

    public LoginServer(
            LoginProperties properties,
            LoginController loginController,
            GameServerTable gameServerTable,
            TaskScheduler taskScheduler,
            ServerExit serverExit,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.loginController = loginController;
        this.gameServerTable = gameServerTable;
        this.taskScheduler = taskScheduler;
        this.serverExit = serverExit;
        this.meterRegistry = meterRegistry;
        _instance = this;
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
            initializeNetworkListeners();
        } catch (RuntimeException | Error e) {
            shutdownServices();
            throw e;
        }

        running = true;
    }

    @Override
    public void stop() {
        shutdownServices();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void shutdownServices() {
        if (clientConnectionManager != null) {
            clientConnectionManager.shutdown();
            clientConnectionManager = null;
        }

        if (_gameServerListener != null) {
            _gameServerListener.close();
        }

        if (restartTask != null) {
            restartTask.cancel(false);
            restartTask = null;
        }

        loginController.shutdown();
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

    /**
     * Initializes security-related resources such as IP bans.
     */
    private void initializeSecurityAndBans() {
        loadBanFile();
    }

    /**
     * Configures an optional scheduled restart for the login server based on configuration values.
     */
    private void configureScheduledRestart() {
        if (!properties.scheduledRestart().enabled()) {
            return;
        }

        final Duration restartDelay = properties.scheduledRestart().delay();
        LOGGER.info("Login server restart scheduled in " + restartDelay + ".");
        restartTask = taskScheduler.schedule(() -> shutdown(true), Instant.now().plus(restartDelay));
    }

    /**
     * Starts listeners for game servers and login clients.
     */
    private void initializeNetworkListeners() {
        startGameServerListener();
        startLoginClientListener();
    }

    /**
     * Starts the game server listener socket.
     */
    private void startGameServerListener() {
        final LoginProperties.GameServer gameServer = properties.gameServer();
        try {
            _gameServerListener = new GameServerListener(gameServer, properties.floodProtection());
            _gameServerListener.start();

            LOGGER.info("Game server listener is listening on " + gameServer.host() + ":" + gameServer.port() + ".");
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Game server listener could not be started on " + gameServer.host() + ":" + gameServer.port() + ".",
                    e);
        }
    }

    /**
     * Starts the login client listener using the generic connection manager.
     */
    private void startLoginClientListener() {
        final LoginProperties.Client client = properties.client();
        try {
            clientConnectionManager = new ConnectionManager<>(
                    new InetSocketAddress(client.host(), client.port()),
                    LoginClient::new,
                    new LoginPacketHandler(),
                    properties.network());
            clientConnectionManager.bindMetrics(meterRegistry, "l2nx-login");
            LOGGER.info(getClass().getSimpleName() + ": Login client listener started on " + client.host() + ":"
                    + client.port() + ".");
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Login client listener could not be started on " + client.host() + ":" + client.port() + ".", e);
        }
    }

    public LoginProperties getProperties() {
        return properties;
    }

    /**
     * Gets the game server listener instance.
     * @return The game server listener instance.
     */
    public GameServerListener getGameServerListener() {
        return _gameServerListener;
    }

    /**
     * Loads banned IP definitions from configuration file and registers them into the login controller.
     */
    public void loadBanFile() {
        final File bannedIpConfigurationFile = new File(BANNED_IPS_CONFIGURATION_PATH);
        if (!bannedIpConfigurationFile.exists() || !bannedIpConfigurationFile.isFile()) {
            LOGGER.warning("IP bans configuration file (" + bannedIpConfigurationFile.getAbsolutePath()
                    + ") does not exist or is not a regular file. No IP bans have been loaded.");
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
            LOGGER.log(
                    Level.WARNING,
                    "Error while reading IP bans configuration file (" + bannedIpConfigurationFile.getAbsolutePath()
                            + "). Details: " + e.getMessage(),
                    e);
        }

        LOGGER.info("Loaded " + LoginController.getInstance().getBannedIps().size() + " IP Bans.");
    }

    /**
     * Parses and registers a single IP ban definition line.
     * @param sourceFile The configuration file being processed.
     * @param lineReader The line reader used to obtain the current line number.
     * @param rawLine The raw line text to parse.
     */
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
            LOGGER.warning("Skipped IP ban entry with empty definition in file (" + sourceFile.getAbsolutePath()
                    + ") at line " + lineReader.getLineNumber() + ".");
            return;
        }

        final String[] tokens = definitionPart.split("\\s+");
        if (tokens.length == 0) {
            LOGGER.warning("Skipped IP ban entry with no address token in file (" + sourceFile.getAbsolutePath()
                    + ") at line " + lineReader.getLineNumber() + ".");
            return;
        }

        final String ipAddress = tokens[0];
        long durationMillis = 0;

        if (tokens.length > 1) {
            final String durationToken = tokens[1];
            try {
                durationMillis = Long.parseLong(durationToken);
            } catch (NumberFormatException e) {
                LOGGER.warning("Skipped IP ban entry due to invalid duration token '" + durationToken + "' in file ("
                        + sourceFile.getAbsolutePath() + ") at line " + lineReader.getLineNumber() + ".");
                return;
            }
        }

        try {
            LoginController.getInstance().addBanForAddress(ipAddress, durationMillis);
        } catch (Exception e) {
            LOGGER.warning("Skipped IP ban registration for address '" + ipAddress + "' from file ("
                    + sourceFile.getAbsolutePath() + ") at line " + lineReader.getLineNumber() + ". Reason: "
                    + e.getMessage() + ".");
        }
    }

    /**
     * Shuts down the login server and optionally signals restart to the supervisor.
     * @param restart True to request a restart exit code.
     */
    public void shutdown(boolean restart) {
        serverExit.exit(restart ? ServerExitCode.RESTART : ServerExitCode.SHUTDOWN);
    }

    /**
     * Gets the current login server status.
     * @return The current login server status.
     */
    public int getStatus() {
        return _loginStatus;
    }

    /**
     * Sets the current login server status.
     * @param status The new login server status.
     */
    public void setStatus(int status) {
        _loginStatus = status;
    }

    /**
     * Gets the login server singleton instance.
     * @return The login server singleton instance.
     */
    public static LoginServer getInstance() {
        return _instance;
    }
}
