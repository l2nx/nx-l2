/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.architecture.fixture.broken.login;

import app.l2nx.architecture.fixture.broken.wire.p267.serverpackets.Ping;

public class Greeting {
    Ping greet() {
        return new Ping(1);
    }
}
