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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.login.protocol.c621.clientpackets;

import app.l2nx.login.enums.LoginFailReason;
import app.l2nx.login.protocol.c621.ConnectionState;
import app.l2nx.login.protocol.c621.serverpackets.GGAuth;
import lombok.extern.slf4j.Slf4j;

/**
 * @author BazookaRpm
 */
@Slf4j
public class AuthGameGuard extends LoginClientPacket {

    private static final int GAME_GUARD_PAYLOAD_SIZE = 20;
    private static final int GAME_GUARD_RESERVED_DWORD_COUNT = 4;

    private int receivedSessionId;

    @Override
    protected boolean readImpl() {
        final int remainingBytes = remaining();
        if (remainingBytes < GAME_GUARD_PAYLOAD_SIZE) {
            if (log.isDebugEnabled()) {
                log.debug("Invalid payload length {} (expected {}).", remainingBytes, GAME_GUARD_PAYLOAD_SIZE);
            }
            return false;
        }

        receivedSessionId = readInt();

        for (int i = 0; i < GAME_GUARD_RESERVED_DWORD_COUNT; i++) {
            readInt();
        }

        return true;
    }

    @Override
    public void run() {
        final int clientSessionId = getClient().getSessionId();
        if (receivedSessionId != clientSessionId) {
            if (log.isDebugEnabled()) {
                log.debug("Session mismatch. Received={}, expected={}.", receivedSessionId, clientSessionId);
            }

            getClient().close(LoginFailReason.REASON_ACCESS_FAILED);
            return;
        }

        getClient().setConnectionState(ConnectionState.AUTHED_GG);
        getClient().handshakeDone();
        getClient().sendPacket(new GGAuth(clientSessionId));
    }
}
