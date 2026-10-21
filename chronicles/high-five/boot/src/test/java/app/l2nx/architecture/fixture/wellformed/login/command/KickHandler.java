/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.architecture.fixture.wellformed.login.command;

import app.l2nx.architecture.fixture.wellformed.login.AccountPort;
import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.command.SessionCommandHandler;

class KickHandler implements SessionCommandHandler<Kick> {
    private final AccountPort port;

    KickHandler(AccountPort port) {
        this.port = port;
    }

    @Override
    public void handle(Session session, Kick kick) {
        port.left(kick.login());
    }
}
