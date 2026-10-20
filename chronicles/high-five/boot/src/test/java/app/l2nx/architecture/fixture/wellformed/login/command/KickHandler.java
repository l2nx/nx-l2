/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.architecture.fixture.wellformed.login.command;

import app.l2nx.architecture.fixture.wellformed.login.AccountPort;

class KickHandler {
    private final AccountPort port;

    KickHandler(AccountPort port) {
        this.port = port;
    }

    void handle(Kick kick) {
        port.left(kick.login());
    }
}
