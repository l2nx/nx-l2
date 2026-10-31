/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.session.Session;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CommandRegistryTest {
    record Ping(int value) implements Command {}

    record Unhandled() implements Command {}

    record Enter(int value) implements Command {}

    record Answer(SessionAddress address) implements SessionAddressed {}

    private static final ContextResolver<String> HERO = new ContextResolver<>() {
        @Override
        public String name() {
            return "CHARACTER";
        }

        @Override
        public String resolve(Session session) {
            return "hero";
        }
    };

    @Nested
    class Handler {
        @Test
        void handler_shouldResolveTheCommandTypeFromTheTypeArgument() {
            final SessionCommandHandler<Ping> pings = sessionPings();

            final CommandRegistry actual = new CommandRegistry(List.of(pings));

            assertThat(actual.commandTypes()).containsExactly(Ping.class);
            assertThat(actual.handler(Ping.class)).isSameAs(pings);
        }

        @Test
        void handler_shouldFail_whenCommandHasNoHandler() {
            final CommandRegistry registry = new CommandRegistry(List.of());

            assertThatThrownBy(() -> registry.handler(Unhandled.class))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(Unhandled.class.getName());
        }

        @Test
        void handler_shouldResolveTheCommandTypeOfAHandlerThatTakesAContext() {
            final ContextCommandHandler<String, Enter> enters = enters();

            final CommandRegistry actual = new CommandRegistry(List.of(enters));

            assertThat(actual.commandTypes()).containsExactly(Enter.class);
            assertThat(actual.handler(Enter.class)).isSameAs(enters);
        }
    }

    @Nested
    class Constructor {
        @Test
        void constructor_shouldRefuseTwoHandlersOfOneCommand() {
            final List<CommandHandler<?>> handlers = List.of(sessionPings(), new ServiceCommandHandler<Ping>() {
                @Override
                public void handle(Ping command) {}
            });

            assertThatThrownBy(() -> new CommandRegistry(handlers))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(Ping.class.getName());
        }

        @Test
        void constructor_shouldRefuseAHandlerWhoseCommandTypeIsUnresolvable() {
            final SessionCommandHandler<Ping> lambda = (session, command) -> {};

            assertThatThrownBy(() -> new CommandRegistry(List.of(lambda)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("command type");
        }

        @Test
        void constructor_shouldRefuseAHandlerOfAnInterfaceType() {
            final SessionCommandHandler<SessionAddressed> anyAddressed = new SessionCommandHandler<>() {
                @Override
                public void handle(Session session, SessionAddressed command) {}
            };

            assertThatThrownBy(() -> new CommandRegistry(List.of(anyAddressed)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(SessionAddressed.class.getName());
        }

        @Test
        void constructor_shouldRefuseAContextHandler_whenItsCommandIsServerAddressed() {
            final ContextCommandHandler<String, Answer> answers = answers();

            assertThatThrownBy(() -> new CommandRegistry(List.of(answers)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(Answer.class.getName());
        }

        @Test
        void constructor_shouldRefuseAContextHandler_whenThereIsNoResolver() {
            final ContextCommandHandler<String, Enter> enters = new ContextCommandHandler<>() {
                @Override
                public ContextResolver<String> context() {
                    return null;
                }

                @Override
                public void handle(String context, Enter command) {}
            };

            assertThatThrownBy(() -> new CommandRegistry(List.of(enters)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("resolver");
        }
    }

    // Anonymous subclasses keep their type arguments, so the registry can read the command type.
    private static ContextCommandHandler<String, Enter> enters() {
        return new ContextCommandHandler<>() {
            @Override
            public ContextResolver<String> context() {
                return HERO;
            }

            @Override
            public void handle(String context, Enter command) {}
        };
    }

    private static ContextCommandHandler<String, Answer> answers() {
        return new ContextCommandHandler<>() {
            @Override
            public ContextResolver<String> context() {
                return HERO;
            }

            @Override
            public void handle(String context, Answer command) {}
        };
    }

    private static SessionCommandHandler<Ping> sessionPings() {
        return new SessionCommandHandler<>() {
            @Override
            public void handle(Session session, Ping command) {}
        };
    }
}
