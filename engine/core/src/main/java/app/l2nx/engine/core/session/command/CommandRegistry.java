/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.command;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.ResolvableType;

public final class CommandRegistry {
    private final Map<Class<? extends Command>, CommandHandler<?>> handlers;

    public CommandRegistry(List<? extends CommandHandler<?>> handlers) {
        final Map<Class<? extends Command>, CommandHandler<?>> byType = new HashMap<>();
        for (CommandHandler<?> handler : handlers) {
            final Class<? extends Command> type = commandType(handler);
            if (handler instanceof ContextCommandHandler<?, ?> contextual) {
                checkContext(contextual, type);
            }
            final CommandHandler<?> previous = byType.putIfAbsent(type, handler);
            if (previous != null) {
                throw new IllegalStateException("Two handlers for " + type.getName() + ": "
                        + previous.getClass().getName() + " and "
                        + handler.getClass().getName());
            }
        }
        this.handlers = Map.copyOf(byType);
    }

    public Set<Class<? extends Command>> commandTypes() {
        return handlers.keySet();
    }

    public CommandHandler<?> handler(Class<? extends Command> type) {
        final CommandHandler<?> handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalStateException("No handler for " + type.getName());
        }
        return handler;
    }

    private static void checkContext(ContextCommandHandler<?, ?> handler, Class<? extends Command> type) {
        if (SessionAddressed.class.isAssignableFrom(type)) {
            throw new IllegalStateException(handler.getClass().getName() + " takes a game context, but "
                    + type.getName() + " is addressed by the server, which carries none");
        }
        if (handler.context() == null) {
            throw new IllegalStateException(
                    handler.getClass().getName() + " takes a game context but names no resolver");
        }
    }

    // Dispatch looks a command up by its exact class, so a handler of an interface or abstract type would never run.
    private static Class<? extends Command> commandType(CommandHandler<?> handler) {
        final Class<?> type = ResolvableType.forClass(handler.getClass())
                .as(CommandHandler.class)
                .resolveGeneric(0);
        if (type == null) {
            throw new IllegalStateException(
                    "Cannot resolve the command type of " + handler.getClass().getName()
                            + ": a handler is a class that names its command type as the type argument");
        }
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalStateException("The command type " + type.getName() + " of "
                    + handler.getClass().getName() + " is not a concrete class");
        }
        return type.asSubclass(Command.class);
    }
}
