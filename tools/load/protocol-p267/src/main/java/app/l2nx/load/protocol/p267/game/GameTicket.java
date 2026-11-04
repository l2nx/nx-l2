/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267.game;

import java.net.InetSocketAddress;
import lombok.Builder;

/**
 * @param loginOk1 0 when the login server showed no licence and sent no {@code LoginOk}
 * @param loginOk2 0 when the login server showed no licence and sent no {@code LoginOk}
 */
@Builder(toBuilder = true)
public record GameTicket(
        String account, InetSocketAddress gameServer, int loginOk1, int loginOk2, int playOk1, int playOk2) {
    /** Never prints the session key. */
    @Override
    public String toString() {
        return "GameTicket[account=" + account + ", gameServer=" + gameServer + "]";
    }
}
