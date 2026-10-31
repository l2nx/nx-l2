/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.architecture.fixture.wellformed.network.protocol.p267;

import app.l2nx.architecture.fixture.wellformed.wire.p267.serverpackets.Ping;

public class PingMapper {
    Ping map(int id) {
        return new Ping(id);
    }
}
