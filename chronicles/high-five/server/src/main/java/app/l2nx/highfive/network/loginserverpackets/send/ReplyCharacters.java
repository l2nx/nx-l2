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
 * Reports character count and pending delete timers for an account.<br>
 * Each pending delete time is serialized as epoch milliseconds.
 * <ul>
 * <li>Opcode: 0x08.</li>
 * <li>Payload: account (String), chars (byte), pendingCount (byte), times (long[]).</li>
 * </ul>
 * @author BazookaRpm
 */
public class ReplyCharacters extends SimpleWritablePacket {
    // Opcode.
    private static final int OPCODE = 0x08;

    // Data.
    private final String account;
    private final int chars;
    private final List<Long> timeToDelete;

    /**
     * @param account account name
     * @param chars number of characters
     * @param timeToDel pending delete times (epoch ms)
     */
    public ReplyCharacters(String account, int chars, List<Long> timeToDel) {
        this.account = (account != null) ? account : "";
        this.chars = chars;
        timeToDelete = (timeToDel != null) ? timeToDel : Collections.emptyList();
    }

    /**
     * Serializes opcode, account, counts and delete timers.
     */
    @Override
    public void write() {
        writeByte(OPCODE);
        writeString(account);
        writeByte(chars);
        writeByte(timeToDelete.size());
        for (Long t : timeToDelete) {
            writeLong((t != null) ? t.longValue() : 0L);
        }
    }
}
