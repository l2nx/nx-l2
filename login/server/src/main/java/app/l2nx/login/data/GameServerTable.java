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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

/**
 * The Class GameServerTable loads the game server names and initialize the game server tables.
 * @author KenM
 */
@Component
public class GameServerTable {
    private static final Logger LOGGER = Logger.getLogger(GameServerTable.class.getName());

    private static final String SERVER_NAMES_FILE = "data/servername.xml";

    // Map for storing server names with their corresponding IDs.
    private static final Map<Integer, String> SERVER_NAMES = new HashMap<>();

    // Table for storing game server information.
    private static final Map<Integer, GameServerInfo> GAME_SERVER_TABLE = new HashMap<>();

    // Configuration for RSA key pairs.
    private static final int KEYS_SIZE = 10;
    private KeyPair[] _keyPairs;

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
        LOGGER.info("Loaded " + GAME_SERVER_TABLE.size() + " registered Game Servers.");

        initRSAKeys();
        LOGGER.info("Cached " + _keyPairs.length + " RSA keys for Game Server communication.");
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
            LOGGER.log(Level.WARNING, "Error parsing " + file.getAbsolutePath() + ".", e);
        }

        LOGGER.info("Loaded " + SERVER_NAMES.size() + " server names.");
    }

    /**
     * Inits the RSA keys.
     */
    private void initRSAKeys() {
        try {
            final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(new RSAKeyGenParameterSpec(512, RSAKeyGenParameterSpec.F4));
            _keyPairs = new KeyPair[KEYS_SIZE];
            for (int i = 0; i < KEYS_SIZE; i++) {
                _keyPairs[i] = keyGen.genKeyPair();
            }
        } catch (Exception e) {
            LOGGER.severe("Error loading RSA keys for Game Server communication!");
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
            LOGGER.severe("Error loading registered game servers!");
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
            LOGGER.severe("Error while saving gameserver!");
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
        return _keyPairs[ThreadLocalRandom.current().nextInt(_keyPairs.length)];
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
        private int _id;
        private final byte[] _hexId;
        private boolean _isAuthed;

        // Status information.
        private GameServerThread _gst;
        private int _status;

        // Network configuration.
        private final List<GameServerAddress> _addrs = new ArrayList<>(5);
        private int _port;

        // Configuration settings.
        private static final boolean IS_PVP = true;
        private int _serverType;
        private int _ageLimit;
        private boolean _isShowingBrackets;
        private int _maxPlayers;

        /**
         * Instantiates a new game server info.
         * @param id the id
         * @param hexId the hex id
         * @param gst the gst
         */
        public GameServerInfo(int id, byte[] hexId, GameServerThread gst) {
            _id = id;
            _hexId = hexId;
            _gst = gst;
            _status = ServerStatus.STATUS_DOWN;
        }

        /**
         * Instantiates a new game server info.
         * @param id the id
         * @param hexId the hex id
         */
        public GameServerInfo(int id, byte[] hexId) {
            this(id, hexId, null);
        }

        /**
         * Sets the id.
         * @param id the new id
         */
        public void setId(int id) {
            _id = id;
        }

        /**
         * Gets the id.
         * @return the id
         */
        public int getId() {
            return _id;
        }

        /**
         * Gets the hex id.
         * @return the hex id
         */
        public byte[] getHexId() {
            return _hexId;
        }

        public String getName() {
            // This value can't be stored in a private variable because the ID can be changed by setId().
            return getInstance().getServerNameById(_id);
        }

        /**
         * Sets the authed.
         * @param isAuthed the new authed
         */
        public void setAuthed(boolean isAuthed) {
            _isAuthed = isAuthed;
        }

        /**
         * Checks if is authed.
         * @return true, if is authed
         */
        public boolean isAuthed() {
            return _isAuthed;
        }

        /**
         * Sets the game server thread.
         * @param gst the new game server thread
         */
        public void setGameServerThread(GameServerThread gst) {
            _gst = gst;
        }

        /**
         * Gets the game server thread.
         * @return the game server thread
         */
        public GameServerThread getGameServerThread() {
            return _gst;
        }

        /**
         * Sets the status.
         * @param status the new status
         */
        public void setStatus(int status) {
            if (LoginServer.getInstance().getStatus() == ServerStatus.STATUS_DOWN) {
                _status = ServerStatus.STATUS_DOWN;
            } else if (LoginServer.getInstance().getStatus() == ServerStatus.STATUS_GM_ONLY) {
                _status = ServerStatus.STATUS_GM_ONLY;
            } else {
                _status = status;
            }
        }

        /**
         * Gets the status.
         * @return the status
         */
        public int getStatus() {
            return _status;
        }

        public String getStatusName() {
            switch (_status) {
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
            if (_gst == null) {
                return 0;
            }

            return _gst.getPlayerCount();
        }

        public boolean canLogin(LoginClient client) {
            // DOWN status doesn't allow anyone to login.
            if (_status == ServerStatus.STATUS_DOWN) {
                return false;
            }

            // GM_ONLY status or full server only allows superior access levels accounts to login.
            if ((_status == ServerStatus.STATUS_GM_ONLY) || (getCurrentPlayerCount() >= getMaxPlayers())) {
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
         * Gets the port.
         * @return the port
         */
        public int getPort() {
            return _port;
        }

        /**
         * Sets the port.
         * @param port the new port
         */
        public void setPort(int port) {
            _port = port;
        }

        /**
         * Sets the max players.
         * @param maxPlayers the new max players
         */
        public void setMaxPlayers(int maxPlayers) {
            _maxPlayers = maxPlayers;
        }

        /**
         * Gets the max players.
         * @return the max players
         */
        public int getMaxPlayers() {
            return _maxPlayers;
        }

        /**
         * Checks if is pvp.
         * @return true, if is pvp
         */
        public boolean isPvp() {
            return IS_PVP;
        }

        /**
         * Sets the age limit.
         * @param value the new age limit
         */
        public void setAgeLimit(int value) {
            _ageLimit = value;
        }

        /**
         * Gets the age limit.
         * @return the age limit
         */
        public int getAgeLimit() {
            return _ageLimit;
        }

        /**
         * Sets the server type.
         * @param value the new server type
         */
        public void setServerType(int value) {
            _serverType = value;
        }

        /**
         * Gets the server type.
         * @return the server type
         */
        public int getServerType() {
            return _serverType;
        }

        /**
         * Sets the showing brackets.
         * @param value the new showing brackets
         */
        public void setShowingBrackets(boolean value) {
            _isShowingBrackets = value;
        }

        /**
         * Checks if is showing brackets.
         * @return true, if is showing brackets
         */
        public boolean isShowingBrackets() {
            return _isShowingBrackets;
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
            _addrs.add(new GameServerAddress(subnet, addr));
        }

        /**
         * Gets the server address.
         * @param addr the addr
         * @return the server address
         */
        @SuppressWarnings("unlikely-arg-type")
        public String getServerAddress(InetAddress addr) {
            for (GameServerAddress a : _addrs) {
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
            final String[] result = new String[_addrs.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = _addrs.get(i).toString();
            }

            return result;
        }

        /**
         * Clear server addresses.
         */
        public void clearServerAddresses() {
            _addrs.clear();
        }

        /**
         * The Class GameServerAddress.
         */
        private class GameServerAddress extends Subnet {
            private final String _serverAddress;

            /**
             * Instantiates a new game server address.
             * @param subnet the subnet
             * @param address the address
             * @throws UnknownHostException the unknown host exception
             */
            public GameServerAddress(String subnet, String address) throws UnknownHostException {
                super(subnet);
                _serverAddress = address;
            }

            /**
             * Gets the server address.
             * @return the server address
             */
            public String getServerAddress() {
                return _serverAddress;
            }

            @Override
            public String toString() {
                return _serverAddress + "/" + super.toString();
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
