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
package app.l2nx.login.gameserver;

import app.l2nx.gslink.v1.GsLinkProto.ListingStatus;
import app.l2nx.login.LoginServer;
import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.gameserver.GameServerRepository.StoredGameServer;
import app.l2nx.login.gameserver.model.Subnet;
import app.l2nx.login.gslink.GameServerLink;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.protocol.LoginVariant;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * The Class GameServerTable loads the game server names and initialize the game server tables.
 * @author KenM
 */
@Component
@Slf4j
public class GameServerTable {

    private static final String SERVER_NAMES_FILE = "data/servername.xml";

    private static final Map<Integer, String> SERVER_NAMES = new HashMap<>();

    // Registered from gRPC threads while client threads iterate it for the server list.
    private static final Map<Integer, GameServerInfo> GAME_SERVER_TABLE = new ConcurrentHashMap<>();

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

    private void loadRegisteredGameServers() {
        // A reload starts from the database alone; the table is static.
        GAME_SERVER_TABLE.clear();
        try {
            for (StoredGameServer stored : gameServers.findAll()) {
                final GameServerInfo info = new GameServerInfo(stored.serverId(), stored.hexId());
                info.setChronicle(stored.chronicle());
                GAME_SERVER_TABLE.put(stored.serverId(), info);
            }
        } catch (Exception e) {
            log.error("Error loading registered game servers!", e);
        }
    }

    public Map<Integer, GameServerInfo> getRegisteredGameServers() {
        return GAME_SERVER_TABLE;
    }

    public GameServerInfo getRegisteredGameServerById(int id) {
        return GAME_SERVER_TABLE.get(id);
    }

    public boolean hasRegisteredGameServerOnId(int id) {
        return GAME_SERVER_TABLE.containsKey(id);
    }

    public boolean registerWithFirstAvailableId(GameServerInfo gsi) {
        for (Integer serverId : SERVER_NAMES.keySet()) {
            // Set before the put, so nobody sees the server under an id it does not carry; a lost race tries the next.
            gsi.setId(serverId);
            if (GAME_SERVER_TABLE.putIfAbsent(serverId, gsi) == null) {
                return true;
            }
        }

        return false;
    }

    public boolean register(int id, GameServerInfo gsi) {
        return GAME_SERVER_TABLE.putIfAbsent(id, gsi) == null;
    }

    public void registerServerOnDB(GameServerInfo gsi) {
        registerServerOnDB(gsi.getHexId(), gsi.getId());
    }

    public void registerServerOnDB(byte[] hexId, int id) {
        register(id, new GameServerInfo(id, hexId));
        try {
            gameServers.create(id, hexId);
        } catch (Exception e) {
            log.error("Error while saving gameserver!", e);
        }
    }

    /** Stores the chronicle a game server registered with: the server list shows the server before it next connects. */
    public void storeChronicle(int id, String chronicle) {
        try {
            gameServers.updateChronicle(id, chronicle);
        } catch (DataAccessException e) {
            log.error("Error while saving the chronicle of game server {}!", id, e);
        }
    }

    public String getServerNameById(int id) {
        return SERVER_NAMES.get(id);
    }

    public Map<Integer, String> getServerNames() {
        return SERVER_NAMES;
    }

    public static class GameServerInfo {
        @Getter
        @Setter
        private int id;

        @Getter
        private final byte[] hexId;

        @Getter
        @Setter
        private boolean authed;

        @Getter
        @Setter
        private volatile GameServerLink gameServerLink;

        /** The chronicle the game server last registered with, {@code null} if it never registered. */
        @Getter
        @Setter
        private volatile String chronicle;

        @Getter
        private int status;

        private final List<GameServerAddress> addrs = new ArrayList<>(5);

        @Getter
        @Setter
        private int port;

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

        public GameServerInfo(int id, byte[] hexId) {
            this.id = id;
            this.hexId = hexId;
            status = ListingStatus.LISTING_STATUS_DOWN_VALUE;
        }

        public String getName() {
            // This value can't be stored in a private variable because the ID can be changed by setId().
            return getInstance().getServerNameById(id);
        }

        public void setStatus(int status) {
            if (LoginServer.getInstance().getStatus() == ListingStatus.LISTING_STATUS_DOWN_VALUE) {
                this.status = ListingStatus.LISTING_STATUS_DOWN_VALUE;
            } else if (LoginServer.getInstance().getStatus() == ListingStatus.LISTING_STATUS_GM_ONLY_VALUE) {
                this.status = ListingStatus.LISTING_STATUS_GM_ONLY_VALUE;
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

        public int getCurrentPlayerCount() {
            final GameServerLink link = gameServerLink;
            return (link != null) ? link.getPlayerCount() : 0;
        }

        /** Whether clients of the variant can enter the server; a server that never registered has no chronicle. */
        public boolean isOpenTo(LoginVariant variant) {
            final String current = chronicle;
            return (current != null) && variant.chronicles().contains(current);
        }

        public boolean canLogin(LoginClient client) {
            if (status == ListingStatus.LISTING_STATUS_DOWN_VALUE) {
                return false;
            }

            if ((status == ListingStatus.LISTING_STATUS_GM_ONLY_VALUE)
                    || (getCurrentPlayerCount() >= getMaxPlayers())) {
                return client.getAccessLevel() > 0;
            }

            return client.getAccessLevel() >= 0;
        }

        public String getExternalHost() {
            try {
                return getServerAddress(InetAddress.getByName("0.0.0.0"));
            } catch (Exception e) {
                // Ignore.
            }

            return null;
        }

        public boolean isPvp() {
            return IS_PVP;
        }

        public void setDown() {
            setAuthed(false);
            setPort(0);
            setGameServerLink(null);
            status = ListingStatus.LISTING_STATUS_DOWN_VALUE;
        }

        public void addServerAddress(String subnet, String addr) throws UnknownHostException {
            addrs.add(new GameServerAddress(subnet, addr));
        }

        @SuppressWarnings("unlikely-arg-type")
        public String getServerAddress(InetAddress addr) {
            for (GameServerAddress a : addrs) {
                if (a.equals(addr)) {
                    return a.getServerAddress();
                }
            }

            return null;
        }

        public String[] getServerAddresses() {
            final String[] result = new String[addrs.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = addrs.get(i).toString();
            }

            return result;
        }

        public void clearServerAddresses() {
            addrs.clear();
        }

        private class GameServerAddress extends Subnet {
            @Getter
            private final String serverAddress;

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

    public static GameServerTable getInstance() {
        return INSTANCE;
    }
}
