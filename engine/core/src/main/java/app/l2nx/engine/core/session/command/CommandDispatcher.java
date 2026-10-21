/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.command;

import app.l2nx.engine.core.session.Session;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.metrics.model.DropReason;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link #dispatch} runs in the session drain. {@link #submit} runs a service command on the caller's lane and hands
 * a session command to its addressed session, or to {@link SessionCommandHandler#handleWithoutSession}.
 */
@Slf4j
public final class CommandDispatcher {
    private final CommandRegistry registry;
    private final SessionDirectory sessions;
    private final NetworkMetrics metrics;

    public CommandDispatcher(CommandRegistry registry, SessionDirectory sessions, NetworkMetrics metrics) {
        this.registry = registry;
        this.sessions = sessions;
        this.metrics = metrics;
    }

    @SuppressWarnings("unchecked")
    public void dispatch(Session session, Command command) {
        switch (registry.handler(command.getClass())) {
            case SessionCommandHandler<?> handler ->
                ((SessionCommandHandler<Command>) handler).handle(session, command);
            case ServiceCommandHandler<?> handler ->
                throw new IllegalStateException(command.getClass().getName()
                        + " has a service handler and never reaches a session; submit it instead");
        }
    }

    @SuppressWarnings("unchecked")
    public void submit(Command command) {
        switch (registry.handler(command.getClass())) {
            case ServiceCommandHandler<?> handler -> ((ServiceCommandHandler<Command>) handler).handle(command);
            case SessionCommandHandler<?> handler -> offer((SessionCommandHandler<Command>) handler, command);
        }
    }

    private void offer(SessionCommandHandler<Command> handler, Command command) {
        if (!(command instanceof SessionAddressed addressed)) {
            throw new IllegalStateException(
                    command.getClass().getName() + " names no session, so only its own session can run it");
        }
        final SessionAddress address = addressed.address();
        if (address == null) {
            throw new IllegalArgumentException(command.getClass().getName() + " carries no session address");
        }
        sessions.find(address).ifPresentOrElse(session -> session.offerInternal(command), () -> {
            metrics.dropped(DropReason.NO_SESSION);
            log.debug("Dropped {}: {} has no session", command.getClass().getName(), address);
            handler.handleWithoutSession(command);
        });
    }
}
