/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.command;

import app.l2nx.engine.core.session.Session;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.metrics.model.DropReason;
import lombok.extern.slf4j.Slf4j;

/** {@link #resolve} runs in the session drain; {@link #submit} runs on the caller's lane. */
@Slf4j
public final class CommandDispatcher {
    public sealed interface Dispatch {
        record Ready(Runnable handling) implements Dispatch {}

        record Rejected(String resolver) implements Dispatch {}
    }

    private final CommandRegistry registry;
    private final SessionDirectory sessions;
    private final NetworkMetrics metrics;

    public CommandDispatcher(CommandRegistry registry, SessionDirectory sessions, NetworkMetrics metrics) {
        this.registry = registry;
        this.sessions = sessions;
        this.metrics = metrics;
    }

    /** Runs no handler: the session runs a ready command under its own timing. */
    @SuppressWarnings("unchecked")
    public Dispatch resolve(Session session, Command command) {
        return switch (registry.handler(command.getClass())) {
            case SessionCommandHandler<?> handler -> {
                final SessionCommandHandler<Command> typed = (SessionCommandHandler<Command>) handler;
                yield new Dispatch.Ready(() -> typed.handle(session, command));
            }
            case ContextCommandHandler<?, ?> handler ->
                resolveInContext((ContextCommandHandler<Object, Command>) handler, session, command);
            case ServiceCommandHandler<?> handler ->
                throw new IllegalStateException(command.getClass().getName()
                        + " has a service handler and never reaches a session; submit it instead");
        };
    }

    @SuppressWarnings("unchecked")
    public void submit(Command command) {
        switch (registry.handler(command.getClass())) {
            case ServiceCommandHandler<?> handler -> ((ServiceCommandHandler<Command>) handler).handle(command);
            case SessionCommandHandler<?> handler -> offer((SessionCommandHandler<Command>) handler, command);
            case ContextCommandHandler<?, ?> handler ->
                throw new IllegalStateException(command.getClass().getName()
                        + " is a client command with a game context, so only its own session runs it");
        }
    }

    private static <X> Dispatch resolveInContext(
            ContextCommandHandler<X, Command> handler, Session session, Command command) {
        final ContextResolver<X> resolver = handler.context();
        final X context = resolver.resolve(session);
        if (context == null) {
            return new Dispatch.Rejected(resolver.name());
        }
        return new Dispatch.Ready(() -> handler.handle(context, command));
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
