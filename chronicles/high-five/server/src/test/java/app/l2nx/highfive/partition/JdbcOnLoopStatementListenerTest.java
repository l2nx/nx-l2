/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.partition.PartitionRuntime;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class JdbcOnLoopStatementListenerTest {
    @Nested
    class Kind {
        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "'SELECT id FROM characters WHERE id = ?', read",
            "'SELECT EXISTS (SELECT 1 FROM items WHERE owner_id = ?)', read",
            "'UPDATE characters SET online_status = ? WHERE id = ?', write",
            "'INSERT INTO items (id) VALUES (?)', write",
            "'DELETE FROM items WHERE id = ?', write",
            "'SET LOCAL lock_timeout = 1000', other"
        })
        void kind_shouldClassifyTheStatementByItsFirstKeyword(String sql, String expected) {
            assertThat(JdbcOnLoopStatementListener.kind(sql)).isEqualTo(expected);
        }
    }

    @Nested
    class AfterQuery {
        @Test
        void afterQuery_shouldCountOnlyStatementsOfLoopThreads() {
            final SimpleMeterRegistry registry = new SimpleMeterRegistry();
            final JdbcOnLoopStatementListener listener = new JdbcOnLoopStatementListener(registry);
            final QueryInfo select = new QueryInfo("SELECT 1");

            listener.afterQuery(new ExecutionInfo(), List.of(select));
            WorldLoopExtension.runInPass(() -> listener.afterQuery(new ExecutionInfo(), List.of(select)));

            assertThat(registry.get(JdbcOnLoopStatementListener.METER)
                            .tag("loop", PartitionRuntime.WORLD)
                            .tag("kind", "read")
                            .counter()
                            .count())
                    .isEqualTo(1.0);
        }
    }
}
