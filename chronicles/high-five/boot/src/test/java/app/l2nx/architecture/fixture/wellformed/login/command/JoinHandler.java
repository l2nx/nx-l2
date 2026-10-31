/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.architecture.fixture.wellformed.login.command;

import app.l2nx.architecture.fixture.wellformed.login.AccountPort;
import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.command.ContextCommandHandler;
import app.l2nx.engine.core.session.command.ContextResolver;

class JoinHandler implements ContextCommandHandler<String, Join> {
    private static final ContextResolver<String> LOGIN = new ContextResolver<>() {
        @Override
        public String name() {
            return "LOGIN";
        }

        @Override
        public String resolve(Session session) {
            return null;
        }
    };

    private final AccountPort port;

    JoinHandler(AccountPort port) {
        this.port = port;
    }

    @Override
    public ContextResolver<String> context() {
        return LOGIN;
    }

    @Override
    public void handle(String login, Join join) {
        port.left(join.login());
    }
}
