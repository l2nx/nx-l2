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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.network.packet.SimpleWritablePacket;

/**
 * Issues a temporary ban for an account and/or IP at the login server.<br>
 * The ban expires at {@code now + minutes * 60000}.
 * <ul>
 * <li>Opcode: 0x0A.</li>
 * <li>Payload: account (String), ip (String), expireMillis (long), reserved (byte).</li>
 * </ul>
 * @author BazookaRpm
 */
public class TempBan extends SimpleWritablePacket {
    // Opcode.
    private static final int OPCODE = 0x0A;
    private static final long MINUTE_MILLIS = 60000L;

    // Data.
    private final String accountName;
    private final String ip;
    private final long minutes;

    /**
     * @param accountName account name
     * @param ip ip address
     * @param time minutes duration
     */
    public TempBan(String accountName, String ip, long time) {
        this.accountName = (accountName != null) ? accountName : "";
        this.ip = (ip != null) ? ip : "";
        minutes = (time > 0L) ? time : 0L;
    }

    /**
     * Serializes opcode, identifiers and expiration time.
     */
    @Override
    public void write() {
        writeByte(OPCODE);
        writeString(accountName);
        writeString(ip);
        writeLong(ClockBridge.wallMillis() + (minutes * MINUTE_MILLIS));
        writeByte(0x00);
    }
}
