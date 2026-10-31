/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.architecture.fixture.wellformed.scripts.handlers.chat.channels;

import app.l2nx.architecture.fixture.wellformed.login.command.Kick;

public class Shout {
    Kick kick(String login) {
        return new Kick(login);
    }
}
