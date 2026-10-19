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
package app.l2nx.highfive.network.loginserverpackets.send;

import app.l2nx.network.packet.SimpleWritablePacket;
import java.util.Collections;
import java.util.List;

/**
 * Game server authentication request to the login server.<br>
 * Signature compatible with existing callsites.
 * <ul>
 * <li>Opcode: 0x01.</li>
 * <li>Payload: id (int), acceptAlt (byte), reserveHost (byte), port (short), maxPlayers (int), hexIdLen (int), hexId (bytes), pairs (int), subnets/hosts (String pairs).</li>
 * </ul>
 * @author BazookaRpm
 */
public class AuthRequest extends SimpleWritablePacket {
    // Opcode.
    private static final int OPCODE = 0x01;

    // Data.
    private final int id;
    private final boolean acceptAlternate;
    private final byte[] hexId;
    private final int port;
    private final boolean reserveHost;
    private final int maxPlayers;
    private final List<String> subnets;
    private final List<String> hosts;

    /**
     * Auth constructor matching (id, acceptAlternate, hexid, port, reserveHost, maxplayer, subnets, hosts).
     * @param id server id
     * @param acceptAlternate allow alternate ID
     * @param hexid server hexadecimal id
     * @param port bind port
     * @param reserveHost reserve host flag
     * @param maxplayer max players
     * @param subnets subnet list
     * @param hosts host list
     */
    public AuthRequest(
            int id,
            boolean acceptAlternate,
            byte[] hexid,
            int port,
            boolean reserveHost,
            int maxplayer,
            List<String> subnets,
            List<String> hosts) {
        this.id = id;
        this.acceptAlternate = acceptAlternate;
        hexId = (hexid != null) ? hexid : new byte[0];
        this.port = port;
        this.reserveHost = reserveHost;
        maxPlayers = maxplayer;
        this.subnets = (subnets != null) ? subnets : Collections.emptyList();
        this.hosts = (hosts != null) ? hosts : Collections.emptyList();
    }

    /**
     * Serializes the full authentication payload including hexId and subnet/host pairs.
     */
    @Override
    public void write() {
        final int pairs = Math.min(subnets.size(), hosts.size());

        writeByte(OPCODE);
        writeByte(id);
        writeByte(acceptAlternate ? 0x01 : 0x00);
        writeByte(reserveHost ? 0x01 : 0x00);
        writeShort(port);
        writeInt(maxPlayers);
        writeInt(hexId.length);
        writeBytes(hexId);
        writeInt(pairs);
        for (int i = 0; i < pairs; i++) {
            writeString((subnets.get(i) != null) ? subnets.get(i) : "");
            writeString((hosts.get(i) != null) ? hosts.get(i) : "");
        }
    }
}
