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
package app.l2nx.highfive.network.loginserverpackets.send;

import app.l2nx.network.packet.SimpleWritablePacket;
import java.util.Objects;
import java.util.UUID;

/**
 * Sends the player's PC IP and up to four routing hops (tracert) to the login server.
 * <ul>
 * <li>Opcode: 0x07.</li>
 * <li>Payload: account id (2 longs: msb, lsb), pcIp, hop1..hop4 (Strings).</li>
 * </ul>
 * @author BazookaRpm
 */
public class PlayerTracert extends SimpleWritablePacket {
    // Opcode.
    private static final int OPCODE = 0x07;

    // Data.
    private final UUID accountId;
    private final String pcIp;
    private final String hop1;
    private final String hop2;
    private final String hop3;
    private final String hop4;

    /**
     * @param accountId the account
     * @param pcIp player IP
     * @param hop1 first hop
     * @param hop2 second hop
     * @param hop3 third hop
     * @param hop4 fourth hop
     */
    public PlayerTracert(UUID accountId, String pcIp, String hop1, String hop2, String hop3, String hop4) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.pcIp = pcIp;
        this.hop1 = hop1;
        this.hop2 = hop2;
        this.hop3 = hop3;
        this.hop4 = hop4;
    }

    /**
     * Serializes opcode, account, pcIp and four hops.
     */
    @Override
    public void write() {
        writeByte(OPCODE);
        writeLong(accountId.getMostSignificantBits());
        writeLong(accountId.getLeastSignificantBits());
        writeString(pcIp);
        writeString(hop1);
        writeString(hop2);
        writeString(hop3);
        writeString(hop4);
    }
}
