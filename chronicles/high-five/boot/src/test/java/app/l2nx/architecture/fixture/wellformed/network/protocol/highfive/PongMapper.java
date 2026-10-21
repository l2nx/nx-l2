/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.architecture.fixture.wellformed.network.protocol.highfive;

import app.l2nx.architecture.fixture.wellformed.network.protocol.highfive.serverpackets.Pong;

public class PongMapper {
    Pong map() {
        return new Pong();
    }
}
