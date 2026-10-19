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
package app.l2nx.login.util;

import app.l2nx.login.config.LoginProperties;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

/**
 * Listens for incoming login connections and applies basic IP based flood protection.<br>
 * Maintains per address connection state and delegates accepted sockets to subclasses.
 * <ul>
 * <li>Binds a server socket on a configured address and port.</li>
 * <li>Tracks connection count and timestamps per remote IP.</li>
 * </ul>
 * @author BazookaRpm
 */
@Slf4j
public abstract class FloodProtectorListener extends Thread {

    // Server socket backlog size.
    private static final int SERVER_SOCKET_BACKLOG = 50;

    // Flood protection state per remote IP.
    private final Map<String, ForeignConnection> floodProtection = new ConcurrentHashMap<>();

    // Login server listening socket.
    private final ServerSocket serverSocket;

    private final LoginProperties.FloodProtection floodProtectionSettings;

    /**
     * Creates a new flood protector listener bound to the given IP and port.
     * @param listenIp The IP address to bind, or "*" to listen on all interfaces.
     * @param port The TCP port to listen on.
     * @throws IOException If the socket cannot be created or bound.
     */
    public FloodProtectorListener(String listenIp, int port, LoginProperties.FloodProtection floodProtection)
            throws IOException {
        floodProtectionSettings = floodProtection;

        // If listening IP is "*", listen on all interfaces.
        if ("*".equals(listenIp)) {
            serverSocket = new ServerSocket(port);
        } else {
            // Listen on the specified IP with a fixed backlog size.
            serverSocket = new ServerSocket(port, SERVER_SOCKET_BACKLOG, InetAddress.getByName(listenIp));
        }
    }

    /**
     * Accepts incoming connections and applies flood protection rules.
     */
    @Override
    public void run() {
        Socket clientSocket = null;
        while (!isInterrupted()) {
            try {
                // Accept incoming connections.
                clientSocket = serverSocket.accept();

                if (floodProtectionSettings.enabled()) {
                    // Check flood protection for this remote address.
                    final String clientAddress = clientSocket.getInetAddress().getHostAddress();
                    ForeignConnection foreignConnection = floodProtection.get(clientAddress);
                    if (foreignConnection != null) {
                        foreignConnection.connectionNumber += 1;

                        if (((foreignConnection.connectionNumber > floodProtectionSettings.fastConnectionLimit())
                                        && ((System.currentTimeMillis() - foreignConnection.lastConnection)
                                                < floodProtectionSettings
                                                        .normalConnectionTime()
                                                        .toMillis()))
                                || ((System.currentTimeMillis() - foreignConnection.lastConnection)
                                        < floodProtectionSettings
                                                .fastConnectionTime()
                                                .toMillis())
                                || (foreignConnection.connectionNumber
                                        > floodProtectionSettings.maxConnectionsPerIp())) {
                            foreignConnection.lastConnection = System.currentTimeMillis();
                            clientSocket.close();
                            foreignConnection.connectionNumber -= 1;

                            if (!foreignConnection.flooding) {
                                log.warn("Potential flood from address {}.", clientAddress);
                            }

                            foreignConnection.flooding = true;
                            continue;
                        }

                        // If the connection was previously considered flooding but now passes the check.
                        if (foreignConnection.flooding) {
                            foreignConnection.flooding = false;
                            log.info("Address {} is no longer considered as flooding.", clientAddress);
                        }

                        foreignConnection.lastConnection = System.currentTimeMillis();
                    } else {
                        // Initialize flood protection state for the new remote address.
                        foreignConnection = new ForeignConnection(System.currentTimeMillis());
                        floodProtection.put(clientAddress, foreignConnection);
                    }
                }

                // Add client connection for further processing (implementation in subclasses).
                addClient(clientSocket);
            } catch (Exception e) {
                // Handle exceptions and potential thread interruption.
                if (isInterrupted()) {
                    // Close server socket and break the loop on thread interruption.
                    try {
                        serverSocket.close();
                    } catch (IOException io) {
                        LoggerFactory.getLogger(getClass())
                                .info("Error while closing server socket on interruption", io);
                    }
                    break;
                }

                LoggerFactory.getLogger(getClass()).warn("Unexpected exception in the main loop", e);
            }
        }
    }

    /**
     * Represents flood protection state for a single remote IP address.
     */
    protected static class ForeignConnection {
        public int connectionNumber;
        public long lastConnection;
        public boolean flooding = false;

        /**
         * Creates a new foreign connection record with an initial timestamp.
         * @param time The timestamp of the first connection.
         */
        public ForeignConnection(long time) {
            // Initialize a new foreign connection.
            lastConnection = time;
            connectionNumber = 1;
        }
    }

    /**
     * Adds a client connection for further processing in subclasses.
     * @param socket The accepted client socket.
     */
    public abstract void addClient(Socket socket);

    /**
     * Decrements and optionally removes flood protection entry for the given IP.<br>
     * Should be called when the session or socket associated with the given address is closed.
     * @param ip The remote IP address.
     */
    public void removeFloodProtection(String ip) {
        // Only proceed if flood protection is enabled.
        if (!floodProtectionSettings.enabled()) {
            return;
        }

        // Decrement connection count or remove if no more connections exist.
        final ForeignConnection foreignConnection = floodProtection.get(ip);
        if (foreignConnection != null) {
            foreignConnection.connectionNumber -= 1;
            if (foreignConnection.connectionNumber == 0) {
                floodProtection.remove(ip);
            }
        } else {
            log.warn("Attempted to remove flood protection for unknown IP {}.", ip);
        }
    }

    /**
     * Closes the listening socket and ends the accept loop.
     */
    public void close() {
        // Interrupt first: the loop exits only when interrupted, otherwise the closed socket makes accept() fail in a
        // busy loop.
        interrupt();
        try {
            serverSocket.close();
        } catch (IOException e) {
            LoggerFactory.getLogger(getClass()).warn("Error while closing server socket", e);
        }
    }
}
