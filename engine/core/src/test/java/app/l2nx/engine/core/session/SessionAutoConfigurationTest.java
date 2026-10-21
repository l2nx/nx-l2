/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.session.command.Command;
import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.engine.core.session.command.CommandRegistry;
import app.l2nx.engine.core.session.command.SessionAddress;
import app.l2nx.engine.core.session.command.SessionAddressed;
import app.l2nx.engine.core.session.command.SessionCommandHandler;
import app.l2nx.engine.core.session.command.SessionDirectory;
import app.l2nx.network.NetworkAutoConfiguration;
import app.l2nx.network.exec.BlockingTaskExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SessionAutoConfigurationTest {

    record Ping() implements Command {}

    record Kick(SessionAddress address) implements SessionAddressed {}

    private static final CountDownLatch PINGED = new CountDownLatch(1);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    NetworkAutoConfiguration.class,
                    SessionAutoConfiguration.class,
                    TaskExecutionAutoConfiguration.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void context_shouldProvideTheSessionBeans() {
        runner.run(context -> {
            assertThat(context)
                    .hasSingleBean(SessionFactory.class)
                    .hasSingleBean(CommandRegistry.class)
                    .hasSingleBean(CommandDispatcher.class)
                    .hasSingleBean(SessionMetrics.class);
            assertThat(context.getBean("sessionDrainExecutor", BlockingTaskExecutor.class))
                    .isNotNull();
            assertThat(context.getBean(MeterRegistry.class)
                            .find("executor.execution")
                            .tag("name", "l2nx-game-packets")
                            .timer())
                    .isNotNull();
        });
    }

    @Test
    void context_shouldKeepBootsApplicationTaskExecutor() {
        // Boot's executor backs off when any Executor bean exists; the drain executor is not one.
        runner.run(context -> assertThat(context).hasBean("applicationTaskExecutor"));
    }

    @Test
    void context_shouldBackOff_whenTheApplicationDefinesARegistry() {
        final CommandRegistry own = new CommandRegistry(List.of());

        runner.withBean(CommandRegistry.class, () -> own)
                .run(context ->
                        assertThat(context.getBean(CommandRegistry.class)).isSameAs(own));
    }

    @Test
    void context_shouldFailToStart_whenTwoHandlersShareACommand() {
        runner.withBean("first", SessionCommandHandler.class, SessionAutoConfigurationTest::pingHandler)
                .withBean("second", SessionCommandHandler.class, SessionAutoConfigurationTest::pingHandler)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void sessionFactory_shouldCreateSessionsThatDrainOnTheDrainExecutor() {
        runner.withBean("ping", SessionCommandHandler.class, SessionAutoConfigurationTest::pingHandler)
                .run(context -> {
                    final Session session =
                            context.getBean(SessionFactory.class).create(new FakeTransport());

                    assertThat(session.offer(session.input(new Ping()))).isTrue();

                    assertThat(PINGED.await(5, TimeUnit.SECONDS)).isTrue();
                });
    }

    @Nested
    class DirectoryLookup {
        private static final SessionAddress ACCOUNT =
                new SessionAddress.Account(UUID.fromString("0190a6a2-7c00-7000-8000-000000000001"));

        @Test
        void submit_shouldNeedTheDirectory_onlyForAnAddressedCommand() {
            runner.withBean("kick", SessionCommandHandler.class, SessionAutoConfigurationTest::kickHandler)
                    .run(context -> {
                        assertThat(context).hasNotFailed();

                        assertThatThrownBy(() ->
                                        context.getBean(CommandDispatcher.class).submit(new Kick(ACCOUNT)))
                                .isInstanceOf(NoSuchBeanDefinitionException.class);
                    });
        }

        @Test
        void submit_shouldResolveTheDirectoryOnce_whenSeveralAddressedCommandsArrive() {
            final AtomicInteger created = new AtomicInteger();
            runner.withBean("kick", SessionCommandHandler.class, SessionAutoConfigurationTest::kickHandler)
                    .withBean(
                            SessionDirectory.class,
                            () -> {
                                created.incrementAndGet();
                                return address -> Optional.empty();
                            },
                            definition -> definition.setScope(BeanDefinition.SCOPE_PROTOTYPE))
                    .run(context -> {
                        final CommandDispatcher dispatcher = context.getBean(CommandDispatcher.class);

                        dispatcher.submit(new Kick(ACCOUNT));
                        dispatcher.submit(new Kick(ACCOUNT));
                        dispatcher.submit(new Kick(ACCOUNT));

                        assertThat(created.get()).isEqualTo(1);
                    });
        }
    }

    private static SessionCommandHandler<Ping> pingHandler() {
        return new SessionCommandHandler<>() {
            @Override
            public void handle(Session session, Ping command) {
                PINGED.countDown();
            }
        };
    }

    private static SessionCommandHandler<Kick> kickHandler() {
        return new SessionCommandHandler<>() {
            @Override
            public void handle(Session session, Kick command) {}
        };
    }
}
