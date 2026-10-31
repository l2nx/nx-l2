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
package app.l2nx.login.protocol.c621.handler;

import app.l2nx.login.network.LoginClient;
import app.l2nx.login.protocol.c621.ConnectionState;
import app.l2nx.protocol.login.c621.clientpackets.AuthGameGuard;
import app.l2nx.protocol.login.c621.serverpackets.GGAuth;
import app.l2nx.protocol.login.c621.serverpackets.reason.LoginFailReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author BazookaRpm
 */
@Component
@Slf4j
public final class AuthGameGuardHandler {
    public void handle(LoginClient client, AuthGameGuard packet) {
        final int clientSessionId = client.getSessionId();
        if (packet.sessionId() != clientSessionId) {
            if (log.isDebugEnabled()) {
                log.debug("Session mismatch. Received={}, expected={}.", packet.sessionId(), clientSessionId);
            }

            client.close(LoginFailReason.REASON_ACCESS_FAILED);
            return;
        }

        client.setConnectionState(ConnectionState.AUTHED_GG);
        client.handshakeDone();
        client.sendPacket(new GGAuth(clientSessionId));
    }
}
