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
package app.l2nx.login.network.serverpackets;

import app.l2nx.login.data.GameServerTable;
import app.l2nx.login.data.GameServerTable.GameServerInfo;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.network.gameserverpackets.receive.ServerStatus;
import app.l2nx.network.buffer.WriteBuffer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;

/**
 * ServerList
 *
 * <pre>
 * Format: cc [cddcchhcdc]
 *
 * c: server list size (number of servers)
 * c: ?
 * [ (repeat for each servers)
 * c: server id (ignored by client?)
 * d: server ip
 * d: server port
 * c: age limit (used by client?)
 * c: pvp or not (used by client?)
 * h: current number of players
 * h: max number of players
 * c: 0 if server is down
 * d: 2nd bit: clock
 *    3rd bit: won't display server name
 *    4th bit: test server (used by client?)
 * c: 0 if you don't want to display brackets in front of sever name
 * ]
 * </pre>
 *
 * Server will be considered as Good when the number of online players<br>
 * is less than half the maximum. as Normal between half and 4/5<br>
 * and Full when there's more than 4/5 of the maximum number of players.
 */
@Slf4j
public class ServerList extends LoginServerPacket {

    private final List<ServerData> servers;
    private final int lastServer;
    private Map<Integer, Integer> charsOnServers;
    private Map<Integer, long[]> charsToDelete;

    class ServerData {
        protected byte[] ip;
        protected int port;
        protected int ageLimit;
        protected boolean pvp;
        protected int currentPlayers;
        protected int maxPlayers;
        protected boolean brackets;
        protected boolean clock;
        protected int status;
        protected int serverId;
        protected int serverType;

        ServerData(LoginClient client, GameServerInfo gsi) {
            try {
                ip = InetAddress.getByName(gsi.getServerAddress(InetAddress.getByName(client.getIp())))
                        .getAddress();
            } catch (UnknownHostException e) {
                log.warn("{}", e.getMessage());
                ip = new byte[4];
                ip[0] = 127;
                ip[1] = 0;
                ip[2] = 0;
                ip[3] = 1;
            }

            port = gsi.getPort();
            pvp = gsi.isPvp();
            serverType = gsi.getServerType();
            currentPlayers = gsi.getCurrentPlayerCount();
            maxPlayers = gsi.getMaxPlayers();
            ageLimit = 0;
            brackets = gsi.isShowingBrackets();

            // If server GM-only - show status only to GMs.
            status = (client.getAccessLevel() < 0)
                            || ((gsi.getStatus() == ServerStatus.STATUS_GM_ONLY) && (client.getAccessLevel() <= 0))
                    ? ServerStatus.STATUS_DOWN
                    : gsi.getStatus();
            serverId = gsi.getId();
        }
    }

    public ServerList(LoginClient client) {
        servers = new ArrayList<>(
                GameServerTable.getInstance().getRegisteredGameServers().size());
        lastServer = client.getLastServer();
        for (GameServerInfo gsi :
                GameServerTable.getInstance().getRegisteredGameServers().values()) {
            // Hide GM-only servers from non-GM accounts.
            if ((gsi.getStatus() == ServerStatus.STATUS_GM_ONLY) && (client.getAccessLevel() < 1)) {
                continue;
            }

            servers.add(new ServerData(client, gsi));
        }

        // Wait 500ms to reply with character list.
        int i = 0;
        while (((charsOnServers == null) || (charsToDelete == null)) && (i++ < 10)) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }

            charsOnServers = client.getCharsOnServ();
            charsToDelete = client.getCharsWaitingDelOnServ();
        }
    }

    @Override
    protected void writeImpl(LoginClient client, WriteBuffer buffer) {
        buffer.writeByte(0x04);
        buffer.writeByte(servers.size());
        buffer.writeByte(lastServer);
        for (ServerData server : servers) {
            buffer.writeByte(server.serverId); // Server id.

            buffer.writeByte(server.ip[0] & 0xff);
            buffer.writeByte(server.ip[1] & 0xff);
            buffer.writeByte(server.ip[2] & 0xff);
            buffer.writeByte(server.ip[3] & 0xff);

            buffer.writeInt(server.port);
            buffer.writeByte(server.ageLimit); // Age Limit 0, 15, 18.
            buffer.writeByte(server.pvp ? 1 : 0);
            buffer.writeShort(server.currentPlayers);
            buffer.writeShort(server.maxPlayers);
            buffer.writeByte(server.status == ServerStatus.STATUS_DOWN ? 0 : 1);
            buffer.writeInt(
                    server.serverType); // 1: Normal, 2: Relax, 4: Public Test, 8: No Label, 16: Character Creation
            // Restricted, 32: Event, 64: Free.
            buffer.writeByte(server.brackets ? 1 : 0);
        }

        buffer.writeShort(0x00); // Unknown.
        if (charsOnServers != null) {
            buffer.writeByte(charsOnServers.size());
            for (Entry<Integer, Integer> entry : charsOnServers.entrySet()) {
                final int servId = entry.getKey();
                buffer.writeByte(servId);
                buffer.writeByte(entry.getValue());
                if ((charsToDelete == null) || !charsToDelete.containsKey(servId)) {
                    buffer.writeByte(0);
                } else {
                    buffer.writeByte(charsToDelete.get(servId).length);
                    for (long deleteTime : charsToDelete.get(servId)) {
                        buffer.writeInt((int) ((deleteTime - System.currentTimeMillis()) / 1000));
                    }
                }
            }
        } else {
            buffer.writeByte(0);
        }
    }
}
