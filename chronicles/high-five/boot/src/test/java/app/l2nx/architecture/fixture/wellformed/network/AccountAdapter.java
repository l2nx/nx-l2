/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.architecture.fixture.wellformed.network;

import app.l2nx.architecture.fixture.wellformed.login.AccountPort;
import app.l2nx.architecture.fixture.wellformed.login.command.Kick;

public class AccountAdapter implements AccountPort {
    @Override
    public void left(String login) {}

    Kick receive(String login) {
        return new Kick(login);
    }
}
