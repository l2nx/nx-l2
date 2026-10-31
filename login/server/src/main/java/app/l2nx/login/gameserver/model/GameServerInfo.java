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
package app.l2nx.login.gameserver.model;

import app.l2nx.login.gslink.GameServerLink;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.protocol.LoginVariant;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

public class GameServerInfo {
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

    // Listing fields: the GS-link's gRPC thread publishes them, the login lanes read them without the monitor.
    @Getter
    @Setter
    private volatile ListingStatus status;

    private final List<GameServerAddress> addrs = new ArrayList<>(5);

    @Getter
    @Setter
    private int port;

    private static final boolean IS_PVP = true;

    @Getter
    @Setter
    private volatile int serverType;

    @Getter
    @Setter
    private volatile int ageLimit;

    @Getter
    @Setter
    private volatile boolean showingBrackets;

    @Getter
    @Setter
    private volatile int maxPlayers;

    public GameServerInfo(int id, byte[] hexId) {
        this.id = id;
        this.hexId = hexId;
        status = ListingStatus.DOWN;
    }

    public int getCurrentPlayerCount() {
        final GameServerLink link = gameServerLink;
        return (link != null) ? link.getPlayerCount() : 0;
    }

    public boolean isOpenTo(LoginVariant variant) {
        final String current = chronicle;
        return (current != null) && variant.chronicles().contains(current);
    }

    public boolean canLogin(LoginClient client) {
        final ListingStatus current = status;
        if (current == ListingStatus.DOWN) {
            return false;
        }

        if ((current == ListingStatus.GM_ONLY) || (getCurrentPlayerCount() >= getMaxPlayers())) {
            return client.getAccessLevel() > 0;
        }

        return client.getAccessLevel() >= 0;
    }

    public boolean isPvp() {
        return IS_PVP;
    }

    public void setDown() {
        setAuthed(false);
        setPort(0);
        setGameServerLink(null);
        status = ListingStatus.DOWN;
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
