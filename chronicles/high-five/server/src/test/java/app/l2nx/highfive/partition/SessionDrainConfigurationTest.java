/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.session.DrainScheduler;
import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.SessionFactory;
import app.l2nx.engine.core.session.SessionMetrics;
import app.l2nx.engine.core.session.SessionState;
import app.l2nx.engine.core.session.SessionTransport;
import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.engine.core.session.command.CommandRegistry;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class SessionDrainConfigurationTest {
    private final SessionMetrics metrics = SessionMetrics.noop();
    private final SessionFactory sessions = new SessionFactory(
            Session.Limits.of(NetworkProperties.defaults()),
            DrainScheduler.pool((drain, abandoned) -> drain.run(), metrics.drainFailures()),
            new SessionDrainConfiguration().authedDrainScheduler(),
            new CommandDispatcher(new CommandRegistry(List.of()), address -> Optional.empty(), NetworkMetrics.noop()),
            metrics);

    @Nested
    class AuthedDrainScheduler {
        @Test
        void authedDrainScheduler_shouldDrainInPassesOfTheBoundWorldLoop(Loop world) {
            final AtomicReference<Loop> ranIn = new AtomicReference<>();
            final Session session = sessions.create(new InlineTransport());
            session.setState(SessionState.AUTHED);

            session.offer(session.input((Runnable) () -> ranIn.set(Loop.current())));

            assertThat(ranIn.get()).isNull();
            WorldLoopExtension.passAfter(Duration.ZERO);
            assertThat(ranIn.get()).isSameAs(world);
        }
    }

    private static final class InlineTransport implements SessionTransport {
        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public void stateChanged(SessionState from, SessionState to) {}

        @Override
        public void close(CloseReason reason) {}
    }
}
