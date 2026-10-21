/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.architecture.fixture.broken.login.command;

import app.l2nx.engine.core.session.command.ServiceCommandHandler;

public class BanHandler implements ServiceCommandHandler<Kick> {
    public BanHandler() {}

    @Override
    public void handle(Kick command) {}
}
