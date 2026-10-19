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
package app.l2nx.highfive.network.loginserverpackets.receive;

import app.l2nx.network.packet.SimpleReadablePacket;
import lombok.Getter;

public class LoginServerFail extends SimpleReadablePacket {
    private static final String[] REASONS = {
        "None",
        "Reason: ip banned",
        "Reason: ip reserved",
        "Reason: wrong hexid",
        "Reason: id reserved",
        "Reason: no free ID",
        "Not authed",
        "Reason: already logged in"
    };

    @Getter
    private final int reason;

    public LoginServerFail(byte[] decrypt) {
        super(decrypt);
        readByte(); // Packet id, it is already processed.

        reason = readByte();
    }

    public String getReasonString() {
        return REASONS[reason];
    }
}
