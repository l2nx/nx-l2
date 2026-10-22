/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.Loop;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class JdbcOnLoopListenerTest {
    private static final Duration QUERY_TIME = Duration.ofMillis(30);

    private final ManualGameClock clock = new ManualGameClock(WorldLoopExtension.WALL_START, ZoneOffset.UTC);
    private final Loop loop = WorldLoopExtension.newLoop(clock);
    private final MockClock meterClock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, meterClock);
    private final Statement statement = mock(Statement.class);
    private final DataSource dataSource = ProxyDataSourceBuilder.create(pool())
            .methodListener(new JdbcOnLoopListener(registry))
            .build();
    private final Logger listenerLog = (Logger) LoggerFactory.getLogger(JdbcOnLoopListener.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            if (Thread.currentThread() == testThread) {
                super.append(event);
            }
        }
    };

    private volatile Thread testThread;

    @BeforeEach
    void captureLogs() {
        testThread = Thread.currentThread();
        logs.start();
        listenerLog.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        listenerLog.detachAppender(logs);
        logs.stop();
    }

    private DataSource pool() {
        final DataSource pool = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        try {
            when(pool.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.execute(anyString())).thenAnswer(invocation -> {
                meterClock.add(QUERY_TIME);
                return true;
            });
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return pool;
    }

    private void query() {
        try (Connection connection = dataSource.getConnection();
                Statement proxied = connection.createStatement()) {
            proxied.execute("SELECT 1");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void runInPass(Runnable body) {
        loop.execute(body);
        loop.runPass(clock.gameMillis());
    }

    private Timer timer() {
        return registry.find(JdbcOnLoopListener.METER).timer();
    }

    private List<String> warnings() {
        return logs.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Nested
    class AfterMethod {
        @Test
        void afterMethod_shouldTimeTheCallsUnderTheLoop_whenRunInAPass() throws SQLException {
            runInPass(JdbcOnLoopListenerTest.this::query);

            verify(statement).execute("SELECT 1");
            final Timer actual = registry.get(JdbcOnLoopListener.METER)
                    .tag("loop", loop.getName())
                    .timer();
            assertThat(actual.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(QUERY_TIME.toMillis());
        }

        @Test
        void afterMethod_shouldRecordNothing_whenRunOffTheLoop() throws SQLException {
            query();

            verify(statement).execute("SELECT 1");
            assertThat(timer()).isNull();
            assertThat(warnings()).isEmpty();
        }

        @Test
        void afterMethod_shouldWarnOncePerSiteAndMinute_whenStatementsRunOnTheLoop() throws SQLException {
            runInPass(() -> {
                query();
                query();
            });
            meterClock.add(Duration.ofMinutes(1));
            runInPass(JdbcOnLoopListenerTest.this::query);

            verify(statement, times(3)).execute("SELECT 1");
            assertThat(warnings())
                    .hasSize(2)
                    .allSatisfy(warning -> assertThat(warning)
                            .startsWith("JDBC statement ran on loop " + loop.getName())
                            .contains(JdbcOnLoopListenerTest.class.getName() + ".query:"));
        }
    }
}
