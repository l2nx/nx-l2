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

import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.gameserver.GameServerRepository.StoredGameServer;
import app.l2nx.login.gameserver.model.GameServerInfo;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The Class GameServerTable loads the game server names and initialize the game server tables.
 * @author KenM
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GameServerTable {

    private static final String SERVER_NAMES_FILE = "data/servername.xml";

    private final GameServerRepository repository;
    private final LoginProperties properties;

    // Sorted: a server that may take another id gets the lowest free one.
    private volatile Map<Integer, String> serverNames = Map.of();

    // Registered from gRPC threads while client threads iterate it for the server list.
    private final Map<Integer, GameServerInfo> servers = new ConcurrentHashMap<>();

    public void load() {
        loadServerNames();

        loadRegisteredGameServers();
        log.info("Loaded {} registered Game Servers.", servers.size());
    }

    private void loadServerNames() {
        final Map<Integer, String> names = new TreeMap<>();
        final File file = new File(properties.datapackRoot(), SERVER_NAMES_FILE);
        try (InputStream input = Files.newInputStream(file.toPath())) {
            final XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            final XMLStreamReader reader = factory.createXMLStreamReader(input);
            try {
                while (reader.hasNext()) {
                    if ((reader.next() == XMLStreamConstants.START_ELEMENT) && "server".equals(reader.getLocalName())) {
                        names.put(
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

        serverNames = Collections.unmodifiableMap(names);
        log.info("Loaded {} server names.", names.size());
    }

    private void loadRegisteredGameServers() {
        servers.clear();
        try {
            for (StoredGameServer stored : repository.findAll()) {
                final GameServerInfo info = new GameServerInfo(stored.serverId(), stored.hexId());
                info.setChronicle(stored.chronicle());
                servers.put(stored.serverId(), info);
            }
        } catch (Exception e) {
            log.error("Error loading registered game servers!", e);
        }
    }

    public Map<Integer, GameServerInfo> getRegisteredGameServers() {
        return servers;
    }

    public GameServerInfo getRegisteredGameServerById(int id) {
        return servers.get(id);
    }

    public boolean registerWithFirstAvailableId(GameServerInfo gsi) {
        for (Integer serverId : serverNames.keySet()) {
            // Set before the put, so nobody sees the server under an id it does not carry; a lost race tries the next.
            gsi.setId(serverId);
            if (servers.putIfAbsent(serverId, gsi) == null) {
                return true;
            }
        }

        return false;
    }

    public boolean register(int id, GameServerInfo gsi) {
        return servers.putIfAbsent(id, gsi) == null;
    }

    public String getServerNameById(int id) {
        return serverNames.get(id);
    }
}
