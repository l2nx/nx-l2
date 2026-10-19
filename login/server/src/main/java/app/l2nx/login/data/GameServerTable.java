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
package app.l2nx.login.data;

import app.l2nx.login.GameServerThread;
import app.l2nx.login.LoginServer;
import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.network.gameserverpackets.receive.ServerStatus;
import app.l2nx.login.repository.GameServerRepository;
import app.l2nx.login.util.Subnet;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The Class GameServerTable loads the game server names and initialize the game server tables.
 * @author KenM
 */
@Component
@Slf4j
public class GameServerTable {

    private static final String SERVER_NAMES_FILE = "data/servername.xml";

    // Map for storing server names with their corresponding IDs.
    private static final Map<Integer, String> SERVER_NAMES = new HashMap<>();

    // Table for storing game server information.
    private static final Map<Integer, GameServerInfo> GAME_SERVER_TABLE = new HashMap<>();

    // Configuration for RSA key pairs.
    private static final int KEYS_SIZE = 10;
    private KeyPair[] keyPairs;

    private static GameServerTable INSTANCE;

    private final GameServerRepository gameServers;
    private final LoginProperties properties;

    public GameServerTable(GameServerRepository gameServers, LoginProperties properties) {
        this.gameServers = gameServers;
        this.properties = properties;
        INSTANCE = this;
    }

    public void load() {
        loadServerNames();

        loadRegisteredGameServers();
        log.info("Loaded {} registered Game Servers.", GAME_SERVER_TABLE.size());

        initRSAKeys();
        log.info("Cached {} RSA keys for Game Server communication.", keyPairs.length);
    }

    private void loadServerNames() {
        SERVER_NAMES.clear();
        final File file = new File(properties.datapackRoot(), SERVER_NAMES_FILE);
        try (InputStream input = Files.newInputStream(file.toPath())) {
            final XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            final XMLStreamReader reader = factory.createXMLStreamReader(input);
            try {
                while (reader.hasNext()) {
                    if ((reader.next() == XMLStreamConstants.START_ELEMENT) && "server".equals(reader.getLocalName())) {
                        SERVER_NAMES.put(
                                Integer.parseInt(reader.getAttributeValue(null, "id")),
                                reader.getAttributeValue(null, "name"));
                    }
                }
            } finally {
                reader.close();
            }
        } catch (IOException | XMLStreamException | RuntimeException e) {
            log.warn("Error parsing {}.", file.getAbsolutePath(), e);
        }

        log.info("Loaded {} server names.", SERVER_NAMES.size());
    }

    /**
     * Inits the RSA keys.
     */
    private void initRSAKeys() {
        try {
            final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(new RSAKeyGenParameterSpec(512, RSAKeyGenParameterSpec.F4));
            keyPairs = new KeyPair[KEYS_SIZE];
            for (int i = 0; i < KEYS_SIZE; i++) {
                keyPairs[i] = keyGen.genKeyPair();
            }
        } catch (Exception e) {
            log.error("Error loading RSA keys for Game Server communication!");
        }
    }

    /**
     * Load registered game servers.
     */
    private void loadRegisteredGameServers() {
        try {
            gameServers
                    .findHexIds()
                    .forEach((id, hexId) -> GAME_SERVER_TABLE.put(id, new GameServerInfo(id, stringToHex(hexId))));
        } catch (Exception e) {
            log.error("Error loading registered game servers!");
        }
    }

    /**
     * Gets the registered game servers.
     * @return the registered game servers
     */
    public Map<Integer, GameServerInfo> getRegisteredGameServers() {
        return GAME_SERVER_TABLE;
    }

    /**
     * Gets the registered game server by id.
     * @param id the game server Id
     * @return the registered game server by id
     */
    public GameServerInfo getRegisteredGameServerById(int id) {
        return GAME_SERVER_TABLE.get(id);
    }

    /**
     * Checks for registered game server on id.
     * @param id the id
     * @return true, if successful
     */
    public boolean hasRegisteredGameServerOnId(int id) {
        return GAME_SERVER_TABLE.containsKey(id);
    }

