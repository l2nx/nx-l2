/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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
package app.l2nx.login.protocol.c621;

import app.l2nx.gslink.v1.GsLinkProto.ListingStatus;
import app.l2nx.login.data.GameServerTable;
import app.l2nx.login.data.GameServerTable.GameServerInfo;
import app.l2nx.login.network.LoginClient;
import app.l2nx.protocol.c621.serverpackets.ServerList;
import app.l2nx.protocol.c621.serverpackets.ServerList.Characters;
import app.l2nx.protocol.c621.serverpackets.ServerList.Server;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ServerListMapper {

    private ServerListMapper() {}

    public static ServerList map(LoginClient client) {
        final List<Server> servers = new ArrayList<>(
                GameServerTable.getInstance().getRegisteredGameServers().size());
        for (GameServerInfo gsi :
                GameServerTable.getInstance().getRegisteredGameServers().values()) {
            if ((gsi.getStatus() == ListingStatus.LISTING_STATUS_GM_ONLY_VALUE) && (client.getAccessLevel() < 1)) {
                continue;
            }

            if (!gsi.isOpenTo(client.getVariant())) {
                continue;
            }

            servers.add(server(client, gsi));
        }

        // Snapshot: game servers answer from other threads, possibly after sendServerList stopped waiting.
        final Map<Integer, Integer> charsOnServers = Map.copyOf(client.getCharsOnServ());
        final Map<Integer, long[]> charsToDelete = Map.copyOf(client.getCharsWaitingDelOnServ());
        final long now = System.currentTimeMillis();
        final List<Characters> characters = new ArrayList<>(charsOnServers.size());
        for (Entry<Integer, Integer> entry : charsOnServers.entrySet()) {
            final long[] deleteTimes = charsToDelete.get(entry.getKey());
            final int[] pendingDeletion = deleteTimes == null ? new int[0] : new int[deleteTimes.length];
            for (int i = 0; i < pendingDeletion.length; i++) {
                pendingDeletion[i] = (int) ((deleteTimes[i] - now) / 1000);
            }
            characters.add(Characters.builder()
                    .serverId(entry.getKey())
                    .count(entry.getValue())
                    .pendingDeletion(pendingDeletion)
                    .build());
        }

        return ServerList.builder()
                .servers(servers)
                .lastServer(client.getLastServer())
                .characters(characters)
                .build();
    }

    private static Server server(LoginClient client, GameServerInfo gsi) {
        final byte[] ip = ipv4(client, gsi);

        final int status = (client.getAccessLevel() < 0)
                        || ((gsi.getStatus() == ListingStatus.LISTING_STATUS_GM_ONLY_VALUE)
                                && (client.getAccessLevel() <= 0))
                ? ListingStatus.LISTING_STATUS_DOWN_VALUE
                : gsi.getStatus();
        return Server.builder()
                .id(gsi.getId())
                .ip(ip)
                .port(gsi.getPort())
                .ageLimit(gsi.getAgeLimit())
                .pvp(gsi.isPvp())
                .currentPlayers(gsi.getCurrentPlayerCount())
                .maxPlayers(gsi.getMaxPlayers())
                .up(status != ListingStatus.LISTING_STATUS_DOWN_VALUE)
                .serverType(gsi.getServerType())
                .brackets(gsi.isShowingBrackets())
                .build();
    }

    /** The list has room for IPv4 only, so a host name resolves to its first IPv4 address. */
    private static byte[] ipv4(LoginClient client, GameServerInfo gsi) {
        try {
            final String host = gsi.getServerAddress(InetAddress.getByName(client.getIp()));
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address instanceof Inet4Address) {
                    return address.getAddress();
                }
            }
            log.warn("Game server {} address {} has no IPv4 address", gsi.getId(), host);
        } catch (UnknownHostException e) {
            log.warn("{}", e.getMessage());
        }
        return new byte[] {127, 0, 0, 1};
    }
}
