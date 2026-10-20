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
import java.util.List;
import java.util.UUID;

/**
 * Announces to the login server the accounts currently connected on this game server.<br>
 * Supports a single account or a list of accounts.
 * <ul>
 * <li>Opcode: 0x02.</li>
 * <li>Payload: count (short) + N * account id (2 longs: msb, lsb).</li>
 * <li>Depends on {@code SimpleWritablePacket.write*} methods.</li>
 * </ul>
 * @author BazookaRpm
 */
public class PlayerInGame extends SimpleWritablePacket {
    // Opcode.
    private static final int OPCODE = 0x02;

    // Data.
    private final List<UUID> accountIds;

    /**
     * Single-account constructor.
     * @param accountId the account
     */
    public PlayerInGame(UUID accountId) {
        this(List.of(accountId));
    }

    /**
     * Multi-account constructor.
     * @param accountIds the accounts
     */
    public PlayerInGame(List<UUID> accountIds) {
        this.accountIds = List.copyOf(accountIds);
    }

    /**
     * Serializes opcode, count and all accounts.
     */
    @Override
    public void write() {
        writeByte(OPCODE);
        writeShort(accountIds.size());
        for (UUID accountId : accountIds) {
            writeLong(accountId.getMostSignificantBits());
            writeLong(accountId.getLeastSignificantBits());
        }
    }
}