    /**
     * Register with first available id.
     * @param gsi the game server information DTO
     * @return true, if successful
     */
    public boolean registerWithFirstAvailableId(GameServerInfo gsi) {
        // Avoid two servers registering with the same "free" id.
        synchronized (GAME_SERVER_TABLE) {
            for (Integer serverId : SERVER_NAMES.keySet()) {
                if (!GAME_SERVER_TABLE.containsKey(serverId)) {
                    GAME_SERVER_TABLE.put(serverId, gsi);
                    gsi.setId(serverId);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Register a game server.
     * @param id the id
     * @param gsi the gsi
     * @return true, if successful
     */
    public boolean register(int id, GameServerInfo gsi) {
        // Avoid two servers registering with the same id.
        synchronized (GAME_SERVER_TABLE) {
            if (!GAME_SERVER_TABLE.containsKey(id)) {
                GAME_SERVER_TABLE.put(id, gsi);
                return true;
            }
        }

        return false;
    }

    /**
     * Wrapper method.
     * @param gsi the game server info DTO.
     */
    public void registerServerOnDB(GameServerInfo gsi) {
        registerServerOnDB(gsi.getHexId(), gsi.getId(), gsi.getExternalHost());
    }

    /**
     * Register server on db.
     * @param hexId the hex id
     * @param id the id
     * @param externalHost the external host
     */
    public void registerServerOnDB(byte[] hexId, int id, String externalHost) {
        register(id, new GameServerInfo(id, hexId));
        try {
            gameServers.create(id, hexToString(hexId), externalHost);
        } catch (Exception e) {
            log.error("Error while saving gameserver!");
        }
    }

    /**
     * Gets the server name by id.
     * @param id the id
     * @return the server name by id
     */
    public String getServerNameById(int id) {
        return SERVER_NAMES.get(id);
    }

    /**
     * Gets the server names.
     * @return the game server names map.
     */
    public Map<Integer, String> getServerNames() {
        return SERVER_NAMES;
    }

    /**
     * Gets the key pair.
     * @return a random key pair.
     */
    public KeyPair getKeyPair() {
        return keyPairs[ThreadLocalRandom.current().nextInt(keyPairs.length)];
    }

    /**
     * String to hex.
     * @param string the string to convert.
     * @return return the hex representation.
     */
    private byte[] stringToHex(String string) {
        return new BigInteger(string, 16).toByteArray();
    }

    /**
     * Hex to string.
     * @param hex the hex value to convert.
     * @return the string representation.
     */
    private String hexToString(byte[] hex) {
        if (hex == null) {
            return "null";
        }

        return new BigInteger(hex).toString(16);
    }

    /**
     * The Class GameServerInfo.
     */
    public static class GameServerInfo {
        // Authentication fields.
        @Getter
        @Setter
        private int id;

        @Getter
        private final byte[] hexId;

        @Getter
        @Setter
        private boolean authed;

        // Status information.
        @Getter
        @Setter
        private GameServerThread gameServerThread;

        @Getter
        private int status;

        // Network configuration.
        private final List<GameServerAddress> addrs = new ArrayList<>(5);

        @Getter
        @Setter
        private int port;

        // Configuration settings.
        private static final boolean IS_PVP = true;

        @Getter
        @Setter
        private int serverType;

        @Getter
        @Setter
        private int ageLimit;

        @Getter
        @Setter
        private boolean showingBrackets;

        @Getter
        @Setter
        private int maxPlayers;

        /**
         * Instantiates a new game server info.
         * @param id the id
         * @param hexId the hex id
         * @param gst the gst
         */
        public GameServerInfo(int id, byte[] hexId, GameServerThread gst) {
            this.id = id;
            this.hexId = hexId;
            gameServerThread = gst;
            status = ServerStatus.STATUS_DOWN;
        }

        /**
         * Instantiates a new game server info.
         * @param id the id
         * @param hexId the hex id
         */
        public GameServerInfo(int id, byte[] hexId) {
            this(id, hexId, null);
        }

        public String getName() {
            // This value can't be stored in a private variable because the ID can be changed by setId().
            return getInstance().getServerNameById(id);
        }

        /**
         * Sets the status.
         * @param status the new status
         */
        public void setStatus(int status) {
            if (LoginServer.getInstance().getStatus() == ServerStatus.STATUS_DOWN) {
                this.status = ServerStatus.STATUS_DOWN;
            } else if (LoginServer.getInstance().getStatus() == ServerStatus.STATUS_GM_ONLY) {
                this.status = ServerStatus.STATUS_GM_ONLY;
            } else {
                this.status = status;
            }
        }

        public String getStatusName() {
            switch (status) {
                case 0: {
                    return "Auto";
                }
                case 1: {
                    return "Good";
                }
                case 2: {
                    return "Normal";
                }
                case 3: {
                    return "Full";
                }
                case 4: {
                    return "Down";
                }
                case 5: {
                    return "GM Only";
                }
                default: {
                    return "Unknown";
                }
            }
        }

        /**
         * Gets the current player count.
         * @return the current player count
         */
        public int getCurrentPlayerCount() {
            if (gameServerThread == null) {
                return 0;
            }

            return gameServerThread.getPlayerCount();
        }

        public boolean canLogin(LoginClient client) {
            // DOWN status doesn't allow anyone to login.
            if (status == ServerStatus.STATUS_DOWN) {
                return false;
            }

            // GM_ONLY status or full server only allows superior access levels accounts to login.
            if ((status == ServerStatus.STATUS_GM_ONLY) || (getCurrentPlayerCount() >= getMaxPlayers())) {
                return client.getAccessLevel() > 0;
            }

            // Otherwise, any positive access level account can login.
            return client.getAccessLevel() >= 0;
        }

        /**
         * Gets the external host.
         * @return the external host
         */
        public String getExternalHost() {
            try {
                return getServerAddress(InetAddress.getByName("0.0.0.0"));
            } catch (Exception e) {
                // Ignore.
            }

            return null;
        }

        /**
         * Checks if is pvp.
         * @return true, if is pvp
         */
        public boolean isPvp() {
            return IS_PVP;
        }

        /**
         * Sets the down.
         */
        public void setDown() {
            setAuthed(false);
            setPort(0);
            setGameServerThread(null);
            setStatus(ServerStatus.STATUS_DOWN);
        }

        /**
         * Adds the server address.
         * @param subnet the subnet
         * @param addr the addr
         * @throws UnknownHostException the unknown host exception
         */
        public void addServerAddress(String subnet, String addr) throws UnknownHostException {
            addrs.add(new GameServerAddress(subnet, addr));
        }

        /**
         * Gets the server address.
         * @param addr the addr
         * @return the server address
         */
        @SuppressWarnings("unlikely-arg-type")
        public String getServerAddress(InetAddress addr) {
            for (GameServerAddress a : addrs) {
                if (a.equals(addr)) {
                    return a.getServerAddress();
                }
            }

            return null; // Should not happen.
        }

        /**
         * Gets the server addresses.
         * @return the server addresses
         */
        public String[] getServerAddresses() {
            final String[] result = new String[addrs.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = addrs.get(i).toString();
            }

            return result;
        }

        /**
         * Clear server addresses.
         */
        public void clearServerAddresses() {
            addrs.clear();
        }

        /**
         * The Class GameServerAddress.
         */
        private class GameServerAddress extends Subnet {
            @Getter
            private final String serverAddress;

            /**
             * Instantiates a new game server address.
             * @param subnet the subnet
             * @param address the address
             * @throws UnknownHostException the unknown host exception
             */
            public GameServerAddress(String subnet, String address) throws UnknownHostException {
                super(subnet);
                serverAddress = address;
            }

            @Override
            public String toString() {
                return serverAddress + "/" + super.toString();
            }
        }
    }

    /**
     * Gets the single instance of GameServerTable.
     * @return single instance of GameServerTable
     */
    public static GameServerTable getInstance() {
        return INSTANCE;
    }
}
