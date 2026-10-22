/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267;

import java.net.InetSocketAddress;
import java.time.Duration;
import lombok.Builder;

/** @param serverId the game server a load bot picks from the server list; one byte on the wire */
@Builder(toBuilder = true)
public record P267Settings(InetSocketAddress login, int serverId, Duration connectTimeout) {
    private static final int MAX_SERVER_ID = 0xFF;

    public P267Settings {
        if (login == null) {
            throw new IllegalArgumentException("The login server address is required");
        }
        if (serverId <= 0 || serverId > MAX_SERVER_ID) {
            throw new IllegalArgumentException(
                    "The game server id must be within [1, " + MAX_SERVER_ID + "]: " + serverId);
        }
        if (connectTimeout == null || !connectTimeout.isPositive()) {
            throw new IllegalArgumentException("The connect timeout must be positive: " + connectTimeout);
        }
    }
}
