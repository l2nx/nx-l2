/*
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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.login;

import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.gslink.GameServerLink;
import app.l2nx.login.util.FloodProtectorListener;
import java.io.IOException;
import java.net.Socket;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens for incoming game server connections on the configured login port.<br>
 * Delegates each accepted connection to a dedicated handler thread and tracks active handlers.
 * <ul>
 * <li>Uses flood protection to mitigate abusive connection attempts.</li>
 * <li>Binds to host and port configured in {@link LoginProperties.GameServer}.</li>
 * <li>Creates one {@link GameServerLink} per accepted socket.</li>
 * <li>Tracks active game server threads in a concurrent collection for monitoring.</li>
 * </ul>
 * @author BazookaRpm
 */
@Slf4j
public class GameServerListener extends FloodProtectorListener {

    // Active game server handler threads.
    @Getter
    private static final Collection<GameServerLink> gameServers = ConcurrentHashMap.newKeySet();

    /**
     * Constructs a new game server listener bound to the configured host and port.<br>
     * Uses flood protection to limit abusive connection attempts.
     * @throws IOException If binding the server socket fails.
     */
    public GameServerListener(LoginProperties.GameServer gameServer, LoginProperties.FloodProtection floodProtection)
            throws IOException {
        super(gameServer.host(), gameServer.port(), floodProtection);
        setName(getClass().getSimpleName() + "-" + gameServer.host() + ":" + gameServer.port());
    }

    /**
     * Registers a newly accepted game server socket and starts its handler thread.<br>
     * Applies basic validation and error handling to avoid leaving broken connections.
     * @param socket The accepted game server socket.
     */
    @Override
    public void addClient(Socket socket) {
        if ((socket == null) || socket.isClosed()) {
            log.warn("Attempted to add game server with null or closed socket.");
            return;
        }

        try {
            final GameServerLink gameServerLink =
                    new GameServerLink(socket); // The constructor itself already does start().
            gameServers.add(gameServerLink);

            if (log.isDebugEnabled()) {
                final String address = (socket.getInetAddress() != null)
                        ? socket.getInetAddress().getHostAddress()
                        : "unknown";
                log.debug(
                        "Registered new game server connection from {}. Active game servers: {}.",
                        address,
                        gameServers.size());
            }
        } catch (Exception e) {
            // Ensure that we do not keep a broken connection or leak resources if handler creation fails.
            log.warn("Failed to register game server connection from {}.", socket.getRemoteSocketAddress(), e);

            try {
                socket.close();
            } catch (IOException ioe) {
                log.debug("Failed to close socket after registration failure.", ioe);
            }
        }
    }

    /**
     * Removes a game server thread from the active collection.<br>
     * Intended to be called when a game server handler terminates.
     * @param gameServerLink The game server thread to remove.
     */
    public void removeGameServer(GameServerLink gameServerLink) {
        if (gameServerLink == null) {
            log.warn("Attempted to remove null game server thread.");
            return;
        }

        final boolean removed = gameServers.remove(gameServerLink);

        if (log.isDebugEnabled()) {
            log.debug(
                    "Removed game server thread {} (removed={}). Active game servers: {}.",
                    gameServerLink,
                    removed,
                    gameServers.size());
        }
    }

    /**
     * Returns the number of active game server connections.
     * @return The current game server count.
     */
    public static int getGameServerCount() {
        return gameServers.size();
    }
}
